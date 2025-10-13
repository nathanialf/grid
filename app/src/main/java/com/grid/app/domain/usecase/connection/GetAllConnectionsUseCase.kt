package com.grid.app.domain.usecase.connection

import com.grid.app.domain.model.Connection
import com.grid.app.domain.repository.ConnectionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllConnectionsUseCase @Inject constructor(
    private val connectionRepository: ConnectionRepository
) {
    operator fun invoke(): Flow<List<Connection>> {
        return connectionRepository.getAllConnections()
    }
}