package com.grid.app.domain.usecase.connection

import com.grid.app.domain.model.Result
import com.grid.app.domain.repository.ConnectionRepository
import javax.inject.Inject

class ReorderConnectionsUseCase @Inject constructor(
    private val connectionRepository: ConnectionRepository
) {
    suspend operator fun invoke(connectionIds: List<String>): Result<Unit> {
        return try {
            connectionIds.forEachIndexed { index, connectionId ->
                connectionRepository.updateConnectionOrder(connectionId, index)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}