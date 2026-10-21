package com.defnf.grid.data.provider

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException

/**
 * Serves reads at arbitrary offsets of a server file from one sequential stream.
 *
 * Apps that read a document (most often to upload it somewhere) read it front to back, so the open
 * stream simply keeps going; a jump reopens it at the new offset, and a short hop forward reads
 * through instead, which is cheaper than a reopen. A stream that fails mid-read (Wi-Fi to cellular,
 * a server dropping an idle session) is reopened where it stopped instead of failing the read, so a
 * blip costs the reading app a pause rather than its whole upload. Any failure counts, not only
 * IOExceptions: smbj reports a dropped session with unchecked exceptions.
 *
 * Not thread-safe: [RemoteFileProxyCallback] makes every call from the descriptor's own thread.
 */
internal class RemoteRangeReader(
    val size: Long,
    private val open: (offset: Long) -> InputStream
) : Closeable {

    private var stream: InputStream? = null
    private var position = 0L

    /** Reads up to [length] bytes at [offset] into [data]; fewer only at the end of the file. */
    fun read(offset: Long, length: Int, data: ByteArray): Int {
        if (length <= 0 || offset >= size) return 0
        val wanted = minOf(length.toLong(), size - offset).toInt()
        var filled = 0
        var failures = 0
        while (filled < wanted) {
            try {
                val read = streamAt(offset + filled).read(data, filled, wanted - filled)
                if (read == -1) break // The file shrank on the server since it was looked up.
                filled += read
                position += read
                failures = 0
            } catch (e: InterruptedIOException) {
                // The reading app gave up; reopening would only ignore it.
                closeStream()
                throw e
            } catch (e: Exception) {
                closeStream()
                if (++failures > MAX_REOPENS) throw e
            }
        }
        return filled
    }

    /** Lets go of the server stream (and its pooled session) until the next read needs it. */
    fun closeStream() {
        val current = stream ?: return
        stream = null
        try {
            current.close()
        } catch (ignored: IOException) {
            // Closing a stream that already broke can fail too; it's gone either way.
        }
    }

    override fun close() = closeStream()

    private fun streamAt(offset: Long): InputStream {
        stream?.let { current ->
            val gap = offset - position
            if (gap == 0L) return current
            if (gap in 1..SKIP_THROUGH_BYTES && discard(current, gap)) return current
        }
        closeStream()
        return open(offset).also {
            stream = it
            position = offset
        }
    }

    /** Reads past [count] bytes; false if the stream ended first. */
    private fun discard(input: InputStream, count: Long): Boolean {
        val scratch = ByteArray(minOf(count, SCRATCH_BYTES.toLong()).toInt())
        var left = count
        while (left > 0) {
            val read = input.read(scratch, 0, minOf(left, scratch.size.toLong()).toInt())
            if (read == -1) return false
            left -= read
            position += read
        }
        return true
    }

    private companion object {
        /** A forward jump at most this far reads through rather than reopening the file. */
        const val SKIP_THROUGH_BYTES = 256L * 1024
        const val SCRATCH_BYTES = 64 * 1024

        /** Reopens allowed in a row without any progress before a read gives up. */
        const val MAX_REOPENS = 2
    }
}
