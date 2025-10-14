package com.grid.app.domain.usecase

import com.grid.app.domain.model.Connection
import com.grid.app.domain.repository.ConnectionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetConnectionsUseCase @Inject constructor(
    private val connectionRepository: ConnectionRepository
) {
    suspend operator fun invoke(): Flow<List<Connection>> {
        return connectionRepository.getAllConnections()
    }
}