package com.grid.app.data.remote

import com.grid.app.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class FtpClient @Inject constructor() : NetworkClient {
    
    private var ftpClient: FTPClient? = null
    
    override suspend fun connect(connection: Connection, credential: Credential): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = FTPClient().apply {
                connectTimeout = 10000
                defaultTimeout = 10000
            }
            
            client.connect(connection.hostname, connection.effectivePort)
            
            if (!client.login(credential.username, credential.password)) {
                client.disconnect()
                return@withContext Result.Error(Exception("Authentication failed"))
            }
            
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            
            ftpClient = client
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ftpClient?.let { client ->
                if (client.isConnected) {
                    client.logout()
                    client.disconnect()
                }
            }
            ftpClient = null
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun listFiles(path: String): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext Result.Error(Exception("Not connected"))
            
            val files = client.listFiles(path)
            val remoteFiles = files.map { ftpFile ->
                RemoteFile(
                    name = ftpFile.name,
                    path = if (path.endsWith("/")) "$path${ftpFile.name}" else "$path/${ftpFile.name}",
                    isDirectory = ftpFile.isDirectory,
                    size = ftpFile.size,
                    lastModified = ftpFile.timestamp?.timeInMillis ?: 0L,
                    isHidden = ftpFile.name.startsWith(".")
                )
            }.filter { it.name != "." && it.name != ".." }
            
            Result.Success(remoteFiles)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext Result.Error(Exception("Not connected"))
            
            val success = client.makeDirectory(path)
            if (success) {
                Result.Success(Unit)
            } else {
                Result.Error(Exception("Failed to create directory"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext Result.Error(Exception("Not connected"))
            
            val success = if (client.listFiles(path).firstOrNull()?.isDirectory == true) {
                client.removeDirectory(path)
            } else {
                client.deleteFile(path)
            }
            
            if (success) {
                Result.Success(Unit)
            } else {
                Result.Error(Exception("Failed to delete file"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun renameFile(oldPath: String, newPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext Result.Error(Exception("Not connected"))
            
            val success = client.rename(oldPath, newPath)
            if (success) {
                Result.Success(Unit)
            } else {
                Result.Error(Exception("Failed to rename file"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun downloadFile(remotePath: String, outputStream: OutputStream): Flow<TransferProgress> = flow {
        val client = ftpClient ?: throw Exception("Not connected")
        
        val fileSize = client.listFiles(remotePath).firstOrNull()?.size ?: 0L
        var bytesTransferred = 0L
        val startTime = System.currentTimeMillis()
        
        val inputStream = client.retrieveFileStream(remotePath)
            ?: throw Exception("Failed to start download")
        
        try {
            val buffer = ByteArray(8192)
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                bytesTransferred += bytesRead
                
                val currentTime = System.currentTimeMillis()
                val elapsedTime = currentTime - startTime
                val speed = if (elapsedTime > 0) (bytesTransferred * 1000) / elapsedTime else 0L
                val timeRemaining = if (speed > 0 && fileSize > 0) {
                    ((fileSize - bytesTransferred) * 1000) / speed
                } else 0L
                
                emit(TransferProgress(bytesTransferred, fileSize, speed, timeRemaining))
            }
        } finally {
            inputStream.close()
            outputStream.close()
            client.completePendingCommand()
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun uploadFile(localInputStream: InputStream, remotePath: String, fileSize: Long): Flow<TransferProgress> = flow {
        val client = ftpClient ?: throw Exception("Not connected")
        
        var bytesTransferred = 0L
        val startTime = System.currentTimeMillis()
        
        val outputStream = client.storeFileStream(remotePath)
            ?: throw Exception("Failed to start upload")
        
        try {
            val buffer = ByteArray(8192)
            var bytesRead: Int
            
            while (localInputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                bytesTransferred += bytesRead
                
                val currentTime = System.currentTimeMillis()
                val elapsedTime = currentTime - startTime
                val speed = if (elapsedTime > 0) (bytesTransferred * 1000) / elapsedTime else 0L
                val timeRemaining = if (speed > 0 && fileSize > 0) {
                    ((fileSize - bytesTransferred) * 1000) / speed
                } else 0L
                
                emit(TransferProgress(bytesTransferred, fileSize, speed, timeRemaining))
            }
        } finally {
            localInputStream.close()
            outputStream.close()
            client.completePendingCommand()
        }
    }.flowOn(Dispatchers.IO)
    
    override fun isConnected(): Boolean {
        return ftpClient?.isConnected == true
    }
}