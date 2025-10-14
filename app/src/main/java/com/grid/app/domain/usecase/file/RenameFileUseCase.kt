package com.grid.app.domain.usecase.file

import com.grid.app.domain.model.Connection
import com.grid.app.domain.repository.FileRepository
import javax.inject.Inject

class RenameFileUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(connection: Connection, oldPath: String, newName: String) {
        val parentDir = oldPath.substringBeforeLast("/", "")
        val newPath = if (parentDir.isEmpty()) {
            "/$newName"
        } else {
            "$parentDir/$newName"
        }
        fileRepository.renameFile(connection, oldPath, newPath)
    }
}