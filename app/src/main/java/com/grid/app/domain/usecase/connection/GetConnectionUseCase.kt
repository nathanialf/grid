package com.grid.app.domain.usecase.connection

import com.grid.app.domain.model.Connection
import com.grid.app.domain.repository.ConnectionRepository
import javax.inject.Inject

class GetConnectionUseCase @Inject constructor(
    private val connectionRepository: ConnectionRepository
) {
    suspend operator fun invoke(connectionId: String): Connection {
        return connectionRepository.getConnectionById(connectionId)
    }
}