package com.grid.app.data.remote

import com.grid.app.domain.model.*
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection as SmbConnection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import javax.inject.Inject

class SmbClient @Inject constructor() : NetworkClient {
    
    private var smbClient: SMBClient? = null
    private var connection: SmbConnection? = null
    private var session: Session? = null
    private var diskShare: DiskShare? = null
    
    override suspend fun connect(connection: Connection, credential: Credential): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = SMBClient()
            val conn = client.connect(connection.hostname, connection.effectivePort)
            
            val authContext = AuthenticationContext(
                credential.username,
                credential.password.toCharArray(),
                connection.hostname
            )
            
            val session = conn.authenticate(authContext)
            
            // Try to connect to specified share or discover available shares
            val shareResult = if (!connection.shareName.isNullOrEmpty()) {
                // Use specified share name
                println("SMB: Connecting to specified share: ${connection.shareName}")
                try {
                    val share = session.connectShare(connection.shareName)
                    if (share is DiskShare) {
                        println("SMB: Successfully connected to share: ${connection.shareName}")
                        share
                    } else {
                        throw Exception("Share '${connection.shareName}' is not a disk share")
                    }
                } catch (e: Exception) {
                    throw Exception("Failed to connect to share '${connection.shareName}': ${e.message}")
                }
            } else {
                // Try to connect to available shares
                println("SMB: No share specified, trying common share names...")
                val shareNames = listOf("C$", "D$", "E$", "Users", "Public", "shared", "share", "data", "home")
                var diskShare: DiskShare? = null
                var successfulShareName: String? = null
                val failedShares = mutableListOf<String>()
                
                for (shareName in shareNames) {
                    try {
                        println("SMB: Trying share: $shareName")
                        val share = session.connectShare(shareName)
                        if (share is DiskShare) {
                            println("SMB: Successfully connected to share: $shareName")
                            diskShare = share
                            successfulShareName = shareName
                            break
                        } else {
                            failedShares.add("$shareName (not a disk share)")
                        }
                    } catch (e: Exception) {
                        // Only log BAD_NETWORK_NAME as debug, it's expected for non-existent shares
                        if (e.message?.contains("STATUS_BAD_NETWORK_NAME") != true) {
                            println("SMB: Share $shareName failed: ${e.message}")
                        }
                        failedShares.add("$shareName (${e.message})")
                        continue
                    }
                }
                
                if (diskShare == null) {
                    throw Exception(
                        "No accessible disk shares found.\n" +
                        "Tried: ${failedShares.joinToString(", ")}\n" +
                        "Please specify a share name in the connection settings.\n" +
                        "Common share names: Users, Public, shared, C$, D$"
                    )
                } else {
                    println("SMB: Connection successful using share: $successfulShareName")
                    // Don't report failed shares when we found a working one
                }
                
                diskShare
            }
            
            this@SmbClient.smbClient = client
            this@SmbClient.connection = conn
            this@SmbClient.session = session
            this@SmbClient.diskShare = shareResult
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            diskShare?.close()
            session?.close()
            connection?.close()
            smbClient?.close()
            
