package com.defnf.grid.domain.usecase.file

import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.repository.FileRepository
import javax.inject.Inject

class CreateDirectoryUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(connection: Connection, directoryPath: String) {
        fileRepository.createDirectory(connection, directoryPath)
    }
}