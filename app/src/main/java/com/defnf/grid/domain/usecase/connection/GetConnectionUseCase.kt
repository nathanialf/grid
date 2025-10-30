package com.defnf.grid.domain.usecase.connection

import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.repository.ConnectionRepository
import javax.inject.Inject

class GetConnectionUseCase @Inject constructor(
    private val connectionRepository: ConnectionRepository
) {
    suspend operator fun invoke(connectionId: String): Connection {
        return connectionRepository.getConnectionById(connectionId)
    }
}