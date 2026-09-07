package com.defnf.grid.data.remote

import com.defnf.grid.data.remote.smb.SmbClientProvider
import com.defnf.grid.data.remote.ssh.SshClientProvider
import com.defnf.grid.di.ApplicationScope
import com.defnf.grid.domain.model.Protocol
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies a [NetworkClient] for a protocol.
 *
 * An interface so the connection pool can be unit-tested against fake clients without standing up
 * the real SSH and SMB stacks.
 */
interface NetworkClientFactory {
    /**
     * Returns a **fresh** client that owns its own session.
     *
     * This is a hard requirement, not an implementation detail. [SftpClient] and [SmbClient] each
     * hold a single session in mutable fields, and the pool assumes one client per pooled entry: if
     * two entries shared an instance, connecting one would overwrite the other's session and
     * disconnecting one would close it — operations would silently run against the wrong server.
     */
    fun createClient(protocol: Protocol): NetworkClient
}

@Singleton
class DefaultNetworkClientFactory @Inject constructor(
    private val sshClientProvider: SshClientProvider,
    private val smbClientProvider: SmbClientProvider,
    @ApplicationScope private val appScope: CoroutineScope
) : NetworkClientFactory {

    // The expensive, genuinely shared state lives in the two providers (one started SshClient, one
    // SMBClient). The per-session wrapper around it is cheap, so a new one per call costs nothing.
    override fun createClient(protocol: Protocol): NetworkClient = when (protocol) {
        Protocol.SFTP -> SftpClient(sshClientProvider)
        Protocol.SMB -> SmbClient(smbClientProvider, appScope)
    }
}
