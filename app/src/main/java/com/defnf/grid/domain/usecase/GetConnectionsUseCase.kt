package com.defnf.grid.domain.usecase

import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.repository.ConnectionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetConnectionsUseCase @Inject constructor(
    private val connectionRepository: ConnectionRepository
) {
    suspend operator fun invoke(): Flow<List<Connection>> {
        return connectionRepository.getAllConnections()
    }
}