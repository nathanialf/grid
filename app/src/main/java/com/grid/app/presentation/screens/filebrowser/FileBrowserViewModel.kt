package com.grid.app.presentation.screens.filebrowser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.app.domain.model.Connection
import com.grid.app.domain.model.RemoteFile
import com.grid.app.domain.usecase.connection.GetConnectionUseCase
import com.grid.app.domain.usecase.file.CreateDirectoryUseCase
import com.grid.app.domain.usecase.file.DownloadFileUseCase
import com.grid.app.domain.usecase.file.ListFilesUseCase
import com.grid.app.domain.usecase.file.UploadFileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val getConnectionUseCase: GetConnectionUseCase,
    private val listFilesUseCase: ListFilesUseCase,
    private val downloadFileUseCase: DownloadFileUseCase,
    private val uploadFileUseCase: UploadFileUseCase,
    private val createDirectoryUseCase: CreateDirectoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileBrowserUiState())
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    private var currentConnection: Connection? = null

    fun initialize(connectionId: String, initialPath: String = "/") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val connection = getConnectionUseCase(connectionId)
                currentConnection = connection

                _uiState.value = _uiState.value.copy(
                    connectionId = connectionId,
                    connectionName = connection.name,
                    currentPath = initialPath,
                    isLoading = false
                )

                loadFiles(initialPath)
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = exception.message ?: "Failed to load connection"
                )
            }
        }
    }

    fun navigateToDirectory(path: String) {
        _uiState.value = _uiState.value.copy(currentPath = path)
        loadFiles(path)
    }

    fun refresh() {
        loadFiles(_uiState.value.currentPath)
    }

    private fun loadFiles(path: String) {
        val connection = currentConnection ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val files = listFilesUseCase(connection, path)
                _uiState.value = _uiState.value.copy(
                    files = files,
                    isLoading = false,
                    error = null
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    files = emptyList(),
                    isLoading = false,
                    error = exception.message ?: "Failed to load files"
                )
            }
        }
    }

    fun downloadFile(file: RemoteFile, localPath: String) {
        val connection = currentConnection ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                downloadingFiles = _uiState.value.downloadingFiles + file.path
            )

            try {
                downloadFileUseCase(connection, file, localPath)
                _uiState.value = _uiState.value.copy(
                    downloadingFiles = _uiState.value.downloadingFiles - file.path,
                    message = "Downloaded ${file.name} successfully"
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    downloadingFiles = _uiState.value.downloadingFiles - file.path,
                    error = "Failed to download ${file.name}: ${exception.message}"
                )
            }
        }
    }

    fun uploadFile(localPath: String, remotePath: String) {
        val connection = currentConnection ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)

            try {
                uploadFileUseCase(connection, localPath, remotePath)
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    message = "Upload completed successfully"
                )
                refresh()
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    error = "Upload failed: ${exception.message}"
                )
            }
        }
    }

    fun createDirectory(directoryName: String) {
        val connection = currentConnection ?: return
        val currentPath = _uiState.value.currentPath

        viewModelScope.launch {
            try {
                val fullPath = if (currentPath.endsWith("/")) {
                    "$currentPath$directoryName"
                } else {
                    "$currentPath/$directoryName"
                }

                createDirectoryUseCase(connection, fullPath)
                _uiState.value = _uiState.value.copy(
                    message = "Directory '$directoryName' created successfully"
                )
                refresh()
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to create directory: ${exception.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

data class FileBrowserUiState(
    val connectionId: String = "",
    val connectionName: String = "",
    val currentPath: String = "/",
    val files: List<RemoteFile> = emptyList(),
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val downloadingFiles: Set<String> = emptySet(),
    val error: String? = null,
    val message: String? = null
)