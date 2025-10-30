package com.defnf.grid.domain.usecase

import com.defnf.grid.domain.model.RemoteFile
import com.defnf.grid.domain.model.Result
import com.defnf.grid.domain.repository.FileRepository
import javax.inject.Inject

class ListFilesUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(connectionId: String, path: String): Result<List<RemoteFile>> {
        return fileRepository.listFiles(connectionId, path)
    }
}