package com.defnf.grid.presentation.screens.filebrowser

import android.app.Application
import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.FileTransfer
import com.defnf.grid.domain.model.TransferProgress
import com.defnf.grid.domain.model.TransferState
import com.defnf.grid.domain.repository.FileRepository
import com.defnf.grid.domain.usecase.connection.GetConnectionUseCase
import com.defnf.grid.domain.usecase.file.CreateDirectoryUseCase
import com.defnf.grid.domain.usecase.file.DeleteFileUseCase
import com.defnf.grid.domain.usecase.file.DownloadFileUseCase
import com.defnf.grid.domain.usecase.file.DownloadFileWithProgressUseCase
import com.defnf.grid.domain.usecase.file.ListFilesUseCase
import com.defnf.grid.domain.usecase.file.RenameDirUseCase
import com.defnf.grid.domain.usecase.file.RenameFileUseCase
import com.defnf.grid.domain.usecase.file.UploadFileUseCase
import com.defnf.grid.domain.usecase.settings.GetSettingsUseCase
import com.defnf.grid.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application = mockk<Application>()
    private val contentResolver = mockk<ContentResolver>()
    private val uploadFileUseCase = mockk<UploadFileUseCase>()
    private lateinit var viewModel: FileBrowserViewModel

    @Before
    fun setUp() {
        every { application.contentResolver } returns contentResolver
        // createTempFileFromUri writes to application.cacheDir; a JVM temp dir works fine.
        every { application.cacheDir } returns Files.createTempDirectory("grid-test-cache").toFile()

        // resolveDisplayName -> query returns a cursor yielding a display name.
        val cursor = mockk<Cursor>(relaxed = true)
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor
        every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { cursor.moveToFirst() } returns true
        every { cursor.getString(0) } returns "file.bin"

        // createTempFileFromUri reads a fresh stream per file.
        every { contentResolver.openInputStream(any()) } answers { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }

        viewModel = FileBrowserViewModel(
            application = application,
            getConnectionUseCase = mockk<GetConnectionUseCase>(relaxed = true),
            listFilesUseCase = mockk<ListFilesUseCase>(relaxed = true),
            downloadFileUseCase = mockk<DownloadFileUseCase>(relaxed = true),
            downloadFileWithProgressUseCase = mockk<DownloadFileWithProgressUseCase>(relaxed = true),
            uploadFileUseCase = uploadFileUseCase,
            createDirectoryUseCase = mockk<CreateDirectoryUseCase>(relaxed = true),
            deleteFileUseCase = mockk<DeleteFileUseCase>(relaxed = true),
            renameFileUseCase = mockk<RenameFileUseCase>(relaxed = true),
            renameDirUseCase = mockk<RenameDirUseCase>(relaxed = true),
            getSettingsUseCase = mockk<GetSettingsUseCase>(relaxed = true),
            fileRepository = mockk<FileRepository>(relaxed = true),
            savedStateHandle = SavedStateHandle()
        )
        // uploadFiles requires a live connection; inject one directly (value is only passed
        // through to the mocked use case).
        injectConnection(mockk<Connection>())
    }

    @Test
    fun `batch of two files both succeed reports uploaded count`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { uploadFileUseCase.uploadWithProgress(any(), any(), any()) } returns flowOf(
            transfer(TransferState.IN_PROGRESS, 50, 100),
            transfer(TransferState.COMPLETED, 100, 100)
        )

        viewModel.uploadFiles(listOf(mockk<Uri>(), mockk<Uri>()))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isUploading)
        assertEquals(0, state.uploadTotalCount)
        assertTrue(state.message!!.contains("Uploaded 2 files"))
        coVerify(exactly = 2) { uploadFileUseCase.uploadWithProgress(any(), any(), any()) }
    }

    @Test
    fun `failed file does not stop the batch`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { uploadFileUseCase.uploadWithProgress(any(), any(), any()) } returnsMany listOf(
            flowOf(transfer(TransferState.FAILED, 0, 100)),
            flowOf(transfer(TransferState.COMPLETED, 100, 100))
        )

        viewModel.uploadFiles(listOf(mockk<Uri>(), mockk<Uri>()))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isUploading)
        assertTrue(state.message!!.contains("Uploaded 1 files"))
        assertTrue(state.message!!.contains("failed to upload 1 files"))
        coVerify(exactly = 2) { uploadFileUseCase.uploadWithProgress(any(), any(), any()) }
    }

    @Test
    fun `cancel mid-batch aborts remaining files`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { uploadFileUseCase.uploadWithProgress(any(), any(), any()) } returns flow {
            emit(transfer(TransferState.IN_PROGRESS, 10, 100))
            delay(10_000)
            emit(transfer(TransferState.COMPLETED, 100, 100))
        }

        viewModel.uploadFiles(listOf(mockk<Uri>(), mockk<Uri>()))
        // Run until the first upload suspends inside its progress flow, then cancel.
        runCurrent()
        viewModel.cancelUpload()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isUploading)
        assertEquals("Upload cancelled", state.message)
        // Second file must never start.
        coVerify(exactly = 1) { uploadFileUseCase.uploadWithProgress(any(), any(), any()) }
    }

    private fun transfer(state: TransferState, bytes: Long, total: Long): FileTransfer =
        FileTransfer(
            id = "transfer",
            localPath = "local",
            remotePath = "remote",
            fileName = "file.bin",
            isUpload = true,
            connectionId = "connection",
            state = state,
            progress = TransferProgress(bytes, total)
        )

    private fun injectConnection(connection: Connection) {
        val field = FileBrowserViewModel::class.java.getDeclaredField("currentConnection")
        field.isAccessible = true
        field.set(viewModel, connection)
    }
}
