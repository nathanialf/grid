package com.defnf.grid.domain.usecase.file

import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.FileTransfer
import com.defnf.grid.domain.repository.FileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Test

class UploadFileUseCaseTest {

    @Test
    fun `uploadWithProgress delegates to repository`() = runTest {
        val repository = mockk<FileRepository>()
        val connection = mockk<Connection>()
        val expected = flowOf(mockk<FileTransfer>())
        coEvery { repository.uploadFileWithProgress(connection, "local", "remote") } returns expected

        val useCase = UploadFileUseCase(repository)
        val result = useCase.uploadWithProgress(connection, "local", "remote")

        assertSame(expected, result)
        coVerify(exactly = 1) { repository.uploadFileWithProgress(connection, "local", "remote") }
    }

    @Test
    fun `invoke delegates to repository uploadFile`() = runTest {
        val repository = mockk<FileRepository>(relaxed = true)
        val connection = mockk<Connection>()

        val useCase = UploadFileUseCase(repository)
        useCase(connection, "local", "remote")

        coVerify(exactly = 1) { repository.uploadFile(connection, "local", "remote") }
    }
}
