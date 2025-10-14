package com.grid.app.data.repository

import com.grid.app.data.remote.NetworkClient
import com.grid.app.data.remote.NetworkClientFactory
import com.grid.app.domain.model.*
import com.grid.app.domain.repository.CredentialRepository
import com.grid.app.domain.repository.FileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepositoryImpl @Inject constructor(
    private val networkClientFactory: NetworkClientFactory,
    private val credentialRepository: CredentialRepository
) : FileRepository {
    
    private val activeClients = ConcurrentHashMap<String, NetworkClient>()
    private val _activeTransfers = MutableStateFlow<List<FileTransfer>>(emptyList())
    
    override suspend fun connect(connection: Connection): Result<Unit> {
        val credential = try {
            credentialRepository.getCredentialById(connection.credentialId)
        } catch (e: Exception) {
            return Result.Error(Exception("Credential not found"))
        }
        
        val client = networkClientFactory.createClient(connection.protocol)
        
        return when (val result = client.connect(connection, credential)) {
            is Result.Success -> {
                activeClients[connection.id] = client
                Result.Success(Unit)
            }
            is Result.Error -> result
            is Result.Loading -> Result.Error(Exception("Unexpected loading state"))
        }
    }
    
    override suspend fun disconnect(connectionId: String): Result<Unit> {
        val client = activeClients.remove(connectionId)
        return if (client != null) {
            client.disconnect()
        } else {
            Result.Success(Unit)
        }
    }
    
    override suspend fun isConnected(connectionId: String): Boolean {
        return activeClients[connectionId]?.isConnected() == true
    }
    
    override suspend fun testConnection(connection: Connection, credential: Credential): Result<Unit> {
        val client = networkClientFactory.createClient(connection.protocol)
        return client.connect(connection, credential)
    }
    
    override suspend fun listFiles(connectionId: String, path: String): Result<List<RemoteFile>> {
        val client = activeClients[connectionId]
            ?: return Result.Error(Exception("Not connected"))
        
        return client.listFiles(path)
    }
    
    override suspend fun listFiles(connection: Connection, path: String): List<RemoteFile> {
        val credential = credentialRepository.getCredentialById(connection.credentialId)
        
        val client = networkClientFactory.createClient(connection.protocol)
        
        when (val connectResult = client.connect(connection, credential)) {
            is Result.Success -> {
                when (val listResult = client.listFiles(path)) {
                    is Result.Success -> return listResult.data
                    is Result.Error -> throw listResult.exception
                    is Result.Loading -> throw Exception("Unexpected loading state")
                }
            }
            is Result.Error -> throw connectResult.exception
            is Result.Loading -> throw Exception("Unexpected loading state")
        }
    }
    
    override suspend fun createDirectory(connectionId: String, path: String): Result<Unit> {
        val client = activeClients[connectionId]
            ?: return Result.Error(Exception("Not connected"))
        
        return client.createDirectory(path)
    }
    
    override suspend fun createDirectory(connection: Connection, directoryPath: String) {
        val credential = credentialRepository.getCredentialById(connection.credentialId)
        
        val client = networkClientFactory.createClient(connection.protocol)
        
        when (val connectResult = client.connect(connection, credential)) {
            is Result.Success -> {
                when (val createResult = client.createDirectory(directoryPath)) {
                    is Result.Success -> return
                    is Result.Error -> throw createResult.exception
                    is Result.Loading -> throw Exception("Unexpected loading state")
                }
            }
            is Result.Error -> throw connectResult.exception
            is Result.Loading -> throw Exception("Unexpected loading state")
        }
    }
    
    override suspend fun deleteFile(connectionId: String, path: String): Result<Unit> {
        val client = activeClients[connectionId]
            ?: return Result.Error(Exception("Not connected"))
        
        return client.deleteFile(path)
    }
    
    override suspend fun deleteFile(connection: Connection, filePath: String) {
        val credential = credentialRepository.getCredentialById(connection.credentialId)
        val client = networkClientFactory.createClient(connection.protocol)
        
        when (val connectResult = client.connect(connection, credential)) {
            is Result.Success -> {
                when (val deleteResult = client.deleteFile(filePath)) {
                    is Result.Success -> {
                        client.disconnect()
                    }
                    is Result.Error -> {
                        client.disconnect()
                        throw deleteResult.exception
                    }
                    is Result.Loading -> {
                        client.disconnect()
                        throw Exception("Unexpected loading state")
                    }
                }
            }
            is Result.Error -> throw connectResult.exception
            is Result.Loading -> throw Exception("Unexpected loading state")
        }
    }
    
    override suspend fun renameFile(connectionId: String, oldPath: String, newPath: String): Result<Unit> {
        val client = activeClients[connectionId]
            ?: return Result.Error(Exception("Not connected"))
        
        return client.renameFile(oldPath, newPath)
    }
    
    override suspend fun renameFile(connection: Connection, oldPath: String, newPath: String) {
        val credential = credentialRepository.getCredentialById(connection.credentialId)
        val client = networkClientFactory.createClient(connection.protocol)
        
        when (val connectResult = client.connect(connection, credential)) {
            is Result.Success -> {
                when (val renameResult = client.renameFile(oldPath, newPath)) {
                    is Result.Success -> {
                        client.disconnect()
                    }
                    is Result.Error -> {
                        client.disconnect()
                        throw renameResult.exception
                    }
                    is Result.Loading -> {
                        client.disconnect()
                        throw Exception("Unexpected loading state")
                    }
                }
            }
            is Result.Error -> throw connectResult.exception
            is Result.Loading -> throw Exception("Unexpected loading state")
        }
    }
    
    override suspend fun downloadFile(
        connectionId: String,
        remotePath: String,
        localPath: String
    ): Flow<FileTransfer> {
        val client = activeClients[connectionId]
            ?: throw Exception("Not connected")
        
        val transferId = java.util.UUID.randomUUID().toString()
        val fileName = remotePath.substringAfterLast('/')
        val localFile = File(localPath)
        
        val transfer = FileTransfer(
            id = transferId,
            localPath = localPath,
            remotePath = remotePath,
            fileName = fileName,
            isUpload = false,
            connectionId = connectionId,
            state = TransferState.IN_PROGRESS,
            startedAt = System.currentTimeMillis()
        )
        
        addTransfer(transfer)
        
        return client.downloadFile(remotePath, FileOutputStream(localFile)).map { progress ->
            val updatedTransfer = transfer.copy(
                progress = progress,
                state = if (progress.bytesTransferred >= progress.totalBytes) {
                    TransferState.COMPLETED
                } else {
                    TransferState.IN_PROGRESS
                }
            )
            updateTransfer(updatedTransfer)
            updatedTransfer
        }
    }
    
    override suspend fun uploadFile(
        connectionId: String,
        localPath: String,
        remotePath: String
    ): Flow<FileTransfer> {
        val client = activeClients[connectionId]
            ?: throw Exception("Not connected")
        
        val transferId = java.util.UUID.randomUUID().toString()
        val localFile = File(localPath)
        val fileName = localFile.name
        
        val transfer = FileTransfer(
            id = transferId,
            localPath = localPath,
            remotePath = remotePath,
            fileName = fileName,
            isUpload = true,
            connectionId = connectionId,
            state = TransferState.IN_PROGRESS,
            startedAt = System.currentTimeMillis()
        )
        
        addTransfer(transfer)
        
        return client.uploadFile(
            FileInputStream(localFile),
            remotePath,
            localFile.length()
        ).map { progress ->
            val updatedTransfer = transfer.copy(
                progress = progress,
                state = if (progress.bytesTransferred >= progress.totalBytes) {
                    TransferState.COMPLETED
                } else {
                    TransferState.IN_PROGRESS
                }
            )
            updateTransfer(updatedTransfer)
            updatedTransfer
        }
    }
    
    override suspend fun downloadFile(connection: Connection, file: RemoteFile, localPath: String) {
        val credential = credentialRepository.getCredentialById(connection.credentialId)
        
        val client = networkClientFactory.createClient(connection.protocol)
        
        when (val connectResult = client.connect(connection, credential)) {
            is Result.Success -> {
                val localFile = File(localPath)
                client.downloadFile(file.path, FileOutputStream(localFile))
                    .collect { /* Collect the flow but don't return it since method signature is Unit */ }
            }
            is Result.Error -> throw connectResult.exception
            is Result.Loading -> throw Exception("Unexpected loading state")
        }
    }
    
    override suspend fun uploadFile(connection: Connection, localPath: String, remotePath: String) {
        val credential = credentialRepository.getCredentialById(connection.credentialId)
        
        val client = networkClientFactory.createClient(connection.protocol)
        
        when (val connectResult = client.connect(connection, credential)) {
            is Result.Success -> {
                val localFile = File(localPath)
                client.uploadFile(FileInputStream(localFile), remotePath, localFile.length())
                    .collect { /* Collect the flow but don't return it since method signature is Unit */ }
            }
            is Result.Error -> throw connectResult.exception
            is Result.Loading -> throw Exception("Unexpected loading state")
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
    
    private fun addTransfer(transfer: FileTransfer) {
        val currentTransfers = _activeTransfers.value.toMutableList()
        currentTransfers.add(transfer)
        _activeTransfers.value = currentTransfers
    }
    
    private fun updateTransfer(transfer: FileTransfer) {
        val currentTransfers = _activeTransfers.value.toMutableList()
        val index = currentTransfers.indexOfFirst { it.id == transfer.id }
        if (index >= 0) {
            currentTransfers[index] = transfer
            _activeTransfers.value = currentTransfers
        }
    }
}