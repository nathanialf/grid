package com.defnf.grid.data.remote.ssh

import org.apache.sshd.client.SshClient
import org.apache.sshd.core.CoreModuleProperties
import org.apache.sshd.sftp.SftpModuleProperties
import java.time.Duration

/**
 * Transport and timeout settings for the shared [SshClient].
 *
 * SSHD's defaults are tuned for a server or a desktop client: a one-minute connect timeout and a
 * two-minute authentication timeout are far too generous for a phone, where the user has already
 * given up. Applied once when the client is built, never per connect.
 */
internal object SshTuning {

    /** TCP connect. A LAN SYN answers in single-digit milliseconds; this covers slow cellular. */
    val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(8)

    /**
     * Key exchange plus authentication. `ConnectFuture.verify()` returns as soon as the session
     * object exists, so the key exchange actually completes during `auth()` and is charged here.
     */
    val AUTH_TIMEOUT: Duration = Duration.ofSeconds(20)

    /** Ceiling on the whole connect sequence, so the UI can never wedge on one phase. */
    const val OVERALL_CONNECT_TIMEOUT_MS = 30_000L

    /** Matches Apache SSHD's own SFTP stream buffer, so one app read maps to one SFTP read. */
    const val TRANSFER_BUFFER_BYTES = 32 * 1024

    fun apply(client: SshClient) {
        // Latency. Both default to false.
        CoreModuleProperties.TCP_NODELAY.set(client, true)
        CoreModuleProperties.SOCKET_KEEPALIVE.set(client, true)

        CoreModuleProperties.IO_CONNECT_TIMEOUT.set(client, CONNECT_TIMEOUT)
        CoreModuleProperties.AUTH_TIMEOUT.set(client, AUTH_TIMEOUT)
        // Default is a full minute, which would block teardown of a shared client.
        CoreModuleProperties.STOP_WAIT_TIME.set(client, Duration.ofSeconds(5))
        SftpModuleProperties.SFTP_CHANNEL_OPEN_TIMEOUT.set(client, Duration.ofSeconds(10))

        // Keep NIO2_READ_TIMEOUT at its Duration.ZERO default: a read timeout would tear down idle
        // pooled sessions. A heartbeat detects a dead peer without punishing an idle one, and it also
        // makes the pool's liveness check free — SSHD closes the session itself after the misses.
        CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, Duration.ofSeconds(30))
        CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX.set(client, 3)
        // The pool owns idle policy, so disable SSHD's own 10-minute idle teardown.
        CoreModuleProperties.IDLE_TIMEOUT.set(client, Duration.ZERO)

        // If a hardened server leaves group-exchange as the only match, cap the prime it may choose.
        // SSHD would otherwise ask for 4096 bits and accept up to 8192.
        CoreModuleProperties.PROP_DHGEX_CLIENT_MIN_KEY.set(client, 2048)
        CoreModuleProperties.PROP_DHGEX_CLIENT_PRF_KEY.set(client, 2048)
        CoreModuleProperties.PROP_DHGEX_CLIENT_MAX_KEY.set(client, 4096)
    }
}
