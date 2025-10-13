package com.grid.app.domain.usecase

import com.grid.app.domain.model.Connection
import com.grid.app.domain.model.Result
import com.grid.app.domain.repository.FileRepository
import javax.inject.Inject

class ConnectToServerUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(connection: Connection): Result<Unit> {
        return fileRepository.connect(connection)
    }
}