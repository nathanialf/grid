package com.defnf.grid.domain.repository

import com.defnf.grid.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.io.File

interface FileRepository {
    suspend fun testConnection(connection: Connection, credential: Credential): Result<Unit>

    suspend fun listFiles(connection: Connection, path: String): List<RemoteFile>
    suspend fun getFiles(connection: Connection, path: String): List<RemoteFile>
    suspend fun createDirectory(connection: Connection, directoryPath: String)
    suspend fun deleteFile(connection: Connection, filePath: String)
    suspend fun renameFile(connection: Connection, oldPath: String, newPath: String)

    suspend fun downloadFile(connection: Connection, file: RemoteFile, localPath: String)
    suspend fun downloadFileWithProgress(
        connection: Connection,
        file: RemoteFile,
        localPath: String
    ): Flow<FileTransfer>
    suspend fun downloadFileToTemp(connection: Connection, file: RemoteFile): File

    suspend fun uploadFile(connection: Connection, localPath: String, remotePath: String)
    suspend fun uploadFileWithProgress(
        connection: Connection,
        localPath: String,
        remotePath: String
    ): Flow<FileTransfer>

    fun getActiveTransfers(): Flow<List<FileTransfer>>
    suspend fun cancelTransfer(transferId: String): Result<Unit>
    suspend fun pauseTransfer(transferId: String): Result<Unit>
    suspend fun resumeTransfer(transferId: String): Result<Unit>

    // Cached files management
    fun getCachedFilesForConnection(connectionId: String): List<CachedFile>
    fun clearCacheForConnection(connectionId: String): Boolean
    fun deleteCachedFile(filePath: String): Boolean
    fun getCacheSizeForConnection(connectionId: String): Long
}
