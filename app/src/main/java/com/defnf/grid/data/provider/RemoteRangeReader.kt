package com.defnf.grid.data.provider

import java.io.Closeable
import java.io.IOException
import java.io.InputStream

/**
 * Serves reads at arbitrary offsets of a server file from one sequential stream.
 *
 * Apps that read a document (most often to upload it somewhere) read it front to back, so the open
 * stream simply keeps going; a jump reopens it at the new offset, and a short hop forward reads
 * through instead, which is cheaper than a reopen. A stream that fails mid-read (Wi-Fi to cellular,
 * a server dropping an idle session) is reopened where it stopped instead of failing the read, so a
 * blip costs the reading app a pause rather than its whole upload.
 *
 * Calls are synchronized: the reader is primed on the thread that opens the document and read on
 * the descriptor's own thread afterwards.
 */
internal class RemoteRangeReader(
    val size: Long,
    private val open: (offset: Long) -> InputStream
) : Closeable {

    private var stream: InputStream? = null
    private var position = 0L

    /** Opens the stream at the start, so a file that can't be read fails to open, not on first read. */
    @Synchronized
    fun prime() {
        if (size > 0) streamAt(0)
    }

    /** Reads up to [length] bytes at [offset] into [data]; fewer only at the end of the file. */
    @Synchronized
    fun read(offset: Long, length: Int, data: ByteArray): Int {
        if (length <= 0 || offset >= size) return 0
        val wanted = minOf(length.toLong(), size - offset).toInt()
        var filled = 0
        var failures = 0
        while (filled < wanted) {
            try {
                val read = streamAt(offset + filled).read(data, filled, wanted - filled)
                if (read == -1) break // The file shrank on the server since it was listed.
                filled += read
                position += read
                failures = 0
            } catch (e: IOException) {
                closeStream()
                if (++failures > MAX_REOPENS) throw e
            }
        }
        return filled
    }

    /** Lets go of the server stream (and its pooled session) until the next read needs it. */
    @Synchronized
    fun closeStream() {
        val current = stream ?: return
        stream = null
        try {
            current.close()
        } catch (ignored: IOException) {
            // Closing a stream that already broke can fail too; it's gone either way.
        }
    }

    @Synchronized
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
