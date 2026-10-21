package com.defnf.grid.data.remote

import com.defnf.grid.data.remote.smb.SmbClientProvider
import com.defnf.grid.data.remote.telemetry.ConnectPhase
import com.defnf.grid.data.remote.telemetry.ConnectTrace
import com.defnf.grid.domain.model.*
import com.defnf.grid.di.ApplicationScope
import com.defnf.grid.util.GridLog
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection as SmbConnection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import javax.inject.Inject

class SmbClient @Inject constructor(
    private val clientProvider: SmbClientProvider,
    @ApplicationScope private val appScope: CoroutineScope
) : NetworkClient {

    @Volatile
    private var connection: SmbConnection? = null

    @Volatile
    private var session: Session? = null

    @Volatile
    private var diskShare: DiskShare? = null

    override suspend fun connect(connection: Connection, credential: Credential): Result<Unit> =
        withContext(Dispatchers.IO) {
            val trace = ConnectTrace.forSmb()
            var openedConnection: SmbConnection? = null
            try {
                withTimeout(CONNECT_TIMEOUT_MS) {
                    // TCP plus the SMB2 negotiate that rides on it.
                    val conn = clientProvider.client
                        .connect(connection.hostname, connection.effectivePort)
                    openedConnection = conn
                    trace.mark(ConnectPhase.TCP)

                    val newSession = conn.authenticate(authContextFor(credential))
                    trace.mark(ConnectPhase.AUTH)

                    val share = connectShare(newSession, connection.shareName)
                    trace.mark(ConnectPhase.SHARE)

                    trace.note("dialect", conn.negotiatedProtocol.dialect.name)
                    trace.note("maxread", conn.negotiatedProtocol.maxReadSize.toString())

                    this@SmbClient.connection = conn
                    this@SmbClient.session = newSession
                    this@SmbClient.diskShare = share
                }

                trace.finish("ok")
                Result.Success(Unit)
            } catch (e: TimeoutCancellationException) {
                // close(), never close(true): SMBClient.connect returns a reference-counted entry
                // from its connectionTable keyed on host:port, so another pooled session may be
                // using this very transport. close(true) would disconnect it regardless of the
                // refcount and kill their in-flight transfer.
                runCatching { openedConnection?.close() }
                trace.finish("timeout")
                Result.Error(IOException("Connection timed out", e))
            } catch (e: Exception) {
                runCatching { openedConnection?.close() }
                trace.finish("error")
                Result.Error(e)
            }
        }

    /**
     * Builds the NTLM authentication context.
     *
     * The domain is not just a label: NTLMv2 derives its response as
     * `HMAC_MD5(NTOWFv1(password), UPPERCASE(user) || domain)`, so a wrong domain produces a wrong
     * hash and `STATUS_LOGON_FAILURE`. The previous code always passed the *hostname*, which only
     * happened to work when the hostname matched the server's NetBIOS name.
     *
     * So: honour an explicit `DOMAIN\user` or `user@domain`, and otherwise send no domain, which is
     * what a standalone Samba or Windows box expects for a local account.
     */
    private fun authContextFor(credential: Credential): AuthenticationContext {
        val raw = credential.username
        val domain: String?
        val user: String
        when {
            raw.contains('\\') -> {
                domain = raw.substringBefore('\\')
                user = raw.substringAfter('\\')
            }
            raw.contains('@') -> {
                domain = raw.substringAfter('@')
                user = raw.substringBefore('@')
            }
            else -> {
                domain = null
                user = raw
            }
        }
        return AuthenticationContext(user, credential.password.toCharArray(), domain)
    }

    private suspend fun connectShare(session: Session, shareName: String?): DiskShare {
        if (!shareName.isNullOrEmpty()) {
            val share = try {
                session.connectShare(shareName)
            } catch (e: Exception) {
                throw IOException("Failed to connect to share '$shareName': ${e.message}", e)
            }
            return share as? DiskShare
                ?: throw IOException("Share '$shareName' is not a disk share")
        }
        return discoverShare(session)
    }

    /**
     * Last-resort share discovery for connections saved before a share name was required.
     *
     * smbj 0.14.0 ships no MS-SRVS/DCERPC support, so the shares cannot actually be enumerated — the
     * only option is to guess. The guesses run concurrently over the one authenticated session, so
     * this costs roughly a single round trip rather than the nine sequential tree-connects the
     * previous implementation paid. User shares are tried ahead of the admin shares, which need
     * administrator rights and tend to be alerted on when they fail.
     */
    private suspend fun discoverShare(session: Session): DiskShare {
        val winner = CompletableDeferred<DiskShare>()

        // Launched on the application scope rather than a child scope on purpose. connectShare is a
        // blocking call with no suspension point, so a loser cannot be cancelled — and structured
        // concurrency would make this function wait for every one of them, letting a single stalled
        // tree-connect blow straight through the timeout below. Losers instead finish in the
        // background and close whatever they opened.
        CANDIDATE_SHARES.forEach { name ->
            appScope.launch(Dispatchers.IO) {
                val share = runCatching { session.connectShare(name) }.getOrNull()
                if (share is DiskShare && winner.complete(share)) return@launch
                runCatching { share?.close() }
            }
        }

        return try {
            withTimeout(SHARE_PROBE_TIMEOUT_MS) { winner.await() }
        } catch (e: TimeoutCancellationException) {
            throw IOException(
                "No accessible disk shares found. Please set a share name in the connection " +
                    "settings — common names are Users, Public, shared, C$.",
                e
            )
        }
    }

    override suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            diskShare?.close()
            session?.close()
            connection?.close()

            diskShare = null
            session = null
            connection = null

            // Deliberately no client.close(): the SMBClient is shared process-wide.

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
                    lastModified = fileInfo.lastWriteTime?.toEpochMillis() ?: 0L,
                    isHidden = fileInfo.fileAttributes and FileAttributes.FILE_ATTRIBUTE_HIDDEN.value != 0L
                )
            }.filter { it.name != "." && it.name != ".." }
            
            Result.Success(remoteFiles)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun stat(path: String): Result<RemoteFile> = withContext(Dispatchers.IO) {
        try {
            val share = diskShare ?: return@withContext Result.Error(Exception("Not connected"))

            val info = share.getFileInformation(path)
            Result.Success(
                RemoteFile(
                    name = path.trimEnd('\\').substringAfterLast('\\'),
                    path = path,
                    isDirectory = info.standardInformation.isDirectory,
                    size = info.standardInformation.endOfFile,
                    lastModified = info.basicInformation.lastWriteTime?.toEpochMillis() ?: 0L,
                    isHidden = info.basicInformation.fileAttributes and FileAttributes.FILE_ATTRIBUTE_HIDDEN.value != 0L
                )
            )
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
        // smbj's recursive rmdir walks the tree using the attributes from each listing, so it
        // skips the per-entry getFileInformation round trips (and uses SMB '\\' separators).
        share.rmdir(path, true)
    }
    
    override suspend fun renameFile(oldPath: String, newPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val share = diskShare ?: return@withContext Result.Error(Exception("Not connected"))
            
            // Check if it's a directory first
            val fileInfo = share.getFileInformation(oldPath)
            val isDirectory = fileInfo.standardInformation.isDirectory
            
            GridLog.d(GridLog.TAG_SMB) { "Renaming ${if (isDirectory) "directory" else "file"}" }
            
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
            GridLog.w(GridLog.TAG_SMB, "Rename failed", e)
            Result.Error(e)
        }
    }
    
    override suspend fun openInputStream(remotePath: String, offset: Long): InputStream =
        withContext(Dispatchers.IO) {
            val share = diskShare ?: throw IOException("Not connected")
            val file = share.openFile(
                remotePath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            )
            // smbj's stream reads one request ahead; skipping before the first read only moves
            // its starting offset. Its close() doesn't close the file handle, so do that here.
            val stream = try {
                file.inputStream.also { if (offset > 0) it.skip(offset) }
            } catch (e: Exception) {
                // Don't leak the server-side handle when the stream can't be set up
                runCatching { file.close() }
                throw e
            }
            object : FilterInputStream(stream) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        file.close()
                    }
                }
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
            val buffer = ByteArray(transferBufferSize())
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
            // Overwrite like SFTP does; FILE_CREATE failed whenever the file already existed,
            // which broke saving an edited file back and retrying a failed upload.
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            null
        )
        
        try {
            val buffer = ByteArray(transferBufferSize())
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
    
    /**
     * Sizes the transfer buffer from what the connection actually negotiated, rather than a fixed
     * 8 KiB. Each read is a synchronous SMB2 request/response pair, so an undersized buffer turns one
     * round trip into many.
     */
    private fun transferBufferSize(): Int {
        val negotiated = connection?.negotiatedProtocol?.maxReadSize ?: DEFAULT_TRANSFER_BYTES
        return negotiated.coerceIn(MIN_TRANSFER_BYTES, MAX_TRANSFER_BYTES)
    }

    override fun isConnected(): Boolean {
        return diskShare != null && connection?.isConnected == true
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val SHARE_PROBE_TIMEOUT_MS = 5_000L

        const val MIN_TRANSFER_BYTES = 64 * 1024
        const val DEFAULT_TRANSFER_BYTES = 64 * 1024
        const val MAX_TRANSFER_BYTES = 1024 * 1024

        /** User-visible shares first; admin shares (C$ and friends) last. */
        val CANDIDATE_SHARES = listOf(
            "Users", "Public", "shared", "share", "data", "home", "C$", "D$", "E$"
        )
    }
}
