package com.defnf.grid.data.repository

import android.app.Application
import com.defnf.grid.data.remote.pool.ConnectionPool
import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.Credential
import com.defnf.grid.domain.model.FileTransfer
import com.defnf.grid.domain.model.RemoteFile
import com.defnf.grid.domain.model.Result
import com.defnf.grid.domain.model.TransferProgress
import com.defnf.grid.domain.model.TransferState
import com.defnf.grid.domain.repository.FileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Unwraps a [Result], turning the error case into the exception these APIs are declared to throw. */
private fun <T> Result<T>.orThrow(): T = when (this) {
    is Result.Success -> data
    is Result.Error -> throw exception
    is Result.Loading -> throw IllegalStateException("Unexpected loading state")
}

@Singleton
class FileRepositoryImpl @Inject constructor(
    private val application: Application,
    private val pool: ConnectionPool
) : FileRepository {

    private val _activeTransfers = MutableStateFlow<List<FileTransfer>>(emptyList())

    override suspend fun testConnection(
        connection: Connection,
        credential: Credential
    ): Result<Unit> = pool.probe(connection, credential)

    override suspend fun listFiles(connection: Connection, path: String): List<RemoteFile> =
        pool.withClient(connection) { client -> client.listFiles(path).orThrow() }

    override suspend fun getFiles(connection: Connection, path: String): List<RemoteFile> =
        listFiles(connection, path)

    // Everything below that changes remote state passes retryOnStale = false. A request that died in
    // flight may already have been applied on the server, so a silent retry could create a directory
    // twice, delete something the user did not mean to, or truncate a partially written upload.

    override suspend fun createDirectory(connection: Connection, directoryPath: String) {
        pool.withClient(connection, retryOnStale = false) { client ->
            client.createDirectory(directoryPath).orThrow()
        }
    }

    override suspend fun deleteFile(connection: Connection, filePath: String) {
        pool.withClient(connection, retryOnStale = false) { client ->
            client.deleteFile(filePath).orThrow()
        }
    }

    override suspend fun renameFile(connection: Connection, oldPath: String, newPath: String) {
        pool.withClient(connection, retryOnStale = false) { client ->
            client.renameFile(oldPath, newPath).orThrow()
        }
    }

    override suspend fun downloadFile(
        connection: Connection,
        file: RemoteFile,
        localPath: String
    ) {
        // Downloads don't change remote state and reopen (truncate) the output on each attempt,
        // so a pooled session that went stale (e.g. after a Wi-Fi/cellular switch) can be retried.
        pool.withClient(connection, retryOnStale = true) { client ->
            val localFile = File(localPath)
            localFile.parentFile?.mkdirs()
            FileOutputStream(localFile).use { outputStream ->
                client.downloadFile(file.path, outputStream).collect { }
            }
        }
    }

    override suspend fun downloadFileWithProgress(
        connection: Connection,
        file: RemoteFile,
        localPath: String
    ): Flow<FileTransfer> = pool.clientFlow(connection) { client ->
        val localFile = File(localPath)
        localFile.parentFile?.mkdirs()

        val transfer = FileTransfer(
            id = java.util.UUID.randomUUID().toString(),
            localPath = localPath,
            remotePath = file.path,
            fileName = file.name,
            isUpload = false,
            connectionId = connection.id,
            state = TransferState.IN_PROGRESS,
            progress = TransferProgress(0, file.size)
        )
        emit(transfer)

        FileOutputStream(localFile).use { outputStream ->
            client.downloadFile(file.path, outputStream).collect { progress ->
                emit(transfer.copy(progress = progress, state = progress.toState()))
            }
        }
    }

    override suspend fun downloadFileToTemp(connection: Connection, file: RemoteFile): File =
        pool.withClient(connection, retryOnStale = true) { client ->
            val tempDir = File(
                System.getProperty("java.io.tmpdir"),
                "grid_documents/${connection.id}"
            )
            if (!tempDir.exists()) tempDir.mkdirs()

            val encodedPath = file.path.replace('/', '|').replace('\\', '|')
            val tempFile = File(tempDir, "${System.currentTimeMillis()}_$encodedPath")

            try {
                FileOutputStream(tempFile).use { outputStream ->
                    client.downloadFile(file.path, outputStream).collect { }
                }
            } catch (e: Throwable) {
                // Each attempt (including a stale-session retry) writes a new timestamped file;
                // don't leave the partial one behind.
                tempFile.delete()
                throw e
            }
            tempFile
        }

    override suspend fun uploadFile(
        connection: Connection,
        localPath: String,
        remotePath: String
    ) {
        pool.withClient(connection, retryOnStale = false) { client ->
            val localFile = File(localPath)
            FileInputStream(localFile).use { inputStream ->
                client.uploadFile(inputStream, remotePath, localFile.length()).collect { }
            }
        }
    }

    override suspend fun uploadFileWithProgress(
        connection: Connection,
        localPath: String,
        remotePath: String
    ): Flow<FileTransfer> = pool.clientFlow(connection) { client ->
        val localFile = File(localPath)

        val transfer = FileTransfer(
            id = java.util.UUID.randomUUID().toString(),
            localPath = localPath,
            remotePath = remotePath,
            fileName = localFile.name,
            isUpload = true,
            connectionId = connection.id,
            state = TransferState.IN_PROGRESS,
            progress = TransferProgress(0, localFile.length())
        )
        emit(transfer)

        FileInputStream(localFile).use { inputStream ->
            client.uploadFile(inputStream, remotePath, localFile.length()).collect { progress ->
                emit(transfer.copy(progress = progress, state = progress.toState()))
            }
        }
    }

    override fun getActiveTransfers(): Flow<List<FileTransfer>> {
        return _activeTransfers.asStateFlow()
    }
    
    override suspend fun cancelTransfer(transferId: String): Result<Unit> {
        return try {
            val currentTransfers = _activeTransfers.value.toMutableList()
            val transferIndex = currentTransfers.indexOfFirst { it.id == transferId }
            
            if (transferIndex >= 0) {
                currentTransfers[transferIndex] = currentTransfers[transferIndex].copy(
                    state = TransferState.CANCELLED
                )
                _activeTransfers.value = currentTransfers
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun pauseTransfer(transferId: String): Result<Unit> {
        return try {
            val currentTransfers = _activeTransfers.value.toMutableList()
            val transferIndex = currentTransfers.indexOfFirst { it.id == transferId }
            
            if (transferIndex >= 0) {
                currentTransfers[transferIndex] = currentTransfers[transferIndex].copy(
                    state = TransferState.PAUSED
                )
                _activeTransfers.value = currentTransfers
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun resumeTransfer(transferId: String): Result<Unit> {
        return try {
            val currentTransfers = _activeTransfers.value.toMutableList()
            val transferIndex = currentTransfers.indexOfFirst { it.id == transferId }
            
            if (transferIndex >= 0) {
                currentTransfers[transferIndex] = currentTransfers[transferIndex].copy(
                    state = TransferState.IN_PROGRESS
                )
                _activeTransfers.value = currentTransfers
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    private fun TransferProgress.toState(): TransferState =
        if (bytesTransferred >= totalBytes) TransferState.COMPLETED else TransferState.IN_PROGRESS
}
