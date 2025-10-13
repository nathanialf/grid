package com.grid.app.domain.usecase.connection

import com.grid.app.domain.model.Result
import com.grid.app.domain.repository.ConnectionRepository
import javax.inject.Inject

class DeleteConnectionUseCase @Inject constructor(
    private val connectionRepository: ConnectionRepository
) {
    suspend operator fun invoke(connectionId: String): Result<Unit> {
        return connectionRepository.deleteConnection(connectionId)
    }
}