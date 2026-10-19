package com.defnf.grid.data.remote.ssh

import android.content.Context
import androidx.core.content.edit
import com.defnf.grid.util.GridLog
import dagger.hilt.android.qualifiers.ApplicationContext
import org.apache.sshd.client.keyverifier.ServerKeyVerifier
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.util.net.SshdSocketAddress
import java.net.SocketAddress
import java.security.PublicKey
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trust-on-first-use SSH host key checking, the same model as OpenSSH's `known_hosts`.
 *
 * The first time Grid connects to a `host:port` it records the server's key fingerprint. Every later
 * connection must present the same key; a different key is rejected, because that is exactly what a
 * man-in-the-middle on the network looks like (it would otherwise receive the user's password).
 * Servers are legitimately rekeyed too (reinstalls), so a rejection is reported to the UI through
 * [takeMismatch], which can show the new fingerprint and let the user [trust] it explicitly.
 *
 * One instance serves the shared `SshClient`, so the target is identified per session from
 * [ClientSession.getConnectAddress] — the host name the user entered, not the resolved IP.
 */
@Singleton
class HostKeyVerifier @Inject constructor(
    @ApplicationContext private val context: Context
) : ServerKeyVerifier {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Rejected fingerprints by host id, waiting for the connecting caller to pick them up. */
    private val mismatches = ConcurrentHashMap<String, String>()

    override fun verifyServerKey(
        clientSession: ClientSession,
        remoteAddress: SocketAddress,
        serverKey: PublicKey
    ): Boolean {
        val target = SshdSocketAddress.toSshdSocketAddress(clientSession.connectAddress ?: remoteAddress)
        val hostId = hostId(target.hostName, target.port)
        val fingerprint = KeyUtils.getFingerPrint(serverKey)
        val known = prefs.getString(hostId, null)

        return when (known) {
            null -> {
                GridLog.d(TAG) { "Trusting first-seen host key for $hostId: $fingerprint" }
                prefs.edit { putString(hostId, fingerprint) }
                true
            }
            fingerprint -> true
            else -> {
                GridLog.w(TAG, "Host key for $hostId changed: expected $known, got $fingerprint")
                mismatches[hostId] = fingerprint
                false
            }
        }
    }

    /** The fingerprint a just-failed connect was rejected for, if it failed on a host key change. */
    fun takeMismatch(host: String, port: Int): String? = mismatches.remove(hostId(host, port))

    /** Records [fingerprint] as the trusted key for this server, replacing any previous one. */
    fun trust(host: String, port: Int, fingerprint: String) {
        prefs.edit { putString(hostId(host, port), fingerprint) }
    }

    /** The trusted fingerprint for this server, or null if Grid hasn't connected to it yet. */
    fun knownFingerprint(host: String, port: Int): String? = prefs.getString(hostId(host, port), null)

    private fun hostId(host: String, port: Int): String =
        "${host.trim().trimEnd('.').lowercase(Locale.ROOT)}:$port"

    private companion object {
        const val TAG = "HostKeyVerifier"
        const val PREFS_NAME = "ssh_known_hosts"
    }
}
