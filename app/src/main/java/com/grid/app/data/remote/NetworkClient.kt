package com.grid.app.data.remote

import com.grid.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream

interface NetworkClient {
    suspend fun connect(connection: Connection, credential: Credential): Result<Unit>
    suspend fun disconnect(): Result<Unit>
    suspend fun listFiles(path: String): Result<List<RemoteFile>>
    suspend fun createDirectory(path: String): Result<Unit>
    suspend fun deleteFile(path: String): Result<Unit>
    suspend fun renameFile(oldPath: String, newPath: String): Result<Unit>
    suspend fun downloadFile(remotePath: String, outputStream: OutputStream): Flow<TransferProgress>
    suspend fun uploadFile(localInputStream: InputStream, remotePath: String, fileSize: Long): Flow<TransferProgress>
    fun isConnected(): Boolean
}