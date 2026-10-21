package com.defnf.grid.data.remote.pool

import com.defnf.grid.data.remote.NetworkClientFactory
import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.Credential
import com.defnf.grid.domain.model.Protocol
import com.defnf.grid.domain.repository.CredentialRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import com.hierynomus.smbj.common.SMBRuntimeException
import org.apache.sshd.common.SshException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionPoolTest {

    private val idleMs = 1_000L
    private val reapMs = 100L

    private val testConfig = PoolConfig(
        foregroundIdleTimeoutMs = idleMs,
        backgroundIdleTimeoutMs = 200L,
        reapIntervalMs = reapMs,
        connectTimeoutMs = 5_000L,
        maxEntries = 4,
        maxConcurrencyPerSession = 4
    )

    private val clients = mutableListOf<FakeNetworkClient>()

    /** Applied to every client the factory creates, so a failure can outlive one reconnect. */
    private var listFailuresForNewClients = 0
    private var credential = Credential(id = "cred", username = "u", password = "p")

    private fun connection(id: String = "c1") = Connection(
        id = id,
        name = id,
        hostname = "host-$id",
        protocol = Protocol.SFTP,
        credentialId = "cred"
    )

    private fun factory(): NetworkClientFactory = object : NetworkClientFactory {
        override fun createClient(protocol: Protocol) = FakeNetworkClient().also {
            it.listFailuresRemaining = listFailuresForNewClients
            clients += it
        }
    }

    private fun credentials(): CredentialRepository = object : CredentialRepository {
        override suspend fun insertCredential(credential: Credential) = Unit
        override suspend fun updateCredential(credential: Credential) = Unit
        override suspend fun deleteCredential(credentialId: String) = Unit
        override suspend fun getCredentialById(credentialId: String): Credential = credential
        override suspend fun getAllCredentials(): Flow<List<Credential>> = flowOf(emptyList())
    }

    private fun TestScope.pool(config: PoolConfig = testConfig) = ConnectionPool(
        clientFactory = factory(),
        credentialRepository = credentials(),
        scope = backgroundScope,
        config = config,
        nowMs = { testScheduler.currentTime }
    )

    @Test
    fun `sequential calls reuse a single session`() = runTest {
        val pool = pool()
        repeat(3) { pool.withClient(connection()) { it.listFiles("/") } }

        assertEquals(1, clients.size)
        assertEquals(1, clients[0].connectCount.get())
    }

    @Test
    fun `concurrent acquisitions perform exactly one handshake`() = runTest {
        val pool = pool()

        // Five callers arrive for the same server at once; the per-entry connect mutex must let
        // exactly one of them do the handshake.
        val jobs = (1..5).map {
            async { pool.withClient(connection()) { it.listFiles("/") } }
        }
        jobs.forEach { it.await() }

        assertEquals(1, clients.size)
        assertEquals(1, clients[0].connectCount.get())
    }

    @Test
    fun `distinct connections get distinct sessions`() = runTest {
        val pool = pool()
        pool.withClient(connection("a")) { it.listFiles("/") }
        pool.withClient(connection("b")) { it.listFiles("/") }

        assertEquals(2, clients.size)
        assertEquals(2, pool.debugEntryCount())
    }

    @Test
    fun `idle session is reaped after the timeout`() = runTest {
        val pool = pool()
        pool.withClient(connection()) { it.listFiles("/") }

        advanceTimeBy(idleMs + reapMs * 2)
        advanceUntilIdle()

        assertEquals(1, clients[0].disconnectCount.get())
        assertEquals(0, pool.debugEntryCount())
    }

    @Test
    fun `reaper never closes a leased session`() = runTest {
        val pool = pool()
        val hold = CompletableDeferred<Unit>()

        val job = launch {
            pool.withClient(connection()) { client ->
                (client as FakeNetworkClient).listGate = hold
                client.listFiles("/")
            }
        }
        advanceUntilIdle()

        // Well past the idle timeout, but the operation is still in flight.
        advanceTimeBy(idleMs * 5)
        advanceUntilIdle()
        assertEquals(0, clients[0].disconnectCount.get())

        hold.complete(Unit)
        job.join()
        advanceTimeBy(idleMs + reapMs * 2)
        advanceUntilIdle()
        assertEquals(1, clients[0].disconnectCount.get())
    }

    @Test
    fun `lease is released when the block throws`() = runTest {
        val pool = pool()
        runCatching {
            pool.withClient(connection()) { error("boom") }
        }
        advanceUntilIdle()

        assertEquals(0, pool.debugLeaseCount("c1"))
    }

    @Test
    fun `stale reused session reconnects once and succeeds`() = runTest {
        val pool = pool()
        pool.withClient(connection()) { it.listFiles("/") }
        assertEquals(1, clients[0].connectCount.get())

        // The pooled session dies between operations.
        clients[0].listFailuresRemaining = 1
        pool.withClient(connection()) { it.listFiles("/") }

        // Retried on a brand new client rather than the dead one.
        assertEquals(2, clients.size)
        assertEquals(1, clients[1].connectCount.get())
    }

    @Test
    fun `a stream that dies with an unchecked smb error retires its session`() = runTest {
        val pool = pool()
        val stream = pool.openFile(connection(), "/a", 0)
        clients[0].streamReadFailure = SMBRuntimeException("session dropped")

        assertThrows(SMBRuntimeException::class.java) { stream.read(ByteArray(8), 0, 8) }
        // close() hands its lease back on the pool's scope, which advanceUntilIdle leaves alone.
        stream.close()
        runCurrent()

        // The dead session was closed with its last lease, so the next operation opens a new one.
        assertEquals(1, clients[0].disconnectCount.get())
        pool.withClient(connection()) { it.listFiles("/") }
        assertEquals(2, clients.size)
    }

    @Test
    fun `reconnect is attempted at most once`() = runTest {
        val pool = pool()
        pool.withClient(connection()) { it.listFiles("/") }

        // Every session, including the one the reconnect opens, is dead.
        clients[0].listFailuresRemaining = 99
        listFailuresForNewClients = 99
        val thrown = runCatching {
            pool.withClient(connection()) { it.listFiles("/") }
        }.exceptionOrNull()

        assertTrue("expected SshException, got $thrown", thrown is SshException)
        // Exactly one reconnect: the original client plus one replacement, and no further attempts.
        assertEquals(2, clients.size)
    }

    @Test
    fun `a freshly connected session does not retry`() = runTest {
        val pool = pool()
        // First ever call fails: the network is broken, not the session stale.
        val thrown = runCatching {
            pool.withClient(connection()) { client ->
                (client as FakeNetworkClient).listFailuresRemaining = 1
                client.listFiles("/")
            }
        }.exceptionOrNull()

        assertTrue(thrown is SshException)
        assertEquals(1, clients.size)
    }

    @Test
    fun `retryOnStale false does not retry`() = runTest {
        val pool = pool()
        pool.withClient(connection()) { it.listFiles("/") }

        clients[0].listFailuresRemaining = 1
        runCatching {
            pool.withClient(connection(), retryOnStale = false) { it.listFiles("/") }
        }

        assertEquals(1, clients.size)
    }

    @Test
    fun `a server-side failure is not treated as a connection fault`() = runTest {
        val pool = pool()
        pool.withClient(connection()) { it.listFiles("/") }

        clients[0].listBusinessFailuresRemaining = 1
        runCatching { pool.withClient(connection()) { it.listFiles("/") } }
        advanceUntilIdle()

        // No reconnect, and the session stays pooled.
        assertEquals(1, clients.size)
        assertEquals(1, pool.debugEntryCount())
    }

    @Test
    fun `a changed credential opens a new session`() = runTest {
        val pool = pool()
        pool.withClient(connection()) { it.listFiles("/") }

        credential = credential.copy(password = "rotated")
        pool.withClient(connection()) { it.listFiles("/") }

        assertEquals(2, clients.size)
    }

    @Test
    fun `evict closes an unleased session immediately`() = runTest {
        val pool = pool()
        pool.withClient(connection()) { it.listFiles("/") }

        pool.evict("c1")
        // The pool closes off-caller on its own scope; advanceUntilIdle() does not drain
        // backgroundScope launches, runCurrent() does.
        runCurrent()

        assertEquals(1, clients[0].disconnectCount.get())
        assertEquals(0, pool.debugEntryCount())
    }

    @Test
    fun `evict defers closing a leased session until it is released`() = runTest {
        val pool = pool()
        val hold = CompletableDeferred<Unit>()

        val job = launch {
            pool.withClient(connection()) { client ->
                (client as FakeNetworkClient).listGate = hold
                client.listFiles("/")
            }
        }
        advanceUntilIdle()

        pool.evict("c1")
        advanceUntilIdle()
        assertEquals(0, clients[0].disconnectCount.get())

        hold.complete(Unit)
        job.join()
        advanceUntilIdle()
        assertEquals(1, clients[0].disconnectCount.get())
    }

    @Test
    fun `background shortens the idle timeout`() = runTest {
        val pool = pool()
        pool.withClient(connection()) { it.listFiles("/") }

        pool.onAppBackgrounded()
        advanceTimeBy(testConfig.backgroundIdleTimeoutMs + reapMs * 2)
        advanceUntilIdle()

        assertEquals(1, clients[0].disconnectCount.get())
    }

    @Test
    fun `foreground restores the longer idle timeout`() = runTest {
        val pool = pool()
        pool.withClient(connection()) { it.listFiles("/") }

        pool.onAppBackgrounded()
        pool.onAppForegrounded()
        advanceTimeBy(testConfig.backgroundIdleTimeoutMs + reapMs * 2)
        advanceUntilIdle()

        assertEquals(0, clients[0].disconnectCount.get())
        assertEquals(1, pool.debugEntryCount())
    }

    @Test
    fun `maxEntries evicts the least recently used idle session`() = runTest {
        val pool = pool(testConfig.copy(maxEntries = 2))
        pool.withClient(connection("a")) { it.listFiles("/") }
        pool.withClient(connection("b")) { it.listFiles("/") }
        pool.withClient(connection("c")) { it.listFiles("/") }
        advanceUntilIdle()

        assertTrue(pool.debugEntryCount() <= 2)
    }

    @Test
    fun `concurrency on one session is bounded`() = runTest {
        val pool = pool()
        pool.withClient(connection()) { it.listFiles("/") }

        val jobs = (1..10).map {
            async { pool.withClient(connection()) { it.listFiles("/") } }
        }
        jobs.forEach { it.await() }

        assertTrue(
            "observed ${clients[0].maxObservedConcurrency}",
            clients[0].maxObservedConcurrency <= testConfig.maxConcurrencyPerSession
        )
    }

    @Test
    fun `probe never pools and always disconnects`() = runTest {
        val pool = pool()
        pool.probe(connection(), credential)
        advanceUntilIdle()

        assertEquals(1, clients[0].disconnectCount.get())
        assertEquals(0, pool.debugEntryCount())
    }
}
