package com.defnf.grid.domain.usecase.file

import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.RemoteFile
import com.defnf.grid.domain.repository.FileRepository
import javax.inject.Inject

class DownloadFileUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(connection: Connection, file: RemoteFile, localPath: String) {
        fileRepository.downloadFile(connection, file, localPath)
    }
}