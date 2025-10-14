package com.grid.app.data.remote

import com.grid.app.domain.model.Protocol
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkClientFactory @Inject constructor(
    private val ftpClient: FtpClient,
    private val sftpClient: SftpClient,
    private val smbClient: SmbClient
) {
    
    fun createClient(protocol: Protocol): NetworkClient {
        return when (protocol) {
            Protocol.FTP -> ftpClient
            Protocol.SFTP -> sftpClient
            Protocol.SMB -> smbClient
        }
    }
}