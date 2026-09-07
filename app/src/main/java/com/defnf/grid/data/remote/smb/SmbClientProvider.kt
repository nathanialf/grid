package com.defnf.grid.data.remote.smb

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the one configured [SMBClient] the app uses for every SMB session.
 *
 * Constructing an `SMBClient` per connect, as the previous code did, seeds a fresh `SecureRandom`,
 * generates a new client GUID, and loads the security provider each time — and it defeats smbj's own
 * `connectionTable`, which is keyed on `"host:port"` and would otherwise hand back an established
 * connection.
 */
@Singleton
class SmbClientProvider @Inject constructor() {

    val client: SMBClient by lazy { SMBClient(buildConfig()) }

    /** Forces construction so the first connect does not pay for it. */
    fun warmUp() {
        client.hashCode()
    }

    private fun buildConfig(): SmbConfig = SmbConfig.builder()
        // Deliberately left at 0 (infinite). smbj's PacketReader wraps *any* IOException from the
        // socket — SocketTimeoutException included — in a TransportException and kills the
        // connection. A non-zero soTimeout would therefore mean "tear down any connection idle
        // longer than this", which would quietly defeat connection reuse. Bound requests with the
        // read/write/transact timeout below, and the whole connect with withTimeout, instead.
        .withSoTimeout(0)
        // Sets read, write and transact timeouts together. The default is 60s for all three, which is
        // far longer than a user will wait.
        .withTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // Set explicitly so nobody turns it on: true prepends a legacy SMB1 negotiate round trip.
        .withMultiProtocolNegotiate(false)
        // Advertise signing but only sign when the server requires it. Requiring it would put an
        // AES-CMAC on every packet, which is a throughput tax rather than a connect-time win.
        .withSigningEnabled(true)
        .withSigningRequired(false)
        // DFS resolution costs an extra referral round trip per path. Off unless a namespace needs it.
        .withDfsEnabled(false)
        .build()

    private companion object {
        const val REQUEST_TIMEOUT_SECONDS = 20L
    }
}
