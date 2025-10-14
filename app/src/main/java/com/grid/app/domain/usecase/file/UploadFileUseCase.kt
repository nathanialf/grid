package com.grid.app.domain.usecase.file

import com.grid.app.domain.model.Connection
import com.grid.app.domain.repository.FileRepository
import javax.inject.Inject

class UploadFileUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(connection: Connection, localPath: String, remotePath: String) {
        fileRepository.uploadFile(connection, localPath, remotePath)
    }
}