package com.defnf.grid.domain.usecase.file

import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.FileTransfer
import com.defnf.grid.domain.repository.FileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class UploadFileUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(connection: Connection, localPath: String, remotePath: String) {
        fileRepository.uploadFile(connection, localPath, remotePath)
    }
    
    suspend fun uploadWithProgress(connection: Connection, localPath: String, remotePath: String): Flow<FileTransfer> {
        return fileRepository.uploadFileWithProgress(connection, localPath, remotePath)
    }
}