            diskShare = null
            session = null
            connection = null
            smbClient = null
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun listFiles(path: String): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        try {
            val share = diskShare ?: return@withContext Result.Error(Exception("Not connected"))
            
            val files = share.list(path)
            val remoteFiles = files.map { fileInfo ->
                val isDirectory = fileInfo.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
                RemoteFile(
                    name = fileInfo.fileName,
                    path = if (path.endsWith("\\")) "$path${fileInfo.fileName}" else "$path\\${fileInfo.fileName}",
                    isDirectory = isDirectory,
                    size = fileInfo.endOfFile,
                    lastModified = 0L, // TODO: Fix FileTime conversion
                    isHidden = fileInfo.fileAttributes and FileAttributes.FILE_ATTRIBUTE_HIDDEN.value != 0L
                )
            }.filter { it.name != "." && it.name != ".." }
            
            Result.Success(remoteFiles)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val share = diskShare ?: return@withContext Result.Error(Exception("Not connected"))
            
            share.mkdir(path)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val share = diskShare ?: return@withContext Result.Error(Exception("Not connected"))
            
            val fileInfo = share.getFileInformation(path)
            val isDirectory = fileInfo.standardInformation.isDirectory
            
            if (isDirectory) {
                // Recursively delete directory contents first
                deleteDirectoryRecursive(share, path)
            } else {
                share.rm(path)
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    private fun deleteDirectoryRecursive(share: DiskShare, path: String) {
        try {
            // List all items in the directory
            val items = share.list(path)
            
            for (item in items) {
                // Skip . and .. entries
                if (item.fileName == "." || item.fileName == "..") {
                    continue
                }
                
                val itemPath = if (path.endsWith("/")) {
                    "$path${item.fileName}"
                } else {
                    "$path/${item.fileName}"
                }
                
                val itemInfo = share.getFileInformation(itemPath)
                if (itemInfo.standardInformation.isDirectory) {
                    // Recursively delete subdirectory
                    deleteDirectoryRecursive(share, itemPath)
                } else {
                    // Delete file
                    share.rm(itemPath)
                }
            }
            
            // Finally, delete the empty directory
            share.rmdir(path, true)
        } catch (e: Exception) {
            // If the recursive delete fails, try to force delete the directory
            try {
                share.rmdir(path, true)
            } catch (fallbackEx: Exception) {
                throw e // Throw the original exception
            }
        }
    }
    
    override suspend fun renameFile(oldPath: String, newPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val share = diskShare ?: return@withContext Result.Error(Exception("Not connected"))
            
            // Check if it's a directory first
            val fileInfo = share.getFileInformation(oldPath)
            val isDirectory = fileInfo.standardInformation.isDirectory
            
            println("SMB: Renaming ${if (isDirectory) "directory" else "file"} from '$oldPath' to '$newPath'")
            
            if (isDirectory) {
                // For directories, use FILE_OPEN_IF and DIRECTORY_FILE attributes
                val directory = share.openDirectory(
                    oldPath,
                    EnumSet.of(AccessMask.DELETE),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                
                try {
                    directory.rename(newPath, false)
                } finally {
                    directory.close()
                }
            } else {
                // For files, use the original logic
                val file = share.openFile(
                    oldPath,
                    EnumSet.of(AccessMask.DELETE),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                
                try {
                    file.rename(newPath, false)
                } finally {
                    file.close()
                }
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            println("SMB: Rename failed: ${e.message}")
            Result.Error(e)
        }
    }
    
    override suspend fun downloadFile(remotePath: String, outputStream: OutputStream): Flow<TransferProgress> = flow {
        val share = diskShare ?: throw Exception("Not connected")
        
        val fileInfo = share.getFileInformation(remotePath)
        val fileSize = fileInfo.standardInformation.endOfFile
        var bytesTransferred = 0L
        val startTime = System.currentTimeMillis()
        
        val file = share.openFile(
            remotePath,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
        
        try {
            val buffer = ByteArray(8192)
            var offset = 0L
            
            while (offset < fileSize) {
                val bytesToRead = minOf(buffer.size.toLong(), fileSize - offset).toInt()
                val bytesRead = file.read(buffer, offset, 0, bytesToRead)
                
                if (bytesRead <= 0) break
                
                outputStream.write(buffer, 0, bytesRead)
                bytesTransferred += bytesRead
                offset += bytesRead
                
                val currentTime = System.currentTimeMillis()
                val elapsedTime = currentTime - startTime
                val speed = if (elapsedTime > 0) (bytesTransferred * 1000) / elapsedTime else 0L
                val timeRemaining = if (speed > 0 && fileSize > 0) {
                    ((fileSize - bytesTransferred) * 1000) / speed
                } else 0L
                
                emit(TransferProgress(bytesTransferred, fileSize, speed, timeRemaining))
            }
        } finally {
            file.close()
            outputStream.close()
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun uploadFile(localInputStream: InputStream, remotePath: String, fileSize: Long): Flow<TransferProgress> = flow {
        val share = diskShare ?: throw Exception("Not connected")
        
        var bytesTransferred = 0L
        val startTime = System.currentTimeMillis()
        
        val file = share.openFile(
            remotePath,
            EnumSet.of(AccessMask.GENERIC_WRITE),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_CREATE,
            null
        )
        
        try {
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var offset = 0L
            
            while (localInputStream.read(buffer).also { bytesRead = it } != -1) {
                file.write(buffer, offset, 0, bytesRead)
                bytesTransferred += bytesRead
                offset += bytesRead
                
                val currentTime = System.currentTimeMillis()
                val elapsedTime = currentTime - startTime
                val speed = if (elapsedTime > 0) (bytesTransferred * 1000) / elapsedTime else 0L
                val timeRemaining = if (speed > 0 && fileSize > 0) {
                    ((fileSize - bytesTransferred) * 1000) / speed
                } else 0L
                
                emit(TransferProgress(bytesTransferred, fileSize, speed, timeRemaining))
            }
        } finally {
            file.close()
            localInputStream.close()
        }
    }.flowOn(Dispatchers.IO)
    
    override fun isConnected(): Boolean {
        return diskShare != null && connection?.isConnected == true
    }
}