package com.grid.app.domain.usecase.connection

import com.grid.app.domain.model.Connection
import com.grid.app.domain.repository.ConnectionRepository
import javax.inject.Inject

class UpdateConnectionUseCase @Inject constructor(
    private val connectionRepository: ConnectionRepository
) {
    suspend operator fun invoke(connection: Connection) {
        connectionRepository.updateConnection(connection)
    }
}