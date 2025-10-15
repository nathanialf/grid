package com.grid.app.domain.usecase.file

import com.grid.app.domain.model.Connection
import com.grid.app.domain.model.RemoteFile
import com.grid.app.domain.model.FileTransfer
import com.grid.app.domain.repository.FileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class DownloadFileWithProgressUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(connection: Connection, file: RemoteFile, localPath: String): Flow<FileTransfer> {
        return fileRepository.downloadFileWithProgress(connection, file, localPath)
    }
}