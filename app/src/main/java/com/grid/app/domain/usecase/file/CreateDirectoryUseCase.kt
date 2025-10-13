package com.grid.app.domain.usecase.file

import com.grid.app.domain.model.Connection
import com.grid.app.domain.repository.FileRepository
import javax.inject.Inject

class CreateDirectoryUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(connection: Connection, directoryPath: String) {
        fileRepository.createDirectory(connection, directoryPath)
    }
}