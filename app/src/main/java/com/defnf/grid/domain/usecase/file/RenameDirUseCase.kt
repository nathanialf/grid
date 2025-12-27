package com.defnf.grid.domain.usecase.file

import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.repository.FileRepository
import javax.inject.Inject

class RenameDirUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(connection: Connection, oldPath: String, newName: String) {
        // Use appropriate path separator based on protocol
        val separator = if (connection.protocol.name == "SMB") "\\" else "/"

        // Handle directory-specific path construction
        val parentDir = oldPath.substringBeforeLast(separator)
        val newPath = if (parentDir.isEmpty() || parentDir == oldPath) {
            // Root level directory - just use the new name
            newName
        } else {
            // Subdirectory - preserve the full parent path with protocol-appropriate separator
            "$parentDir$separator$newName"
        }

        println("Grid: RenameDirUseCase - Renaming directory from '$oldPath' to '$newPath'")
        println("Grid: RenameDirUseCase - Parent directory: '$parentDir'")

        // Use the same repository method but with directory-specific logging
        fileRepository.renameFile(connection, oldPath, newPath)
    }
}