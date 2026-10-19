package com.defnf.grid.data.remote.pool

import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import com.defnf.grid.data.remote.NetworkClient
import com.defnf.grid.data.remote.NetworkClientFactory
import com.defnf.grid.di.ApplicationScope
import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.Credential
import com.defnf.grid.domain.model.Result
import com.defnf.grid.domain.repository.CredentialRepository
import com.defnf.grid.util.GridLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** Tunables, grouped so tests can shrink the timeouts without touching production values. */
data class PoolConfig(
    val foregroundIdleTimeoutMs: Long = 90_000,
    val backgroundIdleTimeoutMs: Long = 15_000,
    val reapIntervalMs: Long = 15_000,
    /**
     * Backstop only. Deliberately longer than each client's own connect timeout so the client
     * reports the failure as a plain error; if this one ever fires it is converted below, because a
     * TimeoutCancellationException would reach the UI as a *cancellation* and be shown as nothing.
     */
    val connectTimeoutMs: Long = 35_000,
    val maxEntries: Int = 4,
    val maxConcurrencyPerSession: Int = 4
)

/**
 * Keeps authenticated SFTP/SMB sessions alive between operations.
 *
 * Before this existed, every directory listing, every file open and every upload ran its own full
 * handshake — TCP, key exchange, authentication, subsystem or tree open — and several of those paths
 * never disconnected afterwards, so the sessions leaked. Callers now borrow a session through
 * [withClient] and the pool decides whether that means reusing a warm one or opening a new one.
 *
 * ### How a session is kept safe while in use
 *
 * Three separate locks, each with a distinct job:
 *
 *  - `registryMutex` guards the entry map and every lease-count transition. It is never held across
 *    network I/O, only across map mutation.
 *  - `PoolEntry.connectMutex` serialises the handshake, so N callers arriving at once for the same
 *    server queue up and exactly one of them connects.
 *  - `PoolEntry.gate` bounds how many operations run concurrently on a single session.
 *
 * An entry is closed in exactly two places: by [release] when its last lease is returned, and by the
 * reaper — which only ever closes an entry whose lease count it read as zero *while holding*
 * `registryMutex`. Since acquiring a lease also happens under that lock, the reaper can never observe
 * a zero that is about to become one.
 */
