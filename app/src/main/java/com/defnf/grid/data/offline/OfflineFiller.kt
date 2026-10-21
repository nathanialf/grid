package com.defnf.grid.data.offline

import com.defnf.grid.data.remote.pool.ConnectionPool
import com.defnf.grid.di.ApplicationScope
import com.defnf.grid.domain.model.Connection
import com.defnf.grid.util.GridLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves streamed media into [OfflineStore] while it plays, so a file is only ever fetched once.
 *
 * A fill downloads front to back into a resumable part file. The player reads that same file as it
 * grows (see `RemoteFileDataSource`), and only reads the server directly for a spot the fill hasn't
 * reached yet. A fill outlives the player: leaving playback lets it finish in the background, and if
 * the process dies, the next play resumes from what is already on disk.
 */
@Singleton
class OfflineFiller @Inject constructor(
    private val pool: ConnectionPool,
    private val offlineStore: OfflineStore,
    @ApplicationScope private val scope: CoroutineScope
) {

    /** One file being filled. Readers wait on it for [written] to grow. */
    class Fill(val target: File, val part: File, val size: Long) {
        @Volatile var written: Long = 0L
            private set
        @Volatile var isComplete: Boolean = false
            private set
        @Volatile var failure: Throwable? = null
            private set

        internal var job: Job? = null
        private val lock = ReentrantLock()
        private val changed = lock.newCondition()

        val isActive: Boolean get() = !isComplete && failure == null

        /** The file holding the bytes: the part file until it's renamed into place. */
        val readable: File get() = if (isComplete) target else part

        /** Blocks until more than [position] bytes are on disk, the fill ends, or [timeoutMs] passes. */
        fun awaitBeyond(position: Long, timeoutMs: Long) {
            lock.withLock {
                if (written <= position && isActive) changed.await(timeoutMs, TimeUnit.MILLISECONDS)
            }
        }

        internal fun advance(bytes: Long) = signal { written = bytes }
        internal fun complete() = signal { isComplete = true }
        internal fun fail(error: Throwable) = signal { failure = error }

        private inline fun signal(change: () -> Unit) {
            lock.withLock {
                change()
                changed.signalAll()
            }
        }

        internal companion object {
            fun completed(target: File) = Fill(target, target, target.length()).apply {
                written = size
                isComplete = true
            }
        }
    }

    private val fills = HashMap<String, Fill>()

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** Running fills' progress (0..1), keyed by [keyOf]. */
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    /**
     * The fill for a remote file, started (or resumed) if none is running. Returns a completed fill
     * when the offline copy already exists; null when the size is unknown, since without it a
     * finished copy can't be told from a truncated one.
     */
    suspend fun acquire(connection: Connection, remotePath: String, size: Long): Fill? {
        if (size <= 0) return null
        val target = offlineStore.fileFor(connection, remotePath)
        val key = keyOf(connection.id, remotePath)
        synchronized(fills) {
            fills[key]?.takeIf { it.isActive }?.let { return it }
            if (target.exists() && OfflineStore.isValid(target, size)) return Fill.completed(target)
            val fill = Fill(target, OfflineStore.streamPartFor(target, size), size)
            fills[key] = fill
            fill.job = scope.launch { run(connection, remotePath, key, fill) }
            return fill
        }
    }

    /** Stops every fill for a connection, e.g. before its offline files are cleared. */
    fun cancelAll(connectionId: String) {
        val prefix = "$connectionId|"
        val cancelled = synchronized(fills) {
            fills.entries.filter { it.key.startsWith(prefix) }.map { it.value }
        }
        // Each fill's own cleanup unregisters it and clears its progress.
        cancelled.forEach { it.job?.cancel() }
    }

    private suspend fun run(connection: Connection, remotePath: String, key: String, fill: Fill) {
        try {
            fill.part.parentFile?.mkdirs()
            // Resume what an earlier fill left. The part's name carries the size it was filling to,
            // so one left from before the file changed on the server is stale and goes.
            OfflineStore.staleStreamParts(fill.target, fill.size).forEach { it.delete() }
            // Fail fast, before any retrying, when the copy can't be written at all (no storage
            // access, read-only storage), so the player stops waiting and reads the server instead.
            FileOutputStream(fill.part, /* append = */ true).close()
            fill.advance(fill.part.length())

            var attempts = 0
            while (fill.written < fill.size) {
                val before = fill.written
                try {
                    copyFrom(connection, remotePath, fill, key)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    // A dropped session mid-file is common on mobile networks; pick up where it
                    // stopped, giving up only after repeated failures that make no progress.
                    if (fill.written > before) attempts = 0
                    if (++attempts >= MAX_ATTEMPTS) throw e
                    GridLog.d(TAG) { "Fill of $remotePath interrupted, retrying: ${e.message}" }
                    delay(RETRY_DELAY_MS * attempts)
                }
            }

            fill.target.delete()
            if (!fill.part.renameTo(fill.target)) throw IOException("Couldn't save ${fill.target.name}")
            fill.complete()
            offlineStore.publish(fill.target)
        } catch (e: Throwable) {
            // The part file stays so a later play can resume it.
            fill.fail(e)
            if (e is CancellationException) throw e
            GridLog.w(TAG, "Couldn't save $remotePath offline", e)
        } finally {
            synchronized(fills) { if (fills[key] === fill) fills.remove(key) }
            _progress.update { it - key }
        }
    }

    private suspend fun copyFrom(connection: Connection, remotePath: String, fill: Fill, key: String) {
        val offset = fill.written
        pool.openStream(connection) { client -> client.openInputStream(remotePath, offset) }.use { input ->
            FileOutputStream(fill.part, /* append = */ true).use { output ->
                val buffer = ByteArray(BUFFER_BYTES)
                var written = offset
                var lastPercent = -1
                while (written < fill.size) {
                    // Reads block, so check here for cancellation (clearing offline files).
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), fill.size - written).toInt())
                    if (read == -1) throw IOException("$remotePath ended early at $written of ${fill.size} bytes")
                    // Unbuffered, so readers of the part file see these bytes once written advances.
                    output.write(buffer, 0, read)
                    written += read
                    fill.advance(written)
                    val percent = (written * 100 / fill.size).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        _progress.update { it + (key to written.toFloat() / fill.size) }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "GridOfflineFill"
        private const val BUFFER_BYTES = 256 * 1024
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1_000L

        fun keyOf(connectionId: String, remotePath: String) = "$connectionId|$remotePath"
    }
}
