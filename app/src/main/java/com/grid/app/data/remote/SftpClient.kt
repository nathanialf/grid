package com.grid.app.data.remote

import android.content.Context
import com.grid.app.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.kex.BuiltinDHFactories
import org.apache.sshd.common.kex.KeyExchangeFactory
import org.apache.sshd.common.signature.BuiltinSignatures
import org.apache.sshd.common.util.io.PathUtils
import org.apache.sshd.sftp.client.SftpClient as ApacheSftpClient
import org.apache.sshd.sftp.client.SftpClientFactory
import org.apache.sshd.sftp.common.SftpConstants
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.nio.file.Paths
import javax.inject.Inject

class SftpClient @Inject constructor(
    @ApplicationContext private val context: Context
) : NetworkClient {
    
    private var sshClient: SshClient? = null
    private var session: ClientSession? = null
    private var sftpClient: ApacheSftpClient? = null
    
    init {
        // Set up Android-compatible user home folder resolver using app's files directory
        PathUtils.setUserHomeFolderResolver { 
            Paths.get(context.filesDir.absolutePath)
        }
    }
    
    override suspend fun connect(connection: Connection, credential: Credential): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = SshClient.setUpDefaultClient()
            
            // Use default configuration which should work for most SFTP servers
            
            client.start()
            
            val connectFuture = client.connect(credential.username, connection.hostname, connection.effectivePort)
            val session = connectFuture.verify(10000).session
            
            // Authenticate
            val authenticated = if (credential.password.isNotEmpty()) {
                try {
                    session.addPasswordIdentity(credential.password)
                    val authResult = session.auth()
                    authResult.verify(15000).isSuccess
                } catch (e: Exception) {
                    false
                }
            } else if (!credential.privateKey.isNullOrEmpty()) {
                // TODO: Implement SSH key authentication
                false
            } else {
                false
            }
            
            if (!authenticated) {
                session.close()
                client.stop()
                return@withContext Result.Error(Exception("Authentication failed"))
            }
            
            val sftp = SftpClientFactory.instance().createSftpClient(session)
            
            this@SftpClient.sshClient = client
            this@SftpClient.session = session
            this@SftpClient.sftpClient = sftp
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            sftpClient?.close()
            session?.close()
            sshClient?.stop()
            
            sftpClient = null
            session = null
            sshClient = null
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun listFiles(path: String): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.Error(Exception("Not connected"))
            
            val files = client.readDir(path)
            val remoteFiles = files.map { entry ->
                val attrs = entry.attributes
                RemoteFile(
                    name = entry.filename,
                    path = if (path.endsWith("/")) "$path${entry.filename}" else "$path/${entry.filename}",
                    isDirectory = attrs.isDirectory,
                    size = attrs.size,
                    lastModified = attrs.modifyTime?.toMillis() ?: 0L,
                    permissions = attrs.permissions.toString(),
                    isHidden = entry.filename.startsWith(".")
                )
            }.filter { it.name != "." && it.name != ".." }
            
            Result.Success(remoteFiles)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.Error(Exception("Not connected"))
            
            client.mkdir(path)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.Error(Exception("Not connected"))
            
            val attrs = client.stat(path)
            if (attrs.isDirectory) {
                client.rmdir(path)
            } else {
                client.remove(path)
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun renameFile(oldPath: String, newPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.Error(Exception("Not connected"))
            
            client.rename(oldPath, newPath)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun downloadFile(remotePath: String, outputStream: OutputStream): Flow<TransferProgress> = flow {
        val client = sftpClient ?: throw Exception("Not connected")
        
        val attrs = client.stat(remotePath)
        val fileSize = attrs.size
        var bytesTransferred = 0L
        val startTime = System.currentTimeMillis()
        
        val inputStream = client.read(remotePath)
        
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
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun uploadFile(localInputStream: InputStream, remotePath: String, fileSize: Long): Flow<TransferProgress> = flow {
        val client = sftpClient ?: throw Exception("Not connected")
        
        var bytesTransferred = 0L
        val startTime = System.currentTimeMillis()
        
        val outputStream = client.write(remotePath)
        
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
        }
    }.flowOn(Dispatchers.IO)
    
    override fun isConnected(): Boolean {
        return sftpClient != null && session?.isOpen == true
    }
}