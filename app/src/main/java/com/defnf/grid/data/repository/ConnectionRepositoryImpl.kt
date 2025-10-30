package com.defnf.grid.data.repository

import android.content.Context
import com.defnf.grid.data.local.PreferencesManager
import com.defnf.grid.data.provider.GridDocumentsProvider
import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.Result
import com.defnf.grid.domain.repository.ConnectionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepositoryImpl @Inject constructor(
    private val preferencesManager: PreferencesManager,
    @ApplicationContext private val context: Context
) : ConnectionRepository {
    
    override fun getAllConnections(): Flow<List<Connection>> {
        return preferencesManager.getConnections().map { connections ->
            connections.sortedBy { it.order }
        }
    }
    
    override suspend fun getConnectionById(id: String): Connection {
        return preferencesManager.getConnections().first()
            .find { it.id == id } ?: throw IllegalArgumentException("Connection not found: $id")
    }
    
    override suspend fun insertConnection(connection: Connection) {
        val currentConnections = preferencesManager.getConnections().first().toMutableList()
        
        // Assign the next highest order to put new connection last
        val maxOrder = currentConnections.maxOfOrNull { it.order } ?: -1
        val connectionWithOrder = connection.copy(order = maxOrder + 1)
        
        currentConnections.add(connectionWithOrder)
        preferencesManager.saveConnections(currentConnections)
        GridDocumentsProvider.notifyRootsChanged(context)
    }
    
    override suspend fun updateConnection(connection: Connection) {
        val currentConnections = preferencesManager.getConnections().first().toMutableList()
        val existingIndex = currentConnections.indexOfFirst { it.id == connection.id }
        
        if (existingIndex >= 0) {
            currentConnections[existingIndex] = connection
            preferencesManager.saveConnections(currentConnections)
            GridDocumentsProvider.notifyRootsChanged(context)
        } else {
            throw IllegalArgumentException("Connection not found: ${connection.id}")
        }
    }
    
    
    override suspend fun saveConnection(connection: Connection): Result<Unit> {
        return try {
            val currentConnections = preferencesManager.getConnections().first().toMutableList()
            val existingIndex = currentConnections.indexOfFirst { it.id == connection.id }
            
            if (existingIndex >= 0) {
                currentConnections[existingIndex] = connection
            } else {
                // Assign the next highest order to put new connection last
                val maxOrder = currentConnections.maxOfOrNull { it.order } ?: -1
                val connectionWithOrder = connection.copy(order = maxOrder + 1)
                currentConnections.add(connectionWithOrder)
            }
            
            preferencesManager.saveConnections(currentConnections)
            GridDocumentsProvider.notifyRootsChanged(context)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun deleteConnection(id: String): Result<Unit> {
        return try {
            val currentConnections = preferencesManager.getConnections().first().toMutableList()
            currentConnections.removeAll { it.id == id }
            preferencesManager.saveConnections(currentConnections)
            GridDocumentsProvider.notifyRootsChanged(context)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun updateLastConnected(connectionId: String): Result<Unit> {
        return try {
            val currentConnections = preferencesManager.getConnections().first().toMutableList()
            val connectionIndex = currentConnections.indexOfFirst { it.id == connectionId }
            
            if (connectionIndex >= 0) {
                val updatedConnection = currentConnections[connectionIndex].copy(
                    lastConnectedAt = System.currentTimeMillis()
                )
                currentConnections[connectionIndex] = updatedConnection
                preferencesManager.saveConnections(currentConnections)
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun updateConnectionOrder(connectionId: String, newOrder: Int): Result<Unit> {
        return try {
            val currentConnections = preferencesManager.getConnections().first().toMutableList()
            val connectionIndex = currentConnections.indexOfFirst { it.id == connectionId }
            
            if (connectionIndex >= 0) {
                val updatedConnection = currentConnections[connectionIndex].copy(order = newOrder)
                currentConnections[connectionIndex] = updatedConnection
                preferencesManager.saveConnections(currentConnections)
                GridDocumentsProvider.notifyRootsChanged(context)
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
}