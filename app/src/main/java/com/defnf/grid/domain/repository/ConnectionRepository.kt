package com.defnf.grid.domain.repository

import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.Result
import kotlinx.coroutines.flow.Flow

interface ConnectionRepository {
    fun getAllConnections(): Flow<List<Connection>>
    suspend fun getConnectionById(id: String): Connection
    suspend fun insertConnection(connection: Connection)
    suspend fun updateConnection(connection: Connection)
    suspend fun deleteConnection(id: String): Result<Unit>
    suspend fun saveConnection(connection: Connection): Result<Unit>
    suspend fun updateLastConnected(connectionId: String): Result<Unit>
    suspend fun updateConnectionOrder(connectionId: String, newOrder: Int): Result<Unit>
}