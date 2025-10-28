package com.grid.app.presentation.screens.filebrowser

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.FileOutputStream
import android.content.Intent
import android.webkit.MimeTypeMap
import android.os.Environment
import com.grid.app.domain.model.Connection
import com.grid.app.domain.model.RemoteFile
import com.grid.app.domain.usecase.connection.GetConnectionUseCase
import com.grid.app.domain.usecase.file.CreateDirectoryUseCase
import com.grid.app.domain.usecase.file.DeleteFileUseCase
import com.grid.app.domain.usecase.file.DownloadFileUseCase
import com.grid.app.domain.usecase.file.DownloadFileWithProgressUseCase
import com.grid.app.domain.usecase.file.ListFilesUseCase
import com.grid.app.domain.usecase.file.RenameFileUseCase
import com.grid.app.domain.usecase.file.RenameDirUseCase
import com.grid.app.domain.usecase.file.UploadFileUseCase
import com.grid.app.domain.usecase.settings.GetSettingsUseCase
import com.grid.app.domain.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    private val downloadFileWithProgressUseCase: DownloadFileWithProgressUseCase,
    private val uploadFileUseCase: UploadFileUseCase,
    private val createDirectoryUseCase: CreateDirectoryUseCase,
    private val deleteFileUseCase: DeleteFileUseCase,
    private val renameFileUseCase: RenameFileUseCase,
    private val renameDirUseCase: RenameDirUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileBrowserUiState())
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    private var currentConnection: Connection? = null
    private val downloadJobs = mutableMapOf<String, Job>()
    private var currentUploadTransferId: String? = null
    private var currentUploadJob: Job? = null
    private var currentUploadRemotePath: String? = null

    fun initialize(connectionId: String, initialPath: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val connection = getConnectionUseCase(connectionId)
                currentConnection = connection

                // Load settings to get view mode
                val settings = getSettingsUseCase().first()
                
                // Determine the starting path based on connection configuration or protocol
                val startingPath = initialPath ?: when (connection.protocol.name) {
                    "SMB" -> ""
                    "FTP", "SFTP" -> connection.startingDirectory?.takeIf { it.isNotBlank() } ?: "/"
                    else -> "/"
                }
                
                _uiState.value = _uiState.value.copy(
                    connectionId = connectionId,
                    connectionName = connection.name,
                    currentPath = startingPath,
                    viewMode = settings.defaultViewMode.name.lowercase(),
                    showHiddenFiles = settings.showHiddenFiles,
                    protocol = connection.protocol.name,
                    shareName = connection.shareName,
                    isLoading = false
                )

                loadFiles(startingPath)
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

    fun openFile(file: RemoteFile, onFileReady: (File) -> Unit) {
        val connection = currentConnection ?: return

        val job = viewModelScope.launch {
            try {
                // Create temporary file in cache directory
                val tempDir = File(application.cacheDir, "opened_files")
                if (!tempDir.exists()) {
                    tempDir.mkdirs()
                }
                
                // Create cache filename that includes connection and path info to avoid conflicts
                val cacheFileName = "${connection.id}_${file.path.replace('/', '_')}_${file.name}"
                val tempFile = File(tempDir, cacheFileName)
                
                // Check if file is already cached and valid
                if (tempFile.exists() && isCacheValid(tempFile, file)) {
                    // File is cached and valid, use it directly
                    onFileReady(tempFile)
                    return@launch
                }
                
                // File not cached or invalid, download it
                _uiState.value = _uiState.value.copy(
                    downloadingFiles = _uiState.value.downloadingFiles + file.path,
                    downloadProgress = _uiState.value.downloadProgress + (file.path to 0f)
                )
                
                // Download file to temp location with progress tracking
                downloadFileWithProgressUseCase(connection, file, tempFile.absolutePath)
                    .collect { transfer ->
                        val progress = if (transfer.progress.totalBytes > 0) {
                            transfer.progress.bytesTransferred.toFloat() / transfer.progress.totalBytes.toFloat()
                        } else {
                            0f
                        }
                        
                        _uiState.value = _uiState.value.copy(
                            downloadProgress = _uiState.value.downloadProgress + (file.path to progress)
                        )
                        
                        if (transfer.state == com.grid.app.domain.model.TransferState.COMPLETED) {
                            downloadJobs.remove(file.path)
                            _uiState.value = _uiState.value.copy(
                                downloadingFiles = _uiState.value.downloadingFiles - file.path,
                                downloadProgress = _uiState.value.downloadProgress - file.path
                            )
                            
                            // Callback with the downloaded file on main thread
                            onFileReady(tempFile)
                        }
                    }
                
            } catch (exception: Exception) {
                downloadJobs.remove(file.path)
                _uiState.value = _uiState.value.copy(
                    downloadingFiles = _uiState.value.downloadingFiles - file.path,
                    downloadProgress = _uiState.value.downloadProgress - file.path,
                    error = if (exception is kotlinx.coroutines.CancellationException) {
                        null // Don't show error for user-cancelled downloads
                    } else {
                        "Failed to open ${file.name}: ${exception.message}"
                    }
                )
            }
        }
        
        // Store the job so it can be cancelled
        downloadJobs[file.path] = job
    }

    fun cancelDownload(filePath: String) {
        downloadJobs[filePath]?.cancel()
        downloadJobs.remove(filePath)
        
        _uiState.value = _uiState.value.copy(
            downloadingFiles = _uiState.value.downloadingFiles - filePath,
            downloadProgress = _uiState.value.downloadProgress - filePath
        )
    }

    private fun isCacheValid(cachedFile: File, remoteFile: RemoteFile): Boolean {
        // Check if cached file size matches remote file size
        // If remote file size is unknown (0 or -1), we assume cache is valid if file exists
        return if (remoteFile.size > 0) {
            cachedFile.length() == remoteFile.size
        } else {
            // For files without size info, assume cache is valid if it exists and isn't empty
            cachedFile.exists() && cachedFile.length() > 0
        }
    }

    fun clearFileCache() {
        viewModelScope.launch {
            try {
                val tempDir = File(application.cacheDir, "opened_files")
                if (tempDir.exists()) {
                    tempDir.listFiles()?.forEach { file ->
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                // Log error but don't crash
                android.util.Log.e("FileBrowserViewModel", "Failed to clear file cache", e)
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

        // Cancel any existing upload first
        currentUploadJob?.cancel()
        currentUploadJob = null
        currentUploadTransferId = null
        currentUploadRemotePath = null

        // Store remote path for cleanup if needed
        currentUploadRemotePath = remotePath

        currentUploadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isUploading = true,
                uploadProgress = 0f,
                uploadFileName = fileName
            )

            try {
                // Create a temporary file from the URI
                val tempFile = createTempFileFromUri(uri, fileName)
                if (tempFile != null) {
                    try {
                        // Use the UploadFileUseCase with progress tracking
                        uploadFileUseCase.uploadWithProgress(connection, tempFile.absolutePath, remotePath)
                            .collect { fileTransfer ->
                                // Store transfer ID for cancellation
                                if (currentUploadTransferId == null) {
                                    currentUploadTransferId = fileTransfer.id
                                }
                                
                                // Only process updates for the current upload
                                if (fileTransfer.id == currentUploadTransferId && _uiState.value.isUploading) {
                                    // Update progress based on actual file transfer progress
                                    val progressPercent = fileTransfer.progress.progressPercent
                                    _uiState.value = _uiState.value.copy(
                                        uploadProgress = progressPercent / 100f
                                    )
                                }
                                
                                // Check if upload is completed (only for current upload)
                                if (fileTransfer.id == currentUploadTransferId) {
                                    when (fileTransfer.state) {
                                        com.grid.app.domain.model.TransferState.COMPLETED -> {
                                            currentUploadTransferId = null
                                            currentUploadJob = null
                                            currentUploadRemotePath = null
                                            _uiState.value = _uiState.value.copy(
                                                isUploading = false,
                                                uploadProgress = 0f,
                                                uploadFileName = "",
                                                message = "Upload completed successfully"
                                            )
                                            refresh()
                                        }
                                        com.grid.app.domain.model.TransferState.FAILED -> {
                                            currentUploadTransferId = null
                                            currentUploadJob = null
                                            currentUploadRemotePath = null
                                            throw Exception(fileTransfer.errorMessage ?: "Upload failed")
                                        }
                                        com.grid.app.domain.model.TransferState.CANCELLED -> {
                                            currentUploadTransferId = null
                                            currentUploadJob = null
                                            currentUploadRemotePath = null
                                            _uiState.value = _uiState.value.copy(
                                                isUploading = false,
                                                uploadProgress = 0f,
                                                uploadFileName = "",
                                                message = "Upload cancelled"
                                            )
                                        }
                                        else -> {
                                            // IN_PROGRESS or other states - do nothing, already handled above
                                        }
                                    }
                                }
                            }
                    } finally {
                        // Always clean up the temporary file, even if cancelled
                        tempFile.delete()
                    }
                } else {
                    throw Exception("Failed to read file from URI")
                }
            } catch (exception: Exception) {
                currentUploadTransferId = null
                currentUploadJob = null
                currentUploadRemotePath = null
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadProgress = 0f,
                    uploadFileName = "",
                    error = if (exception is kotlinx.coroutines.CancellationException) {
                        null // Don't show error for cancelled uploads
                    } else {
                        "Upload failed: ${exception.message}"
                    }
                )
            }
        }
    }

    fun cancelUpload() {
        // Cancel the coroutine job first
        currentUploadJob?.cancel()
        currentUploadJob = null
        
        val transferId = currentUploadTransferId
        val remotePath = currentUploadRemotePath
        val connection = currentConnection
        
        if (transferId != null) {
            viewModelScope.launch {
                try {
                    fileRepository.cancelTransfer(transferId)
                } catch (exception: Exception) {
                    // Ignore cancellation errors since we're already cancelling
                }
            }
        }
        
        // Clean up partial file on server if upload was in progress
        if (remotePath != null && connection != null) {
            viewModelScope.launch {
                try {
                    // Use the DeleteFileUseCase to clean up the partial file
                    deleteFileUseCase(connection, remotePath)
                } catch (exception: Exception) {
                    // Ignore deletion errors - partial file might not exist or be deletable
                }
            }
        }
        
        // Reset UI state immediately
        currentUploadTransferId = null
        currentUploadRemotePath = null
        _uiState.value = _uiState.value.copy(
            isUploading = false,
            uploadProgress = 0f,
            uploadFileName = "",
            message = "Upload cancelled"
        )
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
            currentState.protocol == "SMB" -> {
                // Handle SMB paths with backslashes
                if (currentState.currentPath.isNotEmpty() && currentState.currentPath != "" && currentState.currentPath.contains("\\")) {
                    val parentPath = currentState.currentPath.substringBeforeLast("\\")
                    navigateToDirectory(parentPath)
                    true
                } else {
                    false
                }
            }
            currentState.currentPath != "/" && currentState.currentPath.isNotEmpty() -> {
                // Handle FTP/SFTP paths with forward slashes
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

    fun downloadSelectedFiles() {
        val connection = currentConnection ?: return
        val selectedFilePaths = _uiState.value.selectedFiles
        
        if (selectedFilePaths.isEmpty()) return
        
        // Get the actual file objects
        val selectedFiles = _uiState.value.files.filter { it.path in selectedFilePaths }
        
        viewModelScope.launch {
            // Start progress tracking for all selected files and immediately clear selection mode
            val filesToDownload = selectedFiles.filter { !it.isDirectory }
            _uiState.value = _uiState.value.copy(
                downloadingFiles = _uiState.value.downloadingFiles + filesToDownload.map { it.path }.toSet(),
                downloadProgress = _uiState.value.downloadProgress + filesToDownload.associate { it.path to 0f },
                isSelectionMode = false,
                selectedFiles = emptySet()
            )
            
            try {
                var downloadedCount = 0
                var failedCount = 0
                var skippedDirsCount = 0
                
                // Use the standard public Downloads directory that users can easily access
                val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Grid")
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }
                
                // Log the download path for debugging
                println("Grid: Downloading files to: ${downloadDir.absolutePath}")
                println("Grid: Download directory exists: ${downloadDir.exists()}")
                println("Grid: Download directory writable: ${downloadDir.canWrite()}")
                
                selectedFiles.forEach { file ->
                    try {
                        if (file.isDirectory) {
                            // Skip directories for now
                            skippedDirsCount++
                        } else {
                            val localFile = File(downloadDir, file.name)
                            // If file exists, append number to make it unique
                            var counter = 1
                            var finalLocalFile = localFile
                            while (finalLocalFile.exists()) {
                                val nameWithoutExt = file.name.substringBeforeLast('.', file.name)
                                val extension = if (file.name.contains('.')) ".${file.name.substringAfterLast('.')}" else ""
                                finalLocalFile = File(downloadDir, "${nameWithoutExt}_$counter$extension")
                                counter++
                            }
                            
                            println("Grid: Attempting to download ${file.name} to ${finalLocalFile.absolutePath}")
                            println("Grid: Parent directory exists: ${finalLocalFile.parentFile?.exists()}")
                            println("Grid: Parent directory writable: ${finalLocalFile.parentFile?.canWrite()}")
                            
                            // Ensure parent directory exists
                            finalLocalFile.parentFile?.mkdirs()
                            
                            // Download with progress tracking
                            downloadFileWithProgressUseCase(connection, file, finalLocalFile.absolutePath)
                                .collect { transfer ->
                                    val progress = if (transfer.progress.totalBytes > 0) {
                                        transfer.progress.bytesTransferred.toFloat() / transfer.progress.totalBytes.toFloat()
                                    } else {
                                        0f
                                    }
                                    
                                    _uiState.value = _uiState.value.copy(
                                        downloadProgress = _uiState.value.downloadProgress + (file.path to progress)
                                    )
                                    
                                    if (transfer.state == com.grid.app.domain.model.TransferState.COMPLETED) {
                                        _uiState.value = _uiState.value.copy(
                                            downloadingFiles = _uiState.value.downloadingFiles - file.path,
                                            downloadProgress = _uiState.value.downloadProgress - file.path
                                        )
                                    }
                                }
                            
                            println("Grid: Download completed. File exists: ${finalLocalFile.exists()}, size: ${finalLocalFile.length()}")
                            println("Grid: File readable: ${finalLocalFile.canRead()}")
                            
                            downloadedCount++
                        }
                    } catch (exception: Exception) {
                        println("Grid: Failed to download ${file.name}: ${exception.message}")
                        exception.printStackTrace()
                        failedCount++
                        
                        // Remove failed download from progress tracking
                        _uiState.value = _uiState.value.copy(
                            downloadingFiles = _uiState.value.downloadingFiles - file.path,
                            downloadProgress = _uiState.value.downloadProgress - file.path
                        )
                    }
                }
                
                val messageParts = mutableListOf<String>()
                if (downloadedCount > 0) messageParts.add("Downloaded $downloadedCount files")
                if (skippedDirsCount > 0) messageParts.add("skipped $skippedDirsCount directories")
                if (failedCount > 0) messageParts.add("failed to download $failedCount files")
                
                val message = if (messageParts.isNotEmpty()) {
                    val baseMessage = messageParts.joinToString(", ").replaceFirstChar { it.uppercase() }
                    if (downloadedCount > 0) {
                        "$baseMessage to ${downloadDir.absolutePath}"
                    } else {
                        baseMessage
                    }
                } else {
                    "No files to download"
                }
                
                _uiState.value = _uiState.value.copy(
                    message = message
                )
                
            } catch (exception: Exception) {
                // Clear any remaining download progress on error
                _uiState.value = _uiState.value.copy(
                    downloadingFiles = emptySet(),
                    downloadProgress = emptyMap(),
                    error = "Failed to download files: ${exception.message}"
                )
            }
        }
    }

    fun deleteSelectedFiles() {
        val selectedFiles = _uiState.value.selectedFiles
        if (selectedFiles.isEmpty()) return
        
        // Show confirmation dialog
        _uiState.value = _uiState.value.copy(showDeleteConfirmation = true)
    }
    
    fun confirmDeleteSelectedFiles() {
        val connection = currentConnection ?: return
        val selectedFiles = _uiState.value.selectedFiles
        
        if (selectedFiles.isEmpty()) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                showDeleteConfirmation = false
            )
            
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
    
    fun dismissDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmation = false)
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
        
        // Find the file object to determine if it's a directory
        val file = _uiState.value.files.find { it.path == filePath }
        val isDirectory = file?.isDirectory ?: false
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                if (isDirectory) {
                    renameDirUseCase(connection, filePath, newName)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = "Directory renamed successfully"
                    )
                } else {
                    renameFileUseCase(connection, filePath, newName)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = "File renamed successfully"
                    )
                }
                
                // Clear selection mode after successful rename
                exitSelectionMode()
                refresh()
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to rename ${if (isDirectory) "directory" else "file"}: ${exception.message}"
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
    val downloadProgress: Map<String, Float> = emptyMap(),
    val isSelectionMode: Boolean = false,
    val selectedFiles: Set<String> = emptySet(),
    val showDeleteConfirmation: Boolean = false,
    val error: String? = null,
    val message: String? = null
)