package com.defnf.grid.data.remote.pool

import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.smbj.common.SMBRuntimeException
import org.apache.sshd.common.SshException
import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.channels.ClosedChannelException

/**
 * Distinguishes "the connection died" from "the server answered, and the answer was no".
 *
 * This matters because only the former justifies transparently reconnecting and retrying. An
 * `SftpException` carrying `NO_SUCH_FILE` or `PERMISSION_DENIED` is a perfectly healthy session
 * giving a legitimate answer — retrying it would just repeat the same failure on a new connection, so
 * it is deliberately *not* matched here.
 */
internal object ConnectionFaults {

    private const val MAX_CAUSE_DEPTH = 8

    private val DEAD_SESSION_STATUSES = setOf(
        NtStatus.STATUS_NETWORK_SESSION_EXPIRED,
        NtStatus.STATUS_USER_SESSION_DELETED,
        NtStatus.STATUS_NETWORK_NAME_DELETED,
        NtStatus.STATUS_CONNECTION_DISCONNECTED,
        NtStatus.STATUS_CONNECTION_RESET
    )

    /** Message fragments that signal a dead transport when the exception type alone is ambiguous. */
    private val CLOSED_MARKERS = listOf(
        "channel is closed",
        "closed channel",
        "session is closed",
        "broken pipe",
        "connection reset",
        "not connected"
    )

    fun isConnectionFault(throwable: Throwable): Boolean =
        generateSequence(throwable) { it.cause }
            .take(MAX_CAUSE_DEPTH)
            .any { it.indicatesDeadTransport() }

    private fun Throwable.indicatesDeadTransport(): Boolean = when (this) {
        is SocketException,
        is SocketTimeoutException,
        is EOFException,
        is ClosedChannelException,
        is SshException,
        is TransportException -> true

        // SMBApiException extends SMBRuntimeException but is usually the server answering
        // (not found, access denied); only session/connection teardown statuses mean the
        // transport is gone. Must be matched before SMBRuntimeException.
        is SMBApiException -> status in DEAD_SESSION_STATUSES

        is SMBRuntimeException -> true

        is IOException -> {
            val text = message
            text != null && CLOSED_MARKERS.any { text.contains(it, ignoreCase = true) }
        }

        else -> false
    }
}
