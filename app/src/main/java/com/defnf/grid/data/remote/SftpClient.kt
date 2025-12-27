package com.defnf.grid.data.remote

import android.content.Context
import com.defnf.grid.domain.model.*
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
import org.apache.sshd.common.NamedFactory
import org.apache.sshd.common.signature.Signature
import org.apache.sshd.client.ClientBuilder
import org.apache.sshd.common.util.security.SecurityUtils
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
            // Configure security provider for Android compatibility
            SecurityUtils.setAPrioriDisabledProvider("BC", true) // Disable BouncyCastle provider
            
            val client = SshClient.setUpDefaultClient()
            
            // Set up key exchange factories using the proper transformation
            client.keyExchangeFactories = NamedFactory.setUpTransformedFactories(
                false,
                BuiltinDHFactories.VALUES,
                ClientBuilder.DH2KEX
            )

            // Configure signature factories - prioritize modern RSA-SHA2 algorithms
            // Modern OpenSSH servers reject the old ssh-rsa (SHA-1) algorithm
            val signatureFactories = ArrayList<NamedFactory<Signature>>()
            // Add RSA-SHA2-512 and RSA-SHA2-256 first (required for modern servers)
            signatureFactories.add(BuiltinSignatures.rsaSHA512)
            signatureFactories.add(BuiltinSignatures.rsaSHA256)
            // Add other algorithms
            for (sig in BuiltinSignatures.VALUES) {
                if (sig != BuiltinSignatures.rsaSHA512 && sig != BuiltinSignatures.rsaSHA256) {
                    signatureFactories.add(sig)
                }
            }
            client.signatureFactories = signatureFactories

            // Configure compatibility settings
            client.serverKeyVerifier = org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier.INSTANCE
            
            client.start()
            
            val connectFuture = client.connect(credential.username, connection.hostname, connection.effectivePort)
            val session = connectFuture.verify(10000).session
            
            // Authenticate
            val authenticated = if (!credential.privateKey.isNullOrEmpty()) {
                try {
                    println("SFTP: Starting SSH key authentication for user: ${credential.username}")
                    val keyPair = loadKeyPair(credential.privateKey)
                    if (keyPair != null) {
                        session.addPublicKeyIdentity(keyPair)
                        println("SFTP: SSH key identity added, attempting auth...")
                        val authResult = session.auth()
                        println("SFTP: Auth future created, verifying...")
                        val isSuccess = authResult.verify(15000).isSuccess
                        println("SFTP: SSH key auth result: $isSuccess")
                        isSuccess
                    } else {
                        println("SFTP: Failed to parse SSH key, falling back to password")
                        if (credential.password.isNotEmpty()) {
                            session.addPasswordIdentity(credential.password)
                            val authResult = session.auth()
                            authResult.verify(15000).isSuccess
                        } else {
                            false
                        }
                    }
                } catch (e: Exception) {
                    println("SFTP: SSH key authentication failed: ${e.message}")
                    e.printStackTrace()
                    // Fall back to password if key auth fails
                    if (credential.password.isNotEmpty()) {
                        try {
                            session.addPasswordIdentity(credential.password)
                            val authResult = session.auth()
                            authResult.verify(15000).isSuccess
                        } catch (e2: Exception) {
                            println("SFTP: Password fallback also failed: ${e2.message}")
                            false
                        }
                    } else {
                        false
                    }
                }
            } else if (credential.password.isNotEmpty()) {
                try {
                    println("SFTP: Starting password authentication for user: ${credential.username}")
                    session.addPasswordIdentity(credential.password)
                    println("SFTP: Password identity added, attempting auth...")
                    val authResult = session.auth()
                    println("SFTP: Auth future created, verifying...")
                    val isSuccess = authResult.verify(15000).isSuccess
                    println("SFTP: Auth result: $isSuccess")
                    isSuccess
                } catch (e: Exception) {
                    println("SFTP: Authentication failed with exception: ${e.message}")
                    e.printStackTrace()
                    false
                }
            } else {
                false
            }
            
            if (!authenticated) {
                session.close()
                client.stop()
                return@withContext Result.Error(Exception("Authentication failed"))
            }
            
            // Create SFTP client - let's accept that this will likely fail and provide better error info
            println("SFTP: Creating SFTP client...")
            val sftp = try {
                println("SFTP: Attempting SFTP client creation...")
                val factory = SftpClientFactory.instance()
                factory.createSftpClient(session)
            } catch (e: Exception) {
                println("SFTP: Failed to create SFTP client: ${e.message}")
                e.printStackTrace()
                session.close()
                client.stop()
                
                // Provide helpful error message based on the known issue
                val errorMessage = when {
                    e.message?.contains("Closing while await init message") == true ->
                        "SFTP subsystem initialization failed. This usually means:\n" +
                        "1. The server doesn't have SFTP enabled/configured\n" +
                        "2. The server is incompatible with this SFTP client\n" +
                        "3. Firewall/network issues preventing SFTP traffic\n" +
                        "Please check your server's SSH/SFTP configuration."
                    else -> "Failed to initialize SFTP: ${e.message}"
                }
                
                return@withContext Result.Error(Exception(errorMessage))
            }
            println("SFTP: SFTP client created successfully")
            
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
                // Recursively delete directory contents
                deleteDirectoryRecursive(client, path)
            } else {
                client.remove(path)
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    private fun deleteDirectoryRecursive(client: ApacheSftpClient, dirPath: String) {
        try {
            // List all items in the directory
            val items = client.readDir(dirPath)
            
            for (item in items) {
                // Skip . and .. entries
                if (item.filename == "." || item.filename == "..") {
                    continue
                }
                
                val itemPath = if (dirPath.endsWith("/")) {
                    "$dirPath${item.filename}"
                } else {
                    "$dirPath/${item.filename}"
                }
                
                if (item.attributes.isDirectory) {
                    // Recursively delete subdirectory
                    deleteDirectoryRecursive(client, itemPath)
                } else {
                    // Delete file
                    client.remove(itemPath)
                }
            }
            
            // Finally, delete the empty directory
            client.rmdir(dirPath)
        } catch (e: Exception) {
            println("SFTP: Error during recursive delete: ${e.message}")
            // Try to delete the directory anyway
            try {
                client.rmdir(dirPath)
            } catch (fallbackEx: Exception) {
                throw e // Throw the original exception
            }
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

    private fun loadKeyPair(privateKeyContent: String): java.security.KeyPair? {
        return try {
            val cleanedKey = privateKeyContent.trim()
            println("SFTP: Attempting to parse SSH key (${cleanedKey.length} chars)")
            println("SFTP: Key format: ${cleanedKey.lines().firstOrNull()}")

            // Check if user accidentally provided public key instead of private key
            if (cleanedKey.startsWith("ssh-rsa ") || cleanedKey.startsWith("ssh-ed25519 ") ||
                cleanedKey.startsWith("ecdsa-sha2-") || cleanedKey.startsWith("ssh-dss ")) {
                println("SFTP: ERROR - This appears to be a PUBLIC key, not a private key!")
                println("SFTP: Private keys should start with '-----BEGIN' (e.g., '-----BEGIN OPENSSH PRIVATE KEY-----')")
                return null
            }

            // Try PKCS8 format first (generated by Java KeyPairGenerator)
            if (cleanedKey.contains("-----BEGIN PRIVATE KEY-----")) {
                println("SFTP: Detected PKCS8 format key, parsing manually")
                return parsePkcs8Key(cleanedKey)
            }

            // Try Apache SSHD's key loading utilities for other formats (OpenSSH, PEM RSA)
            val keyPairResourceParser = SecurityUtils.getKeyPairResourceParser()
            val inputStream = java.io.ByteArrayInputStream(cleanedKey.toByteArray(Charsets.UTF_8))

            val keyPairs = keyPairResourceParser.loadKeyPairs(
                null,
                org.apache.sshd.common.NamedResource.ofName("user-provided-key"),
                null,
                inputStream
            )

            if (keyPairs.isNotEmpty()) {
                println("SFTP: Successfully loaded SSH key pair via Apache SSHD")
                keyPairs.first()
            } else {
                println("SFTP: No key pairs found in provided content")
                null
            }
        } catch (e: Exception) {
            println("SFTP: Failed to parse SSH key: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun parsePkcs8Key(pemContent: String): java.security.KeyPair? {
        return try {
            // Extract base64 content between headers
            val base64Content = pemContent
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")

            val keyBytes = android.util.Base64.decode(base64Content, android.util.Base64.DEFAULT)
            val keySpec = java.security.spec.PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = java.security.KeyFactory.getInstance("RSA")
            val privateKey = keyFactory.generatePrivate(keySpec) as java.security.interfaces.RSAPrivateCrtKey

            // Reconstruct public key from private key components
            val publicKeySpec = java.security.spec.RSAPublicKeySpec(
                privateKey.modulus,
                privateKey.publicExponent
            )
            val publicKey = keyFactory.generatePublic(publicKeySpec)

            println("SFTP: Successfully parsed PKCS8 RSA key")
            java.security.KeyPair(publicKey, privateKey)
        } catch (e: Exception) {
            println("SFTP: Failed to parse PKCS8 key: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}