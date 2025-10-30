package com.defnf.grid.domain.usecase

import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.Result
import com.defnf.grid.domain.repository.FileRepository
import javax.inject.Inject

class ConnectToServerUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(connection: Connection): Result<Unit> {
        return fileRepository.connect(connection)
    }
}