package com.defnf.grid.data.remote

import com.defnf.grid.domain.model.Protocol
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkClientFactory @Inject constructor(
    private val sftpClient: SftpClient,
    private val smbClient: SmbClient
) {
    
    fun createClient(protocol: Protocol): NetworkClient {
        return when (protocol) {
            Protocol.SFTP -> sftpClient
            Protocol.SMB -> smbClient
        }
    }
}