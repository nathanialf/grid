package com.defnf.grid.domain.usecase.connection

import com.defnf.grid.domain.model.Result
import com.defnf.grid.domain.repository.ConnectionRepository
import javax.inject.Inject

class DeleteConnectionUseCase @Inject constructor(
    private val connectionRepository: ConnectionRepository
) {
    suspend operator fun invoke(connectionId: String): Result<Unit> {
        return connectionRepository.deleteConnection(connectionId)
    }
}