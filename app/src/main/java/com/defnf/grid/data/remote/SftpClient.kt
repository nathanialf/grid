package com.defnf.grid.data.remote

import com.defnf.grid.data.remote.ssh.SshClientProvider
import com.defnf.grid.data.remote.ssh.SshTuning
import com.defnf.grid.data.remote.telemetry.ConnectPhase
import com.defnf.grid.data.remote.telemetry.ConnectTrace
import com.defnf.grid.domain.model.*
import com.defnf.grid.util.GridLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.kex.BuiltinDHFactories
import org.apache.sshd.common.kex.KexProposalOption
import org.apache.sshd.common.session.SessionListener
import org.apache.sshd.common.util.security.SecurityUtils
import org.apache.sshd.sftp.client.SftpClient as ApacheSftpClient
import org.apache.sshd.sftp.client.SftpClientFactory
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class SftpClient @Inject constructor(
    private val sshClientProvider: SshClientProvider
) : NetworkClient {

    @Volatile
    private var session: ClientSession? = null

    @Volatile
    private var sftpClient: ApacheSftpClient? = null

    override suspend fun connect(connection: Connection, credential: Credential): Result<Unit> =
        withContext(Dispatchers.IO) {
            val trace = ConnectTrace.forSftp()
            var openedSession: ClientSession? = null
            try {
                withTimeout(SshTuning.OVERALL_CONNECT_TIMEOUT_MS) {
                    // Already built and started; the expensive setup happened during warm-up.
                    val client = sshClientProvider.get()

                    val newSession = client
                        .connect(credential.username, connection.hostname, connection.effectivePort)
                        .verify(SshTuning.CONNECT_TIMEOUT.toMillis())
                        .session
                    openedSession = newSession
                    trace.mark(ConnectPhase.TCP)

                    // The key exchange completes during auth(), so listen for it to separate the two.
                    newSession.addSessionListener(object : SessionListener {
                        override fun sessionEvent(
                            session: org.apache.sshd.common.session.Session,
                            event: SessionListener.Event
                        ) {
                            if (event == SessionListener.Event.KeyEstablished) {
                                trace.mark(ConnectPhase.KEX)
                            }
                        }
                    })

                    if (!authenticate(newSession, credential)) {
                        throw IOException("Authentication failed")
                    }
                    trace.mark(ConnectPhase.AUTH)

                    // These are what prove the algorithm ordering actually took effect against this
                    // particular server, rather than us assuming it did.
                    trace.note("kex", newSession.getNegotiatedKexParameter(KexProposalOption.ALGORITHMS))
                    trace.note("hostkey", newSession.getNegotiatedKexParameter(KexProposalOption.SERVERKEYS))
                    trace.note("cipher", newSession.getNegotiatedKexParameter(KexProposalOption.S2CENC))
                    trace.note("mac", newSession.getNegotiatedKexParameter(KexProposalOption.S2CMAC))
                    trace.note("x25519", BuiltinDHFactories.curve25519.isSupported.toString())

                    val sftp = try {
                        SftpClientFactory.instance().createSftpClient(newSession)
                    } catch (e: Exception) {
                        throw IOException(sftpSubsystemMessage(e), e)
                    }
                    trace.mark(ConnectPhase.SUBSYSTEM)

                    this@SftpClient.session = newSession
                    this@SftpClient.sftpClient = sftp
                }

                trace.finish("ok")
                Result.Success(Unit)
            } catch (e: TimeoutCancellationException) {
                // withTimeout cannot interrupt a blocking verify(), so close explicitly or the
                // half-open session and its thread leak.
                runCatching { openedSession?.close(true) }
                trace.finish("timeout")
                Result.Error(IOException("Connection timed out", e))
            } catch (e: Exception) {
                runCatching { openedSession?.close(true) }
                trace.finish("error")
                GridLog.w(GridLog.TAG_SFTP, "Connect failed", e)
                Result.Error(e)
            }
        }

    /** Tries key auth when a key is present, falling back to password on any failure. */
    private fun authenticate(session: ClientSession, credential: Credential): Boolean {
        val authTimeoutMs = SshTuning.AUTH_TIMEOUT.toMillis()

        if (!credential.privateKey.isNullOrEmpty()) {
            val keyPair = loadKeyPair(credential.privateKey)
            if (keyPair != null) {
                val succeeded = try {
                    session.addPublicKeyIdentity(keyPair)
                    session.auth().verify(authTimeoutMs).isSuccess
                } catch (e: Exception) {
                    GridLog.w(GridLog.TAG_SFTP, "Public key authentication failed", e)
                    false
                }
                if (succeeded) return true
            }
        }

        if (credential.password.isEmpty()) return false

        return try {
            session.addPasswordIdentity(credential.password)
            session.auth().verify(authTimeoutMs).isSuccess
        } catch (e: Exception) {
            GridLog.w(GridLog.TAG_SFTP, "Password authentication failed", e)
            false
        }
    }

    private fun sftpSubsystemMessage(e: Exception): String = when {
        e.message?.contains("Closing while await init message") == true ->
            "SFTP subsystem initialization failed. This usually means:\n" +
                "1. The server doesn't have SFTP enabled/configured\n" +
                "2. The server is incompatible with this SFTP client\n" +
                "3. Firewall/network issues preventing SFTP traffic\n" +
                "Please check your server's SSH/SFTP configuration."
        else -> "Failed to initialize SFTP: ${e.message}"
    }

    override suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            sftpClient?.close()
            session?.close()

            sftpClient = null
            session = null

            // Deliberately no sshClient.stop(): the SshClient is shared process-wide, and stopping it
            // would close every other connection's session and block for up to STOP_WAIT_TIME.

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
            GridLog.w(GridLog.TAG_SFTP, "Recursive delete failed, retrying rmdir", e)
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
            val buffer = ByteArray(SshTuning.TRANSFER_BUFFER_BYTES)
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
            val buffer = ByteArray(SshTuning.TRANSFER_BUFFER_BYTES)
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
        val sftp = sftpClient ?: return false
        val current = session
        // Consult the library's own liveness rather than just a non-null field. With heartbeats
        // enabled, SSHD closes the session itself after the configured misses, so a dropped peer or a
        // NAT rebind shows up here without us spending a round trip to find out.
        return sftp.isOpen && current?.isOpen == true && current.isClosing == false
    }

    /**
     * Parses a user-supplied private key. Returns null for anything unparseable — including a public
     * key pasted by mistake, which is a common enough error to be worth naming in the log.
     *
     * Nothing here logs the key's content, its length, or its header line: all three are usable
     * fingerprints of the user's key material, and these logs ship in release builds.
     */
    private fun loadKeyPair(privateKeyContent: String): java.security.KeyPair? {
        return try {
            val cleanedKey = privateKeyContent.trim()

            if (cleanedKey.startsWith("ssh-rsa ") || cleanedKey.startsWith("ssh-ed25519 ") ||
                cleanedKey.startsWith("ecdsa-sha2-") || cleanedKey.startsWith("ssh-dss ")
            ) {
                GridLog.w(
                    GridLog.TAG_SFTP,
                    "Supplied key looks like a public key; a private key is required " +
                        "(it should start with -----BEGIN)"
                )
                return null
            }

            // PKCS8 (what Java's KeyPairGenerator emits) needs a manual path; SSHD's parser handles
            // OpenSSH and PEM RSA.
            if (cleanedKey.contains("-----BEGIN PRIVATE KEY-----")) {
                GridLog.d(GridLog.TAG_SFTP) { "Parsing key: PKCS8" }
                return parsePkcs8Key(cleanedKey)
            }

            GridLog.d(GridLog.TAG_SFTP) { "Parsing key: OPENSSH/PEM" }
            val keyPairResourceParser = SecurityUtils.getKeyPairResourceParser()
            val inputStream = java.io.ByteArrayInputStream(cleanedKey.toByteArray(Charsets.UTF_8))

            val keyPairs = keyPairResourceParser.loadKeyPairs(
                null,
                org.apache.sshd.common.NamedResource.ofName("user-provided-key"),
                null,
                inputStream
            )

            keyPairs.firstOrNull()
        } catch (e: Exception) {
            GridLog.w(GridLog.TAG_SFTP, "Could not parse the supplied private key", e)
            null
        }
    }

    private fun parsePkcs8Key(pemContent: String): java.security.KeyPair? {
        return try {
            val base64Content = pemContent
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")

            val keyBytes = android.util.Base64.decode(base64Content, android.util.Base64.DEFAULT)
            val keySpec = java.security.spec.PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = java.security.KeyFactory.getInstance("RSA")
            val privateKey =
                keyFactory.generatePrivate(keySpec) as java.security.interfaces.RSAPrivateCrtKey

            // Reconstruct the public key from the private key's components.
            val publicKeySpec = java.security.spec.RSAPublicKeySpec(
                privateKey.modulus,
                privateKey.publicExponent
            )
            val publicKey = keyFactory.generatePublic(publicKeySpec)

            java.security.KeyPair(publicKey, privateKey)
        } catch (e: Exception) {
            GridLog.w(GridLog.TAG_SFTP, "Could not parse the supplied PKCS8 key", e)
            null
        }
    }
}
