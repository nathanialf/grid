package com.grid.app.domain.usecase.file

import com.grid.app.domain.model.Connection
import com.grid.app.domain.model.RemoteFile
import com.grid.app.domain.repository.FileRepository
import javax.inject.Inject

class DownloadFileUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(connection: Connection, file: RemoteFile, localPath: String) {
        fileRepository.downloadFile(connection, file, localPath)
    }
}