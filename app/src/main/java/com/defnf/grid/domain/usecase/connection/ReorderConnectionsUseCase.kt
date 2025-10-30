package com.defnf.grid.domain.usecase.connection

import com.defnf.grid.domain.model.Result
import com.defnf.grid.domain.repository.ConnectionRepository
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