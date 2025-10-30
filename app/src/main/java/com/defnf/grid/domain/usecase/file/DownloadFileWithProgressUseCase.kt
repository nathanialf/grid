package com.defnf.grid.domain.usecase.file

import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.RemoteFile
import com.defnf.grid.domain.model.FileTransfer
import com.defnf.grid.domain.repository.FileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class DownloadFileWithProgressUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(connection: Connection, file: RemoteFile, localPath: String): Flow<FileTransfer> {
        return fileRepository.downloadFileWithProgress(connection, file, localPath)
    }
}