package com.defnf.grid.data.offline

import com.defnf.grid.data.remote.NetworkClientFactory
import com.defnf.grid.data.remote.pool.ConnectionPool
import com.defnf.grid.data.remote.pool.FakeNetworkClient
import com.defnf.grid.data.remote.pool.PoolConfig
import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.Credential
import com.defnf.grid.domain.model.Protocol
import com.defnf.grid.domain.repository.CredentialRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFillerTest {

    private val remotePath = "/music/song.mp3"
    private val content = ByteArray(1_000_000) { (it % 251).toByte() }
    private val connection = Connection(
        id = "c1",
        name = "NAS",
        hostname = "nas.local",
        protocol = Protocol.SFTP,
        credentialId = "cred"
    )

    private val clients = mutableListOf<FakeNetworkClient>()
    private val target = File(Files.createTempDirectory("offline").toFile(), "NAS/music/song.mp3")

    private val offlineStore = mockk<OfflineStore>().also {
        coEvery { it.fileFor(any(), remotePath) } returns target
        every { it.publish(any()) } just runs
    }

    private fun TestScope.filler(): OfflineFiller {
        val factory = object : NetworkClientFactory {
            override fun createClient(protocol: Protocol) = FakeNetworkClient().also {
                it.fileContents = mapOf(remotePath to content)
                clients += it
            }
        }
        val credentials = object : CredentialRepository {
            override suspend fun insertCredential(credential: Credential) = Unit
            override suspend fun updateCredential(credential: Credential) = Unit
            override suspend fun deleteCredential(credentialId: String) = Unit
            override suspend fun getCredentialById(credentialId: String) =
                Credential(id = credentialId, username = "u", password = "p")
            override suspend fun getAllCredentials(): Flow<List<Credential>> = flowOf(emptyList())
        }
        val pool = ConnectionPool(factory, credentials, backgroundScope, PoolConfig(), nowMs = { testScheduler.currentTime })
        return OfflineFiller(pool, offlineStore, backgroundScope)
    }

    @Test
    fun `a stream fills its offline copy and swaps it into place`() = runTest {
        val fill = filler().acquire(connection, remotePath, content.size.toLong())!!
        fill.job!!.join()

        assertTrue(fill.isComplete)
        assertEquals(target, fill.readable)
        assertArrayEquals(content, target.readBytes())
        assertFalse(OfflineStore.streamPartFor(target, content.size.toLong()).exists())
    }

    @Test
    fun `an interrupted fill resumes from what is already on disk`() = runTest {
        val part = OfflineStore.streamPartFor(target, content.size.toLong())
        part.parentFile!!.mkdirs()
        // Marker bytes prove the first 400 KB were kept, not fetched again.
        val kept = ByteArray(400_000) { 7 }
        part.writeBytes(kept)

        val fill = filler().acquire(connection, remotePath, content.size.toLong())!!
        fill.job!!.join()

        val saved = target.readBytes()
        assertArrayEquals(kept, saved.copyOfRange(0, kept.size))
        assertArrayEquals(content.copyOfRange(kept.size, content.size), saved.copyOfRange(kept.size, saved.size))
    }

    @Test
    fun `a finished offline copy needs no connection`() = runTest {
        target.parentFile!!.mkdirs()
        target.writeBytes(content)

        val fill = filler().acquire(connection, remotePath, content.size.toLong())!!

        assertTrue(fill.isComplete)
        assertTrue(clients.isEmpty())
    }

    @Test
    fun `playing the same file twice joins the running fill`() = runTest {
        val filler = filler()
        val first = filler.acquire(connection, remotePath, content.size.toLong())
        val second = filler.acquire(connection, remotePath, content.size.toLong())
        assertTrue(first === second)
        first!!.job!!.join()
    }

    @Test
    fun `without a known size nothing is saved`() = runTest {
        assertNull(filler().acquire(connection, remotePath, 0))
    }
}
