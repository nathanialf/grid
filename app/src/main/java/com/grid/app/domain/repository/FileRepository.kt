package com.grid.app.domain.repository

import com.grid.app.domain.model.*
import kotlinx.coroutines.flow.Flow

interface FileRepository {
    suspend fun connect(connection: Connection): Result<Unit>
    suspend fun disconnect(connectionId: String): Result<Unit>
    suspend fun isConnected(connectionId: String): Boolean
    suspend fun testConnection(connection: Connection, credential: Credential): Result<Unit>
    
    suspend fun listFiles(connectionId: String, path: String): Result<List<RemoteFile>>
    suspend fun listFiles(connection: Connection, path: String): List<RemoteFile>
    suspend fun getFiles(connection: Connection, path: String): List<RemoteFile>
    suspend fun createDirectory(connectionId: String, path: String): Result<Unit>
    suspend fun createDirectory(connection: Connection, directoryPath: String)
    suspend fun deleteFile(connectionId: String, path: String): Result<Unit>
    suspend fun deleteFile(connection: Connection, filePath: String)
    suspend fun renameFile(connectionId: String, oldPath: String, newPath: String): Result<Unit>
    suspend fun renameFile(connection: Connection, oldPath: String, newPath: String)
    
    suspend fun downloadFile(
        connectionId: String,
        remotePath: String,
        localPath: String
    ): Flow<FileTransfer>
    
    suspend fun downloadFile(connection: Connection, file: RemoteFile, localPath: String)
    
    suspend fun downloadFileWithProgress(connection: Connection, file: RemoteFile, localPath: String): Flow<FileTransfer>
    
    suspend fun downloadFileToTemp(connection: Connection, file: RemoteFile): java.io.File
    
    suspend fun uploadFile(
        connectionId: String,
        localPath: String,
        remotePath: String
    ): Flow<FileTransfer>
    
    suspend fun uploadFile(connection: Connection, localPath: String, remotePath: String)
    
    suspend fun uploadFileWithProgress(connection: Connection, localPath: String, remotePath: String): Flow<FileTransfer>
    
    fun getActiveTransfers(): Flow<List<FileTransfer>>
    suspend fun cancelTransfer(transferId: String): Result<Unit>
    suspend fun pauseTransfer(transferId: String): Result<Unit>
    suspend fun resumeTransfer(transferId: String): Result<Unit>
}