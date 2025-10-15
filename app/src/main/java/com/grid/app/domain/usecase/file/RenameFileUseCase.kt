package com.grid.app.domain.usecase.file

import com.grid.app.domain.model.Connection
import com.grid.app.domain.repository.FileRepository
import javax.inject.Inject

class RenameFileUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(connection: Connection, oldPath: String, newName: String) {
        // Use appropriate path separator based on protocol
        val separator = if (connection.protocol.name == "SMB") "\\" else "/"
        
        val parentDir = oldPath.substringBeforeLast(separator)
        val newPath = if (parentDir.isEmpty() || parentDir == oldPath) {
            // Root level file - just use the new name
            newName
        } else {
            // File in subdirectory - preserve the full parent path with protocol-appropriate separator
            "$parentDir$separator$newName"
        }
        fileRepository.renameFile(connection, oldPath, newPath)
    }
}