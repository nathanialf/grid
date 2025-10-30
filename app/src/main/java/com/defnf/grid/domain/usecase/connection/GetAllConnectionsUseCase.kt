package com.defnf.grid.domain.usecase.connection

import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.repository.ConnectionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllConnectionsUseCase @Inject constructor(
    private val connectionRepository: ConnectionRepository
) {
    operator fun invoke(): Flow<List<Connection>> {
        return connectionRepository.getAllConnections()
    }
}