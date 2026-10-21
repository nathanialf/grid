package com.defnf.grid.data.provider

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException

class RemoteRangeReaderTest {

    private val content = ByteArray(1_000_000) { (it % 251).toByte() }
    private val opens = mutableListOf<Long>()

    /** A server stream that can be told to die after handing out some bytes. */
    private inner class FlakyStream(offset: Long, private var failAfter: Int) : InputStream() {
        private val inner = ByteArrayInputStream(content, offset.toInt(), content.size - offset.toInt())
        override fun read(): Int = throw UnsupportedOperationException()
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (failAfter <= 0) throw IOException("connection reset")
            val read = inner.read(b, off, minOf(len, failAfter, 4096))
            if (read > 0) failAfter -= read
            return read
        }
    }

    private fun reader(failAfter: (Int) -> Int = { Int.MAX_VALUE }) =
        RemoteRangeReader(content.size.toLong()) { offset ->
            opens += offset
            FlakyStream(offset, failAfter(opens.size))
        }

    private fun readAll(reader: RemoteRangeReader, chunk: Int = 128 * 1024): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(chunk)
        var offset = 0L
        while (true) {
            val read = reader.read(offset, chunk, buffer)
            if (read == 0) break
            out.write(buffer, 0, read)
            offset += read
        }
        return out.toByteArray()
    }

    @Test
    fun `sequential reads use one stream and fill every chunk`() {
        val reader = reader()
        assertArrayEquals(content, readAll(reader))
        assertEquals(listOf(0L), opens)
    }

    @Test
    fun `a dropped stream is reopened where it stopped`() {
        // The first stream dies 300 KB in; the reopened one carries on.
        val reader = reader { attempt -> if (attempt == 1) 300_000 else Int.MAX_VALUE }
        assertArrayEquals(content, readAll(reader))
        assertEquals(listOf(0L, 300_000L), opens)
    }

    @Test
    fun `an unchecked failure is retried like an IOException`() {
        var failed = false
        val reader = RemoteRangeReader(content.size.toLong()) { offset ->
            opens += offset
            if (!failed) {
                failed = true
                object : InputStream() {
                    override fun read(): Int = throw IllegalStateException("session dropped")
                    override fun read(b: ByteArray, off: Int, len: Int): Int = throw IllegalStateException("session dropped")
                }
            } else {
                FlakyStream(offset, Int.MAX_VALUE)
            }
        }
        assertArrayEquals(content, readAll(reader))
        assertEquals(listOf(0L, 0L), opens)
    }

    @Test(expected = InterruptedIOException::class)
    fun `an interrupted read is not retried`() {
        val reader = RemoteRangeReader(content.size.toLong()) { offset ->
            opens += offset
            object : InputStream() {
                override fun read(): Int = throw InterruptedIOException()
                override fun read(b: ByteArray, off: Int, len: Int): Int = throw InterruptedIOException()
            }
        }
        try {
            reader.read(0, 1024, ByteArray(1024))
        } finally {
            assertEquals(listOf(0L), opens)
        }
    }

    @Test(expected = IOException::class)
    fun `a server that keeps failing still fails the read`() {
        reader { 0 }.read(0, 1024, ByteArray(1024))
    }

    @Test
    fun `a short hop forward reads through, a jump reopens`() {
        val reader = reader()
        val buffer = ByteArray(1024)
        reader.read(0, 1024, buffer)
        reader.read(100_000, 1024, buffer)
        assertArrayEquals(content.copyOfRange(100_000, 101_024), buffer)
        reader.read(900_000, 1024, buffer)
        assertArrayEquals(content.copyOfRange(900_000, 901_024), buffer)
        reader.read(10, 1024, buffer)
        assertArrayEquals(content.copyOfRange(10, 1034), buffer)
        assertEquals(listOf(0L, 900_000L, 10L), opens)
    }

    @Test
    fun `reads stop at the end of the file`() {
        val buffer = ByteArray(4096)
        val reader = reader()
        assertEquals(1000, reader.read(content.size - 1000L, 4096, buffer))
        assertEquals(0, reader.read(content.size.toLong(), 4096, buffer))
    }

    @Test
    fun `an idle close reopens on the next read`() {
        val reader = reader()
        val buffer = ByteArray(1024)
        reader.read(0, 1024, buffer)
        reader.closeStream()
        reader.read(1024, 1024, buffer)
        assertArrayEquals(content.copyOfRange(1024, 2048), buffer)
        assertEquals(listOf(0L, 1024L), opens)
    }
}
