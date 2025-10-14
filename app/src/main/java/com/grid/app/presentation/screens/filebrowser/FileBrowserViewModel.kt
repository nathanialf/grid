package com.grid.app.presentation.screens.filebrowser

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.FileOutputStream
import com.grid.app.domain.model.Connection
import com.grid.app.domain.model.RemoteFile
import com.grid.app.domain.usecase.connection.GetConnectionUseCase
import com.grid.app.domain.usecase.file.CreateDirectoryUseCase
import com.grid.app.domain.usecase.file.DeleteFileUseCase
import com.grid.app.domain.usecase.file.DownloadFileUseCase
import com.grid.app.domain.usecase.file.ListFilesUseCase
import com.grid.app.domain.usecase.file.RenameFileUseCase
import com.grid.app.domain.usecase.file.UploadFileUseCase
import com.grid.app.domain.usecase.settings.GetSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val application: Application,
    private val getConnectionUseCase: GetConnectionUseCase,
    private val listFilesUseCase: ListFilesUseCase,
    private val downloadFileUseCase: DownloadFileUseCase,
    private val uploadFileUseCase: UploadFileUseCase,
    private val createDirectoryUseCase: CreateDirectoryUseCase,
    private val deleteFileUseCase: DeleteFileUseCase,
    private val renameFileUseCase: RenameFileUseCase,
    private val getSettingsUseCase: GetSettingsUseCase
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

                // Load settings to get view mode
                val settings = getSettingsUseCase().first()
                _uiState.value = _uiState.value.copy(
                    connectionId = connectionId,
                    connectionName = connection.name,
                    currentPath = initialPath,
                    viewMode = settings.defaultViewMode.name.lowercase(),
                    showHiddenFiles = settings.showHiddenFiles,
                    protocol = connection.protocol.name,
                    shareName = connection.shareName,
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
                val sortedFiles = sortFiles(files, _uiState.value.sortOption)
                _uiState.value = _uiState.value.copy(
                    files = sortedFiles,
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

    fun uploadFile(uri: Uri, fileName: String) {
        val connection = currentConnection ?: return
        val currentPath = _uiState.value.currentPath
        val remotePath = if (currentPath.endsWith("/")) {
            "$currentPath$fileName"
        } else {
            "$currentPath/$fileName"
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isUploading = true,
                uploadProgress = 0f,
                uploadFileName = fileName
            )

            try {
                // Simulate progress updates
                for (progress in 1..10) {
                    kotlinx.coroutines.delay(200)
                    _uiState.value = _uiState.value.copy(
                        uploadProgress = progress / 10f
                    )
                }
                
                // Create a temporary file from the URI
                val tempFile = createTempFileFromUri(uri, fileName)
                if (tempFile != null) {
                    uploadFileUseCase(connection, tempFile.absolutePath, remotePath)
                    // Clean up the temporary file
                    tempFile.delete()
                } else {
                    throw Exception("Failed to read file from URI")
                }
                
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadProgress = 0f,
                    uploadFileName = "",
                    message = "Upload completed successfully"
                )
                refresh()
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadProgress = 0f,
                    uploadFileName = "",
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

    fun handleBackNavigation(): Boolean {
        val currentState = _uiState.value
        return when {
            currentState.isSelectionMode -> {
                exitSelectionMode()
                true
            }
            currentState.currentPath != "/" && currentState.currentPath.isNotEmpty() -> {
                val parentPath = currentState.currentPath.substringBeforeLast("/")
                    .takeIf { it.isNotEmpty() } ?: "/"
                navigateToDirectory(parentPath)
                true
            }
            else -> false
        }
    }

    fun enterSelectionMode() {
        _uiState.value = _uiState.value.copy(
            isSelectionMode = true,
            selectedFiles = emptySet()
        )
    }

    fun enterSelectionModeWithFile(filePath: String) {
        _uiState.value = _uiState.value.copy(
            isSelectionMode = true,
            selectedFiles = setOf(filePath)
        )
    }

    fun exitSelectionMode() {
        _uiState.value = _uiState.value.copy(
            isSelectionMode = false,
            selectedFiles = emptySet()
        )
    }

    fun toggleFileSelection(filePath: String) {
        val currentSelected = _uiState.value.selectedFiles
        val newSelected = if (currentSelected.contains(filePath)) {
            currentSelected - filePath
        } else {
            currentSelected + filePath
        }
        _uiState.value = _uiState.value.copy(selectedFiles = newSelected)
    }

    fun setSortOption(sortOption: SortOption) {
        val currentFiles = _uiState.value.files
        val sortedFiles = sortFiles(currentFiles, sortOption)
        _uiState.value = _uiState.value.copy(
            sortOption = sortOption,
            files = sortedFiles
        )
    }

    private fun sortFiles(files: List<RemoteFile>, sortOption: SortOption): List<RemoteFile> {
        return when (sortOption) {
            SortOption.NAME -> {
                // Sort by name only (mixed files and directories)
                files.sortedBy { it.name.lowercase() }
            }
            SortOption.TYPE -> {
                // Sort by type: directories first, then files, then by name within each group
                files.sortedWith(compareBy<RemoteFile> { !it.isDirectory }.thenBy { it.name.lowercase() })
            }
            SortOption.LAST_MODIFIED -> {
                // Check if we have meaningful timestamps (not all zero or very old)
                val hasValidTimestamps = files.any { it.lastModified > 946684800000L } // After year 2000
                
                if (hasValidTimestamps) {
                    // Sort by last modified date (most recent first), with name as tiebreaker
                    files.sortedWith(
                        compareByDescending<RemoteFile> { it.lastModified }
                            .thenBy { it.name.lowercase() }
                    )
                } else {
                    // Fall back to name sorting if timestamps are not meaningful
                    files.sortedBy { it.name.lowercase() }
                }
            }
        }
    }

    fun deleteSelectedFiles() {
        val connection = currentConnection ?: return
        val selectedFiles = _uiState.value.selectedFiles
        
        if (selectedFiles.isEmpty()) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                var deletedCount = 0
                var failedCount = 0
                
                selectedFiles.forEach { filePath ->
                    try {
                        deleteFileUseCase(connection, filePath)
                        deletedCount++
                    } catch (exception: Exception) {
                        failedCount++
                    }
                }
                
                val message = when {
                    failedCount == 0 -> "Deleted $deletedCount files successfully"
                    deletedCount == 0 -> "Failed to delete all $failedCount files"
                    else -> "Deleted $deletedCount files, failed to delete $failedCount files"
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSelectionMode = false,
                    selectedFiles = emptySet(),
                    message = message
                )
                
                // Refresh the file list to show the updated state
                refresh()
                
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to delete files: ${exception.message}"
                )
            }
        }
    }

    fun deleteFile(filePath: String) {
        val connection = currentConnection ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                deleteFileUseCase(connection, filePath)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "File deleted successfully"
                )
                refresh()
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to delete file: ${exception.message}"
                )
            }
        }
    }

    fun renameFile(filePath: String, newName: String) {
        val connection = currentConnection ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                renameFileUseCase(connection, filePath, newName)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "File renamed successfully"
                )
                refresh()
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to rename file: ${exception.message}"
                )
            }
        }
    }
    
    private fun createTempFileFromUri(uri: Uri, fileName: String): File? {
        return try {
            val inputStream = application.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val tempFile = File.createTempFile(
                    "upload_", 
                    "_$fileName", 
                    application.cacheDir
                )
                
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                inputStream.close()
                tempFile
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

enum class SortOption {
    NAME, TYPE, LAST_MODIFIED
}

data class FileBrowserUiState(
    val connectionId: String = "",
    val connectionName: String = "",
    val currentPath: String = "/",
    val files: List<RemoteFile> = emptyList(),
    val viewMode: String = "list",
    val showHiddenFiles: Boolean = false,
    val sortOption: SortOption = SortOption.NAME,
    val protocol: String = "",
    val shareName: String? = null,
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val uploadFileName: String = "",
    val downloadingFiles: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val selectedFiles: Set<String> = emptySet(),
    val error: String? = null,
    val message: String? = null
)