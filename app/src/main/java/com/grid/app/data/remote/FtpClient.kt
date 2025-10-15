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
import org.apache.commons.net.ftp.FTPReply
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class FtpClient @Inject constructor() : NetworkClient {
    
    private var ftpClient: FTPClient? = null
    
    override suspend fun connect(connection: Connection, credential: Credential): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = FTPClient().apply {
                connectTimeout = 30000  // Increased timeout to 30 seconds
                defaultTimeout = 30000
                // Note: controlKeepAliveTimeout is deprecated, using setKeepAlive instead
            }
            
            // Connect to server
            client.connect(connection.hostname, connection.effectivePort)
            
            // Check if connection was successful
            val replyCode = client.replyCode
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                client.disconnect()
                return@withContext Result.Error(Exception("FTP server refused connection. Reply code: $replyCode"))
            }
            
            // Attempt login
            if (!client.login(credential.username, credential.password)) {
                val replyString = client.replyString
                client.disconnect()
                return@withContext Result.Error(Exception("Authentication failed: $replyString"))
            }
            
            // Try passive mode first, fallback to active if it fails
            try {
                client.enterLocalPassiveMode()
                // Test passive mode with a simple command
                client.printWorkingDirectory()
            } catch (e: Exception) {
                println("Passive mode failed, trying active mode: ${e.message}")
                client.enterLocalActiveMode()
            }
            
            // Set binary file type for proper file transfers
            if (!client.setFileType(FTP.BINARY_FILE_TYPE)) {
                println("Warning: Could not set binary file type")
            }
            
            // Enable keep-alive to maintain connection
            client.setKeepAlive(true)
            
            ftpClient = client
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Exception("FTP connection failed: ${e.message}", e))
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
            
            if (!client.isConnected) {
                return@withContext Result.Error(Exception("Connection lost"))
            }
            
            // Normalize path - ensure it starts with / for absolute paths
            val normalizedPath = if (path.isEmpty() || path == "/") "/" else path
            
            val files = client.listFiles(normalizedPath)
            
            // Check if the listFiles operation was successful
            if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                return@withContext Result.Error(Exception("Failed to list files: ${client.replyString}"))
            }
            
            val remoteFiles = files.mapNotNull { ftpFile ->
                // Skip null entries and special directories
                if (ftpFile?.name == null || ftpFile.name == "." || ftpFile.name == "..") {
                    null
                } else {
                    RemoteFile(
                        name = ftpFile.name,
                        path = if (normalizedPath.endsWith("/")) "$normalizedPath${ftpFile.name}" else "$normalizedPath/${ftpFile.name}",
                        isDirectory = ftpFile.isDirectory,
                        size = if (ftpFile.isDirectory) 0L else ftpFile.size,
                        lastModified = ftpFile.timestamp?.timeInMillis ?: 0L,
                        isHidden = ftpFile.name.startsWith(".")
                    )
                }
            }
            
            Result.Success(remoteFiles)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to list files: ${e.message}", e))
        }
    }
    
    override suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext Result.Error(Exception("Not connected"))
            
            if (!client.isConnected) {
                return@withContext Result.Error(Exception("Connection lost"))
            }
            
            val success = client.makeDirectory(path)
            if (success) {
                Result.Success(Unit)
            } else {
                Result.Error(Exception("Failed to create directory: ${client.replyString}"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Failed to create directory: ${e.message}", e))
        }
    }
    
    override suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext Result.Error(Exception("Not connected"))
            
            if (!client.isConnected) {
                return@withContext Result.Error(Exception("Connection lost"))
            }
            
            // First check if path is a directory
            val parentPath = path.substringBeforeLast("/")
            val fileName = path.substringAfterLast("/")
            val files = client.listFiles(parentPath.ifEmpty { "/" })
            val targetFile = files.find { it.name == fileName }
            
            val success = if (targetFile?.isDirectory == true) {
                client.removeDirectory(path)
            } else {
                client.deleteFile(path)
            }
            
            if (success) {
                Result.Success(Unit)
            } else {
                Result.Error(Exception("Failed to delete file: ${client.replyString}"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Failed to delete file: ${e.message}", e))
        }
    }
    
    override suspend fun renameFile(oldPath: String, newPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext Result.Error(Exception("Not connected"))
            
            if (!client.isConnected) {
                return@withContext Result.Error(Exception("Connection lost"))
            }
            
            val success = client.rename(oldPath, newPath)
            if (success) {
                Result.Success(Unit)
            } else {
                Result.Error(Exception("Failed to rename file: ${client.replyString}"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Failed to rename file: ${e.message}", e))
        }
    }
    
    override suspend fun downloadFile(remotePath: String, outputStream: OutputStream): Flow<TransferProgress> = flow {
        val client = ftpClient ?: throw Exception("Not connected")
        
        if (!client.isConnected) {
            throw Exception("Connection lost")
        }
        
        // Get file size for progress tracking
        val parentPath = remotePath.substringBeforeLast("/")
        val fileName = remotePath.substringAfterLast("/")
        val files = client.listFiles(parentPath.ifEmpty { "/" })
        val fileSize = files.find { it.name == fileName }?.size ?: 0L
        
        var bytesTransferred = 0L
        val startTime = System.currentTimeMillis()
        
        val inputStream = client.retrieveFileStream(remotePath)
            ?: throw Exception("Failed to start download: ${client.replyString}")
        
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
            // Complete the pending command to finalize the transfer
            if (!client.completePendingCommand()) {
                throw Exception("Failed to complete download: ${client.replyString}")
            }
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun uploadFile(localInputStream: InputStream, remotePath: String, fileSize: Long): Flow<TransferProgress> = flow {
        val client = ftpClient ?: throw Exception("Not connected")
        
        if (!client.isConnected) {
            throw Exception("Connection lost")
        }
        
        var bytesTransferred = 0L
        val startTime = System.currentTimeMillis()
        
        val outputStream = client.storeFileStream(remotePath)
            ?: throw Exception("Failed to start upload: ${client.replyString}")
        
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
            // Complete the pending command to finalize the transfer
            if (!client.completePendingCommand()) {
                throw Exception("Failed to complete upload: ${client.replyString}")
            }
        }
    }.flowOn(Dispatchers.IO)
    
    override fun isConnected(): Boolean {
        return ftpClient?.isConnected == true
    }
    
    // Helper method to validate connection before operations
    private suspend fun ensureConnected(): Boolean {
        val client = ftpClient ?: return false
        
        if (!client.isConnected) {
            return false
        }
        
        // Send a simple command to test if connection is still alive
        return try {
            client.printWorkingDirectory()
            true
        } catch (e: Exception) {
            false
        }
    }
}