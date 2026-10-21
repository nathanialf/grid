package com.defnf.grid.data.offline

import com.defnf.grid.data.remote.pool.ConnectionPool
import com.defnf.grid.di.ApplicationScope
import com.defnf.grid.domain.model.Connection
import com.defnf.grid.util.GridLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
     * finished copy can't be told from a truncated one. [modified] is the server's modified time
     * (0 if unknown), so a part file is only resumed against the version it was saving.
     *
     * A new fill's part file exists, holding whatever an earlier fill left, before this returns, so
     * a reader can open it and knows how far it already reaches. Throws if it can't be written at
     * all (no storage access), in which case there is nothing to save into.
     */
    suspend fun acquire(connection: Connection, remotePath: String, size: Long, modified: Long): Fill? {
        if (size <= 0) return null
        val target = offlineStore.fileFor(connection, remotePath)
        val key = keyOf(connection.id, remotePath)
        synchronized(fills) {
            fills[key]?.takeIf { it.isActive }?.let { return it }
            if (target.exists() && OfflineStore.isValid(target, size)) return Fill.completed(target)
            val fill = Fill(target, OfflineStore.streamPartFor(target, size, modified), size)
            prepare(fill, modified)
            fills[key] = fill
            fill.job = scope.launch { run(connection, remotePath, key, fill) }.also { job ->
                // Here rather than in run(), so it also covers a fill cancelled before it ever ran:
                // readers must not wait on it, and the next play must not join it.
                job.invokeOnCompletion { cause ->
                    if (fill.isActive) fill.fail(cause ?: IOException("Stopped before finishing"))
                    synchronized(fills) { if (fills[key] === fill) fills.remove(key) }
                    _progress.update { it - key }
                }
            }
            return fill
        }
    }

    /** Readies [fill]'s part file: drops other versions' parts and picks up where this one stopped. */
    private fun prepare(fill: Fill, modified: Long) {
        fill.part.parentFile?.mkdirs()
        OfflineStore.staleStreamParts(fill.target, fill.size, modified).forEach { it.delete() }
        // Creating it now also fails fast when the copy can't be written at all (no storage
        // access, read-only storage), so the player reads the server instead of waiting.
        FileOutputStream(fill.part, /* append = */ true).close()
        // A part longer than the file can't be one of its prefixes; start over.
        if (fill.part.length() > fill.size) {
            fill.part.delete()
            FileOutputStream(fill.part).close()
        }
        fill.advance(fill.part.length())
    }

    /**
     * Stops every fill for a connection and waits until they have, e.g. before its offline files
     * are cleared, so none writes its part file back afterwards. Media still playing starts saving
     * again on its next read, as any new play would.
     */
    suspend fun cancelAll(connectionId: String) {
        val prefix = "$connectionId|"
        stop(synchronized(fills) { fills.filterKeys { it.startsWith(prefix) }.values.toList() })
    }

    /** Stops the fill of one file, if any, and waits until it has (before deleting that file). */
    suspend fun cancel(connectionId: String, remotePath: String) {
        stop(synchronized(fills) { listOfNotNull(fills[keyOf(connectionId, remotePath)]) })
    }

    // Each fill's completion handler unregisters it and clears its progress.
    private suspend fun stop(running: List<Fill>) = running.forEach { it.job?.cancelAndJoin() }

    private suspend fun run(connection: Connection, remotePath: String, key: String, fill: Fill) {
        try {
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
            if (!fill.part.renameTo(fill.target)) {
                // Most likely an earlier install of Grid owns a file of that name, which this one
                // can neither see nor replace. Resuming would only end here again, so let it go.
                fill.part.delete()
                throw IOException("Couldn't save ${fill.target.name}; is a file of that name in the way?")
            }
            fill.complete()
            offlineStore.publish(fill.target)
        } catch (e: Throwable) {
            // The part file stays so a later play can resume it.
            fill.fail(e)
            if (e is CancellationException) throw e
            GridLog.w(TAG, "Couldn't save $remotePath offline", e)
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
