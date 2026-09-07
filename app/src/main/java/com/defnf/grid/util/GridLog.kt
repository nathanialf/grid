package com.defnf.grid.util

import android.util.Log
import com.defnf.grid.BuildConfig

/**
 * Logging for the networking layer.
 *
 * Release builds run with `isMinifyEnabled = false`, so R8's `-assumenosideeffects` trick for
 * stripping [Log] calls is not available to us. The debug-only level therefore takes a lambda and is
 * `inline`, so in a release build the message is never even constructed.
 *
 * Connect timings go out at INFO on purpose: they are low-volume, carry no credential material, and
 * are the only way to tell a slow server from a slow handshake in the field.
 */
object GridLog {

    const val TAG_SFTP = "GridSftp"
    const val TAG_SMB = "GridSmb"
    const val TAG_CONNECT = "GridConnect"

    @PublishedApi
    internal val verbose = BuildConfig.DEBUG

    inline fun d(tag: String, message: () -> String) {
        if (verbose) Log.d(tag, message())
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}
