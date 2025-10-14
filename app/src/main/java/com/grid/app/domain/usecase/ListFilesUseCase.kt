package com.grid.app.domain.usecase

import com.grid.app.domain.model.RemoteFile
import com.grid.app.domain.model.Result
import com.grid.app.domain.repository.FileRepository
import javax.inject.Inject

class ListFilesUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(connectionId: String, path: String): Result<List<RemoteFile>> {
        return fileRepository.listFiles(connectionId, path)
    }
}