@Singleton
class ConnectionPool @VisibleForTesting internal constructor(
    private val clientFactory: NetworkClientFactory,
    private val credentialRepository: CredentialRepository,
    private val scope: CoroutineScope,
    private val config: PoolConfig,
    /**
     * Injected so tests can drive idle accounting from a [kotlinx.coroutines.test.TestCoroutineScheduler].
     * Production uses [SystemClock.elapsedRealtime]: it is monotonic, so an NTP correction cannot
     * make a live entry look hours idle.
     */
    private val nowMs: () -> Long
) {

    // Dagger cannot see Kotlin default arguments, so the injectable constructor is spelled out.
    @Inject
    constructor(
        clientFactory: NetworkClientFactory,
        credentialRepository: CredentialRepository,
        @ApplicationScope scope: CoroutineScope
    ) : this(
        clientFactory = clientFactory,
        credentialRepository = credentialRepository,
        scope = scope,
        config = PoolConfig(),
        nowMs = { SystemClock.elapsedRealtime() }
    )


    private class PoolEntry(
        val key: PoolKey,
        val client: NetworkClient,
        maxConcurrency: Int
    ) {
        val connectMutex = Mutex()
        val gate = Semaphore(maxConcurrency)
        val leases = AtomicInteger(0)

        @Volatile var connected = false
        @Volatile var invalid = false
        @Volatile var closeOnRelease = false
        @Volatile var lastUsedAtMs = 0L
    }

    private class Lease(val entry: PoolEntry, val wasReused: Boolean)

    /** Thrown internally when an entry is invalidated between acquiring and connecting. */
    private class StaleEntryException : Exception()

    private val registryMutex = Mutex()
    private val entries = LinkedHashMap<PoolKey, PoolEntry>()
    private val backgrounded = AtomicBoolean(false)
    private var reaperJob: Job? = null

    /**
     * Runs [block] against a live session, reusing a pooled one when possible.
     *
     * **[block] must not be wrapped in `withContext`.** [clientFlow] emits from inside it, and
     * changing the `ContinuationInterceptor` between the flow builder and the emission trips
     * `Flow invariant is violated` at runtime. `Mutex.withLock`, `Semaphore.withPermit` and
     * `withTimeout` are all safe here because they preserve the interceptor; `withContext` is not.
     * The clients switch to `Dispatchers.IO` internally, which is fine — nothing emits inside those.
     *
     * @param retryOnStale whether a dead *reused* session should be transparently reconnected and the
     * operation retried once. Safe for reads; must be false for anything that mutates remote state,
     * since a request that died in flight may already have been applied server-side.
     */
    suspend fun <T> withClient(
        connection: Connection,
        retryOnStale: Boolean = true,
        block: suspend (NetworkClient) -> T
    ): T {
        val credential = credentialRepository.getCredentialById(connection.credentialId)
        val key = poolKeyOf(connection, credential)

        var attempt = 0
        while (true) {
            val lease = acquire(key, connection, credential)
            try {
                return lease.entry.gate.withPermit { block(lease.entry.client) }
            } catch (e: CancellationException) {
                // A cancelled caller says nothing about the session's health; leave it pooled.
                throw e
            } catch (e: Throwable) {
                val connectionFault = ConnectionFaults.isConnectionFault(e)
                if (connectionFault) lease.entry.invalid = true

                // Retry only a session we found already open. If a handshake we just completed fails
                // on its very first operation, the network is genuinely broken and retrying only
                // doubles the user's wait.
                if (attempt == 0 && lease.wasReused && retryOnStale && connectionFault) {
                    GridLog.d(TAG) { "Reconnecting after stale session: $key" }
                    attempt++
                    continue
                }
                throw e
            } finally {
                release(lease)
            }
        }
    }

    /**
     * Flow-shaped counterpart to [withClient]: the lease is taken when collection starts and returned
     * when it completes or is cancelled.
     *
     * Never retries. A retry cannot un-emit progress values a collector has already seen.
     */
    fun <T> clientFlow(
        connection: Connection,
        block: suspend FlowCollector<T>.(NetworkClient) -> Unit
    ): Flow<T> = flow {
        withClient(connection, retryOnStale = false) { client -> block(client) }
    }

    /**
     * Opens a long-lived stream (media playback) on a pooled session.
     *
     * The session stays leased, and one of its concurrency permits held, until the returned stream
     * is closed, so the reaper can't close it mid-playback. A stale *reused* session is reconnected
     * and the open retried once; opening for read changes nothing on the server.
     */
    suspend fun openStream(
        connection: Connection,
        opener: suspend (NetworkClient) -> InputStream
    ): InputStream {
        val credential = credentialRepository.getCredentialById(connection.credentialId)
        val key = poolKeyOf(connection, credential)

        var attempt = 0
        while (true) {
            val lease = acquire(key, connection, credential)
            var permitHeld = false
            try {
                lease.entry.gate.acquire()
                permitHeld = true
                val stream = opener(lease.entry.client)
                return LeasedInputStream(stream, lease)
            } catch (e: CancellationException) {
                if (permitHeld) lease.entry.gate.release()
                release(lease)
                throw e
            } catch (e: Throwable) {
                if (permitHeld) lease.entry.gate.release()
                val connectionFault = ConnectionFaults.isConnectionFault(e)
                if (connectionFault) lease.entry.invalid = true
                release(lease)
                if (attempt == 0 && lease.wasReused && connectionFault) {
                    GridLog.d(TAG) { "Reconnecting stale session for stream: $key" }
                    attempt++
                    continue
                }
                throw e
            }
        }
    }

    /** Returns its lease to the pool when closed, and flags the session if reading hit a dead link. */
    private inner class LeasedInputStream(
        stream: InputStream,
        private val lease: Lease
    ) : FilterInputStream(stream) {
        private val closed = AtomicBoolean(false)

        override fun read(): Int = try {
            super.read()
        } catch (e: IOException) {
            throw flagIfDead(e)
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int = try {
            super.read(b, off, len)
        } catch (e: IOException) {
            throw flagIfDead(e)
        }

        override fun skip(n: Long): Long = try {
            super.skip(n)
        } catch (e: IOException) {
            throw flagIfDead(e)
        }

        private fun flagIfDead(e: IOException): IOException {
            if (ConnectionFaults.isConnectionFault(e)) lease.entry.invalid = true
            return e
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            try {
                super.close()
            } catch (e: IOException) {
                flagIfDead(e)
                GridLog.w(TAG, "Closing stream failed", e)
            } finally {
                lease.entry.gate.release()
                // close() is called from player loader threads; hand the bookkeeping to the pool's scope.
                scope.launch { release(lease) }
            }
        }
    }

    /**
     * Opens a throwaway connection to check that settings work, then closes it.
     *
     * Never pooled: the credential comes from an editor form rather than the store, so it may not
     * correspond to anything saved yet.
     */
    suspend fun probe(connection: Connection, credential: Credential): Result<Unit> {
        val client = clientFactory.createClient(connection.protocol)
        return try {
            val result = withTimeout(config.connectTimeoutMs) {
                client.connect(connection, credential)
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.Error(e as? Exception ?: Exception(e))
        } finally {
            runCatching { client.disconnect() }
        }
    }

    /** Opens and pools a session ahead of the first real operation. */
    suspend fun warm(connection: Connection): Result<Unit> = try {
        withClient(connection) { Result.Success(Unit) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.Error(e as? Exception ?: Exception(e))
    }

    /** Drops any pooled session for this connection, deferring the close if one is in use. */
    suspend fun evict(connectionId: String) {
        evictMatching { it.connectionId == connectionId }
    }

    /** Drops any pooled session using this credential. Call when a password is changed or removed. */
    suspend fun evictByCredential(credentialId: String) {
        evictMatching { it.credentialId == credentialId }
    }

    suspend fun evictAll() {
        evictMatching { true }
    }

    fun onAppForegrounded() {
        backgrounded.set(false)
    }

    /**
     * Shortens the idle timeout rather than tearing everything down.
     *
     * This looks over-cautious but is not: `ProcessLifecycleOwner` reports background whenever Grid
     * has no started activity, and the system file picker runs in a *different* process. So while the
     * user browses Grid's roots in the picker, `GridDocumentsProvider` is being driven hard on binder
     * threads in this process while the lifecycle says "background". Evicting there would make every
     * single SAF query pay a fresh handshake — worse than having no pool at all. Shrinking the
     * timeout instead keeps sessions warm while they are still being used, and reclaims them shortly
     * after the user stops.
     */
    fun onAppBackgrounded() {
        backgrounded.set(true)
        scope.launch { sweep() }
    }

    private suspend fun acquire(
        key: PoolKey,
        connection: Connection,
        credential: Credential
    ): Lease {
        while (true) {
            val entry: PoolEntry
            val reusedExisting: Boolean

            registryMutex.withLock {
                val existing = entries[key]
                if (existing != null && !existing.invalid) {
                    existing.leases.incrementAndGet()
                    entry = existing
                    reusedExisting = existing.connected
                } else {
                    if (existing != null) {
                        entries.remove(key)
                        closeLater(existing)
                    }
                    evictOverCapacityLocked()
                    val fresh = PoolEntry(
                        key = key,
                        client = clientFactory.createClient(connection.protocol),
                        maxConcurrency = config.maxConcurrencyPerSession
                    )
                    fresh.leases.incrementAndGet()
                    entries[key] = fresh
                    ensureReaperRunningLocked()
                    entry = fresh
                    reusedExisting = false
                }
            }

            // Connect outside registryMutex so a slow handshake never blocks the whole pool, but
            // inside the entry's own mutex so concurrent callers for this key connect exactly once.
            try {
                entry.connectMutex.withLock {
                    if (entry.invalid) throw StaleEntryException()
                    if (!entry.connected || !entry.client.isConnected()) {
                        val result = try {
                            withTimeout(config.connectTimeoutMs) {
                                entry.client.connect(connection, credential)
                            }
                        } catch (e: TimeoutCancellationException) {
                            // TimeoutCancellationException is a CancellationException, and callers
                            // treat cancellation as "the user backed out" and show no error at all.
                            // A connect that timed out is a failure and must be reported as one.
                            entry.invalid = true
                            throw IOException("Connection timed out", e)
                        }
                        when (result) {
                            is Result.Success -> entry.connected = true
                            is Result.Error -> {
                                entry.invalid = true
                                throw result.exception
                            }
                            is Result.Loading -> {
                                entry.invalid = true
                                throw IllegalStateException("Unexpected loading state from connect")
                            }
                        }
                    }
                }
            } catch (e: StaleEntryException) {
                // Someone invalidated this entry while we were queued; start over with a fresh one.
                release(Lease(entry, false))
                continue
            } catch (e: Throwable) {
                // The lease must not leak when the handshake fails.
                release(Lease(entry, false))
                throw e
            }

            return Lease(entry, reusedExisting && entry.connected)
        }
    }

    /**
     * Returns a lease. Runs [NonCancellable] on purpose: this is called from a `finally`, and a
     * caller cancelled mid-operation (navigating away, a torn-down viewModelScope) would otherwise
     * resume straight into a CancellationException while suspended on the mutex — the decrement
     * would never run, and the entry would stay leased forever, invisible to both the reaper and
     * evict().
     */
    private suspend fun release(lease: Lease): Unit = withContext(NonCancellable) {
        val entry = lease.entry
        val toClose = registryMutex.withLock {
            val remaining = entry.leases.decrementAndGet()
            entry.lastUsedAtMs = nowMs()
            if (remaining <= 0 && (entry.invalid || entry.closeOnRelease)) {
                if (entries[entry.key] === entry) entries.remove(entry.key)
                entry
            } else {
                null
            }
        }
        toClose?.let { closeLater(it) }
    }

    private suspend fun evictMatching(predicate: (PoolKey) -> Boolean) {
        val toClose = registryMutex.withLock {
            val doomed = mutableListOf<PoolEntry>()
            val iterator = entries.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next().value
                if (!predicate(entry.key)) continue
                iterator.remove()
                if (entry.leases.get() <= 0) {
                    doomed += entry
                } else {
                    // Still in use. Removing it from the map is enough to stop new callers finding
                    // it; the last one to let go will close it.
                    entry.closeOnRelease = true
                }
            }
            doomed
        }
        toClose.forEach { closeLater(it) }
    }

    /**
     * Caller must hold [registryMutex]. Evicts least-recently-used idle entries when over cap.
     *
     * A soft cap: only unleased entries can be evicted, so when every entry is busy the pool is
     * allowed to exceed [PoolConfig.maxEntries] rather than block a caller behind an unrelated
     * connection. Overflow is transient — the extras are reaped once their operations finish.
     */
    private fun evictOverCapacityLocked() {
        while (entries.size >= config.maxEntries) {
            val victim = entries.values
                .filter { it.leases.get() <= 0 }
                .minByOrNull { it.lastUsedAtMs }
                ?: return
            entries.remove(victim.key)
            closeLater(victim)
        }
    }

    /** Caller must hold [registryMutex]. */
    private fun ensureReaperRunningLocked() {
        if (reaperJob?.isActive == true) return
        reaperJob = scope.launch {
            while (isActive) {
                delay(config.reapIntervalMs)
                if (sweep()) break
            }
        }
    }

    /** @return true when the pool is empty and the reaper can stand down. */
    private suspend fun sweep(): Boolean {
        val timeout = if (backgrounded.get()) {
            config.backgroundIdleTimeoutMs
        } else {
            config.foregroundIdleTimeoutMs
        }
        val now = nowMs()

        val doomed = registryMutex.withLock {
            val out = mutableListOf<PoolEntry>()
            val iterator = entries.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next().value
                val expired = now - entry.lastUsedAtMs >= timeout
                // The lease check and the removal happen under the same lock, which is what stops
                // this racing an in-flight acquire.
                if (entry.leases.get() <= 0 && (expired || entry.invalid)) {
                    iterator.remove()
                    out += entry
                }
            }
            out
        }
        doomed.forEach { closeLater(it) }

        return registryMutex.withLock {
            val empty = entries.isEmpty()
            // Cleared under the same lock that decides to stop. Otherwise a caller arriving between
            // the reaper's `break` and the coroutine actually completing would see isActive == true,
            // skip starting a new reaper, and leave its entry with nothing to reap it.
            if (empty) reaperJob = null
            empty
        }
    }

    /**
     * Closes off the caller's thread. A `disconnect()` on a half-dead socket can block, and no
     * caller's `finally` should have to wait for it.
     */
    private fun closeLater(entry: PoolEntry) {
        scope.launch {
            runCatching { entry.client.disconnect() }
                .onFailure { GridLog.d(TAG) { "Ignoring error closing ${entry.key}: ${it.message}" } }
        }
    }

    @VisibleForTesting
    internal suspend fun debugEntryCount(): Int = registryMutex.withLock { entries.size }

    @VisibleForTesting
    internal suspend fun debugLeaseCount(connectionId: String): Int = registryMutex.withLock {
        entries.values.firstOrNull { it.key.connectionId == connectionId }?.leases?.get() ?: 0
    }

    private companion object {
        const val TAG = "GridPool"
    }
}
