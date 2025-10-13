package com.grid.app.domain.repository

import com.grid.app.domain.model.Connection
import com.grid.app.domain.model.Result
import kotlinx.coroutines.flow.Flow

interface ConnectionRepository {
    fun getAllConnections(): Flow<List<Connection>>
    suspend fun getConnectionById(id: String): Connection
    suspend fun insertConnection(connection: Connection)
    suspend fun updateConnection(connection: Connection)
    suspend fun deleteConnection(id: String): Result<Unit>
    suspend fun saveConnection(connection: Connection): Result<Unit>
    suspend fun updateLastConnected(connectionId: String): Result<Unit>
}