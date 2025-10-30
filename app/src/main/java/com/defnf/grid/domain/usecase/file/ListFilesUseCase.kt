package com.defnf.grid.domain.usecase.file

import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.RemoteFile
import com.defnf.grid.domain.repository.FileRepository
import javax.inject.Inject

class ListFilesUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(connection: Connection, path: String): List<RemoteFile> {
        return fileRepository.listFiles(connection, path)
    }
}