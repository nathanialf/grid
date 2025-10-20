package com.grid.app.presentation.fileviewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.grid.app.domain.usecase.connection.GetConnectionUseCase
import com.grid.app.domain.usecase.file.CreateDirectoryUseCase
import com.grid.app.domain.usecase.file.UploadFileUseCase
import com.grid.app.presentation.fileviewer.composables.archive.ArchiveExtractor
import com.grid.app.presentation.fileviewer.composables.archive.ExtractionProgress
import com.grid.app.presentation.fileviewer.utils.FileUtils
import java.io.File
import javax.inject.Inject

data class FileViewerUiState(
    val isEditMode: Boolean = false,
    val saveError: String? = null,
    val isExtracting: Boolean = false,
    val extractionProgress: ExtractionProgress? = null,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val extractionComplete: Boolean = false
)

@HiltViewModel
class FileViewerViewModel @Inject constructor(
    private val uploadFileUseCase: UploadFileUseCase,
    private val createDirectoryUseCase: CreateDirectoryUseCase,
    private val getConnectionUseCase: GetConnectionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileViewerUiState())
    val uiState: StateFlow<FileViewerUiState> = _uiState.asStateFlow()

    fun enterEditMode() {
        _uiState.value = _uiState.value.copy(isEditMode = true)
    }

    fun exitEditMode() {
        _uiState.value = _uiState.value.copy(isEditMode = false)
    }

    fun clearSaveError() {
        _uiState.value = _uiState.value.copy(saveError = null)
    }

    fun saveFile(file: File, content: String, connectionId: String?, remotePath: String?) {
        viewModelScope.launch {
            try {
                // Save locally first
                FileUtils.saveFile(file, content)
                    .onSuccess {
                        // Upload to server if connected
                        if (connectionId != null && remotePath != null) {
                            try {
                                val connection = getConnectionUseCase(connectionId)
                                uploadFileUseCase(connection, file.absolutePath, remotePath)
                                exitEditMode()
                            } catch (e: Exception) {
                                _uiState.value = _uiState.value.copy(
                                    saveError = "Failed to upload to server: ${e.message}"
                                )
                            }
                        } else {
                            exitEditMode()
                        }
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            saveError = "Failed to save file: ${error.message}"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    saveError = "Failed to save file: ${e.message}"
                )
            }
        }
    }

    fun extractArchive(
        file: File,
        fileName: String,
        connectionId: String?,
        remotePath: String?
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isExtracting = true)
                
                val extractDir = File(file.parent, file.nameWithoutExtension)
                val archiveExtractor = ArchiveExtractor()
                
                archiveExtractor.extractArchive(file, extractDir).collect { progress ->
                    _uiState.value = _uiState.value.copy(extractionProgress = progress)
                    
                    if (progress.isComplete) {
                        // Upload to server if connected
                        if (connectionId != null && remotePath != null) {
                            uploadExtractedFiles(extractDir, fileName, connectionId, remotePath)
                        } else {
                            completeExtraction()
                        }
                    } else if (progress.error != null) {
                        _uiState.value = _uiState.value.copy(
                            saveError = "Extraction failed: ${progress.error}",
                            isExtracting = false,
                            extractionProgress = null
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    saveError = "Extraction failed: ${e.message}",
                    isExtracting = false,
                    extractionProgress = null
                )
            }
        }
    }

    private suspend fun uploadExtractedFiles(
        extractDir: File,
        fileName: String,
        connectionId: String,
        remotePath: String
    ) {
        try {
            _uiState.value = _uiState.value.copy(isUploading = true, uploadProgress = 0f)
            
            val connection = getConnectionUseCase(connectionId)
            val serverFileNameWithoutExt = fileName.substringBeforeLast('.')
            val remoteDir = remotePath.substringBeforeLast('/') + "/" + serverFileNameWithoutExt
            
            // Create directory on server
            createDirectoryUseCase(connection, remoteDir)
            
            // Upload files with progress tracking
            uploadDirectoryContents(extractDir, remoteDir, connection) { progress ->
                _uiState.value = _uiState.value.copy(uploadProgress = progress)
            }
            
            completeExtraction()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                saveError = "Failed to upload extracted files: ${e.message}",
                isUploading = false,
                uploadProgress = 0f
            )
        }
    }

    private suspend fun uploadDirectoryContents(
        localDir: File,
        remoteDir: String,
        connection: com.grid.app.domain.model.Connection,
        onProgress: (Float) -> Unit
    ) {
        // Count total files
        fun countFiles(dir: File): Int {
            return dir.listFiles()?.sumOf { file ->
                if (file.isDirectory) countFiles(file) else 1
            } ?: 0
        }
        
        val totalFiles = countFiles(localDir)
        var uploadedFiles = 0
        
        // Upload recursively
        suspend fun uploadRecursive(dir: File, remoteDirPath: String) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    val subRemoteDir = "$remoteDirPath/${file.name}"
                    createDirectoryUseCase(connection, subRemoteDir)
                    uploadRecursive(file, subRemoteDir)
                } else {
                    val remoteFilePath = "$remoteDirPath/${file.name}"
                    uploadFileUseCase(connection, file.absolutePath, remoteFilePath)
                    uploadedFiles++
                    onProgress(uploadedFiles.toFloat() / totalFiles.toFloat())
                }
            }
        }
        
        uploadRecursive(localDir, remoteDir)
    }

    private fun completeExtraction() {
        _uiState.value = _uiState.value.copy(
            isExtracting = false,
            isUploading = false,
            extractionProgress = null,
            uploadProgress = 0f,
            extractionComplete = true
        )
    }
}