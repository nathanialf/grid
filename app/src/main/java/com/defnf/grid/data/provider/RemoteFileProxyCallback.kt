package com.defnf.grid.data.provider

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ProxyFileDescriptorCallback
import android.system.ErrnoException
import android.system.OsConstants
import androidx.annotation.RequiresApi
import com.defnf.grid.util.GridLog

/**
 * Backs a seekable file descriptor with a [RemoteRangeReader], so an app reading a server file gets
 * its descriptor at once and the bytes as it asks for them, instead of waiting for a full download.
 *
 * Every callback runs on [thread]. A descriptor the reading app leaves open but stops reading lets
 * go of its server stream after [IDLE_CLOSE_MS], so it doesn't hold one of the session's slots.
 */
@RequiresApi(Build.VERSION_CODES.O)
internal class RemoteFileProxyCallback(
    private val reader: RemoteRangeReader,
    private val thread: HandlerThread,
    private val handler: Handler,
    private val label: String
) : ProxyFileDescriptorCallback() {

    private val closeIdle = Runnable { reader.closeStream() }

    init {
        // The reader was primed with an open stream; an app that never reads must not keep it.
        handler.postDelayed(closeIdle, IDLE_CLOSE_MS)
    }

    override fun onGetSize(): Long = reader.size

    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        handler.removeCallbacks(closeIdle)
        return try {
            reader.read(offset, size, data)
        } catch (e: Exception) {
            GridLog.w(TAG, "Reading $label at $offset failed", e)
            throw ErrnoException("onRead", OsConstants.EIO)
        } finally {
            handler.postDelayed(closeIdle, IDLE_CLOSE_MS)
        }
    }

    override fun onRelease() {
        handler.removeCallbacks(closeIdle)
        reader.close()
        thread.quitSafely()
    }

    private companion object {
        const val TAG = "GridDocuments"
        const val IDLE_CLOSE_MS = 10_000L
    }
}
