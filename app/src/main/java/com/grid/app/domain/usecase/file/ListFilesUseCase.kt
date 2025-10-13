package com.grid.app.domain.usecase.file

import com.grid.app.domain.model.Connection
import com.grid.app.domain.model.RemoteFile
import com.grid.app.domain.repository.FileRepository
import javax.inject.Inject

class ListFilesUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(connection: Connection, path: String): List<RemoteFile> {
        return fileRepository.listFiles(connection, path)
    }
}