package com.defnf.grid.presentation.screens.filebrowser

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.FileOutputStream
import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.Protocol
import com.defnf.grid.domain.model.RemoteFile
import com.defnf.grid.domain.model.TransferState
import com.defnf.grid.domain.usecase.connection.GetConnectionUseCase
import com.defnf.grid.domain.usecase.file.CreateDirectoryUseCase
import com.defnf.grid.domain.usecase.file.DeleteFileUseCase
import com.defnf.grid.domain.usecase.file.DownloadFileUseCase
import com.defnf.grid.domain.usecase.file.DownloadFileWithProgressUseCase
import com.defnf.grid.domain.usecase.file.ListFilesUseCase
import com.defnf.grid.domain.usecase.file.RenameFileUseCase
import com.defnf.grid.domain.usecase.file.RenameDirUseCase
import com.defnf.grid.domain.usecase.file.UploadFileUseCase
import com.defnf.grid.domain.usecase.settings.GetSettingsUseCase
import com.defnf.grid.domain.repository.FileRepository
import com.defnf.grid.data.media.RemoteMediaUri
import com.defnf.grid.data.offline.OfflineFiller
import com.defnf.grid.data.offline.OfflineStore
import com.defnf.grid.data.remote.ssh.HostKeyVerifier
import com.defnf.grid.di.IoDispatcher
import com.defnf.grid.domain.model.HostKeyChangedException
import com.defnf.grid.domain.model.findHostKeyChange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val fileRepository: FileRepository,
    private val hostKeyVerifier: HostKeyVerifier,
    private val offlineStore: OfflineStore,
    private val offlineFiller: OfflineFiller,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileBrowserUiState())
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    private companion object {
        /** SavedStateHandle key for the browsed path, so it survives activity/process recreation. */
        const val KEY_CURRENT_PATH = "file_browser_current_path"
    }

    private var currentConnection: Connection? = null
    private var fillProgressJob: Job? = null
    private val downloadJobs = mutableMapOf<String, Job>()
    private var loadJob: Job? = null
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
                val offlineFolder = offlineStore.folderFor(connection)

                // SMB paths are relative to the share, so its top level is the root; SFTP may be
                // pinned to a starting directory.
                val rootPath = when (connection.protocol) {
                    Protocol.SMB -> ""
                    Protocol.SFTP -> connection.startingDirectory?.takeIf { it.isNotBlank() } ?: "/"
                }

                // Determine the starting path. Prefer an explicit initialPath, then any path
                // restored from SavedStateHandle (survives MainActivity recreation / process
                // death while the file viewer is in front), and only fall back to the connection
                // default on a genuinely fresh open. Without this, returning from the viewer after
                // the OS reclaimed MainActivity would reset the browser to the connection root.
                val startingPath = initialPath
                    ?: savedStateHandle.get<String>(KEY_CURRENT_PATH)
                    ?: rootPath
                savedStateHandle[KEY_CURRENT_PATH] = startingPath

                _uiState.value = _uiState.value.copy(
                    connectionId = connectionId,
                    connectionName = connection.name,
                    currentPath = startingPath,
                    viewMode = settings.defaultViewMode.name.lowercase(),
                    showHiddenFiles = settings.showHiddenFiles,
                    protocol = connection.protocol.name,
                    shareName = connection.shareName,
                    rootPath = rootPath,
                    offlineFolder = offlineFolder,
                    isLoading = false
                )

                observeFills(connection.id)
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
        savedStateHandle[KEY_CURRENT_PATH] = path
        // A selection only makes sense in the folder it was made in; carrying it over would let
        // Delete act on files that are no longer on screen.
        _uiState.value = _uiState.value.copy(
            currentPath = path,
            isSelectionMode = false,
            selectedFiles = emptySet()
        )
        loadFiles(path)
    }

    fun refresh() {
        loadFiles(_uiState.value.currentPath)
    }

    /**
     * The listed files that have an offline copy, matched by exact name: shared storage ignores
     * case, so a plain exists() would also match another file's copy.
     */
    private suspend fun offlineCopiesOf(connection: Connection, files: List<RemoteFile>): Map<String, File> {
        val folder = _uiState.value.offlineFolder ?: return emptyMap()
        return withContext(ioDispatcher) {
            files.filter { !it.isDirectory }
                .groupBy { OfflineStore.fileIn(folder, it.path, connection.protocol).parentFile }
                .flatMap { (dir, inDir) ->
                    val names = dir?.list()?.toHashSet() ?: return@flatMap emptyList()
                    inDir.mapNotNull { file ->
                        val local = OfflineStore.fileIn(folder, file.path, connection.protocol)
                        if (local.name in names) file.path to local else null
                    }
                }
                .toMap()
        }
    }

    private fun refreshOfflineCopies() {
        val connection = currentConnection ?: return
        viewModelScope.launch {
            val copies = offlineCopiesOf(connection, _uiState.value.files)
            _uiState.value = _uiState.value.copy(offlineCopies = copies)
        }
    }

    /** Goes to the parent folder; false at the connection's root, which nothing goes above. */
    fun navigateUp(): Boolean {
        val state = _uiState.value
        val parent = BrowsePaths.parentWithinRoot(state.currentPath, state.rootPath, state.protocol == "SMB")
            ?: return false
        navigateToDirectory(parent)
        return true
    }

    /** Shows streamed media saving offline in the background like any other download. */
    private fun observeFills(connectionId: String) {
        val prefix = OfflineFiller.keyOf(connectionId, "")
        fillProgressJob?.cancel()
        fillProgressJob = viewModelScope.launch {
            offlineFiller.progress
                .map { fills ->
                    fills.filterKeys { it.startsWith(prefix) }.mapKeys { it.key.removePrefix(prefix) }
                }
                .collect { fills ->
                    val ended = _uiState.value.fillProgress.keys - fills.keys
                    _uiState.value = _uiState.value.copy(fillProgress = fills)
                    // A finished save may have a thumbnail to show now.
                    if (ended.isNotEmpty()) refreshOfflineCopies()
                }
        }
    }

    /** The user accepted the server's new host key; remember it and reconnect. */
    fun trustChangedHostKey() {
        val change = _uiState.value.hostKeyChange ?: return
        hostKeyVerifier.trust(change.host, change.port, change.fingerprint)
        _uiState.value = _uiState.value.copy(hostKeyChange = null, error = null)
        refresh()
    }

    fun dismissHostKeyChange() {
        _uiState.value = _uiState.value.copy(hostKeyChange = null)
    }

    private fun loadFiles(path: String) {
        val connection = currentConnection ?: return

        // Only the latest listing may update the screen; otherwise quickly tapping into a folder
        // and back can show one folder's contents under another's path.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val files = listFilesUseCase(connection, path)
                val sortedFiles = sortFiles(files, _uiState.value.sortOption)
                val offlineCopies = offlineCopiesOf(connection, sortedFiles)
                _uiState.value = _uiState.value.copy(
                    files = sortedFiles,
                    offlineCopies = offlineCopies,
                    isLoading = false,
                    error = null
                )
            } catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                _uiState.value = _uiState.value.copy(
                    files = emptyList(),
                    isLoading = false,
                    hostKeyChange = exception.findHostKeyChange(),
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

        // A second tap on a file that is still downloading cancels it rather than starting a
        // parallel download of the same file.
        if (downloadJobs[file.path]?.isActive == true) {
            cancelDownload(file.path)
            return
        }

        val job = viewModelScope.launch {
            try {
                existingCopyOf(connection, file)?.let { copy ->
                    onFileReady(copy)
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    downloadingFiles = _uiState.value.downloadingFiles + file.path,
                    downloadProgress = _uiState.value.downloadProgress + (file.path to 0f)
                )
                val saved = fetchOffline(connection, file, offlineStore.fileFor(connection, file.path))
                _uiState.value = _uiState.value.copy(
                    downloadingFiles = _uiState.value.downloadingFiles - file.path,
                    downloadProgress = _uiState.value.downloadProgress - file.path
                )
                onFileReady(saved)
            } catch (exception: Exception) {
                // A newer download of the same file owns the progress state now; leave it alone.
                val owner = downloadJobs[file.path]
                if (owner != null && owner !== coroutineContext[Job]) return@launch
                _uiState.value = _uiState.value.copy(
                    downloadingFiles = _uiState.value.downloadingFiles - file.path,
                    downloadProgress = _uiState.value.downloadProgress - file.path,
                    error = if (exception is kotlinx.coroutines.CancellationException) {
                        null // Don't show error for user-cancelled downloads
                    } else {
                        "Failed to open ${file.name}: ${exception.message}"
                    }
                )
            } finally {
                if (downloadJobs[file.path] === coroutineContext[Job]) {
                    downloadJobs.remove(file.path)
                }
            }
        }

        // Store the job so it can be cancelled
        if (job.isActive) {
            downloadJobs[file.path] = job
        }
    }

    /**
     * Audio and video play straight from the server instead of downloading first, saving offline
     * as they play; a finished offline copy is still preferred since it needs no network at all.
     */
    fun openMedia(file: RemoteFile, onLocalFile: (File) -> Unit, onStream: (String) -> Unit) {
        val connection = currentConnection ?: return
        viewModelScope.launch {
            val copy = existingCopyOf(connection, file)
            if (copy != null) {
                onLocalFile(copy)
            } else {
                onStream(RemoteMediaUri.build(connection.id, file.path, file.name, file.size, file.lastModified).toString())
            }
        }
    }

    /** A finished local copy of [file]: its offline copy, or else an unlisted fallback copy. */
    private suspend fun existingCopyOf(connection: Connection, file: RemoteFile): File? {
        val candidates = listOf(
            offlineStore.fileFor(connection, file.path),
            offlineStore.unlistedFileFor(connection, file.path)
        )
        return withContext(ioDispatcher) { candidates.firstOrNull { isOffline(it, file) } }
    }

    /**
     * Downloads [file] into its offline copy [target] through a hidden part file that is swapped in
     * only once complete, so a cancelled or failed transfer never leaves a truncated file that looks
     * like a finished one. Progress lands in [FileBrowserUiState.downloadProgress].
     *
     * Returns the saved file: [target], or an unlisted fallback copy when [target] couldn't be
     * written (see [placeOutsideDownloads]).
     */
    private suspend fun fetchOffline(connection: Connection, file: RemoteFile, target: File): File {
        // Unique per attempt: a cancelled download may still be writing (and then deleting) its
        // part file while a re-tap's new download has already started.
        val partFile = OfflineStore.newPartFor(target)
        try {
            withContext(ioDispatcher) { target.parentFile?.mkdirs() }

            // Progress arrives per transfer buffer; only publish whole-percent changes so a
            // large file doesn't recompose the browser thousands of times.
            var lastPercent = -1
            downloadFileWithProgressUseCase(connection, file, partFile.absolutePath)
                .collect { transfer ->
                    val progress = if (transfer.progress.totalBytes > 0) {
                        transfer.progress.bytesTransferred.toFloat() / transfer.progress.totalBytes.toFloat()
                    } else {
                        0f
                    }
                    val percent = (progress * 100).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        _uiState.value = _uiState.value.copy(
                            downloadProgress = _uiState.value.downloadProgress + (file.path to progress)
                        )
                    }
                }

            // The flow completes once the file is fully written and closed (this also covers
            // empty files, which never report a COMPLETED progress tick).
            val placed = withContext(ioDispatcher) {
                target.delete()
                partFile.renameTo(target)
            }
            if (!placed) return placeOutsideDownloads(connection, file, target, partFile)
            offlineStore.publish(target)
            refreshOfflineCopies()
            return target
        } catch (exception: Exception) {
            withContext(NonCancellable + ioDispatcher) { partFile.delete() }
            throw exception
        }
    }

    /**
     * [target]'s name is taken by a file Grid may not touch: on Android 11+ most likely one an
     * earlier install of Grid saved, which this install can neither see nor replace. Keeps the
     * downloaded bytes as an unlisted private copy so the file still opens, and says what's in the way.
     */
    private suspend fun placeOutsideDownloads(
        connection: Connection,
        file: RemoteFile,
        target: File,
        partFile: File
    ): File {
        val fallback = offlineStore.unlistedFileFor(connection, file.path)
        withContext(ioDispatcher) {
            try {
                fallback.parentFile?.mkdirs()
                partFile.copyTo(fallback, overwrite = true)
            } finally {
                partFile.delete()
            }
        }
        val reason = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && !target.exists()) {
            "a file of that name there likely belongs to an earlier install of Grid. Delete it with a file manager to keep this one offline"
        } else {
            "a file of that name there can't be replaced"
        }
        _uiState.value = _uiState.value.copy(
            message = "Couldn't save ${file.name} to ${target.parent}: $reason."
        )
        return fallback
    }

    fun cancelDownload(filePath: String) {
        downloadJobs[filePath]?.cancel()
        downloadJobs.remove(filePath)
        
        _uiState.value = _uiState.value.copy(
            downloadingFiles = _uiState.value.downloadingFiles - filePath,
            downloadProgress = _uiState.value.downloadProgress - filePath
        )
    }

    private fun isOffline(local: File, remoteFile: RemoteFile): Boolean =
        local.exists() && OfflineStore.isValid(local, remoteFile.size)

    fun uploadFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val connection = currentConnection ?: return
        val currentPath = _uiState.value.currentPath

        // Cancel any existing upload first
        currentUploadJob?.cancel()
        currentUploadJob = null
        currentUploadTransferId = null
        currentUploadRemotePath = null

        currentUploadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isUploading = true,
                uploadProgress = 0f,
                uploadFileName = "",
                uploadTotalCount = uris.size,
                uploadCurrentIndex = 0
            )

            var uploadedCount = 0
            var failedCount = 0

            try {
                // Upload sequentially: each transfer opens its own connection, and most
                // SFTP/FTP/SMB servers cap concurrent connections, so parallel is unreliable.
                uris.forEachIndexed { index, uri ->
                    // Advance the "X of N" counter before resolving the file.
                    _uiState.value = _uiState.value.copy(
                        uploadCurrentIndex = index + 1,
                        uploadProgress = 0f
                    )

                    // Reset per-file tracking so cancelUpload() targets the in-flight file.
                    currentUploadTransferId = null
                    currentUploadRemotePath = null

                    var tempFile: File? = null
                    try {
                        // ContentResolver queries and the local copy are disk I/O; keep them off
                        // the main thread so large files don't freeze the UI.
                        val fileName = withContext(ioDispatcher) { resolveDisplayName(uri) }
                        val remotePath = if (currentPath.endsWith("/")) {
                            "$currentPath$fileName"
                        } else {
                            "$currentPath/$fileName"
                        }
                        _uiState.value = _uiState.value.copy(uploadFileName = fileName)

                        tempFile = withContext(ioDispatcher) { createTempFileFromUri(uri, fileName) }
                            ?: throw Exception("Failed to read file from URI")

                        // Use the UploadFileUseCase with progress tracking
                        uploadFileUseCase.uploadWithProgress(connection, tempFile.absolutePath, remotePath)
                            .collect { fileTransfer ->
                                if (currentUploadTransferId == null) {
                                    currentUploadTransferId = fileTransfer.id
                                }
                                if (fileTransfer.id != currentUploadTransferId) return@collect
                                // The repository's first emission precedes opening the remote file, so
                                // only once bytes have been written is there a partial file of ours
                                // that cancel may clean up; before that the path may be someone's
                                // existing file.
                                if (fileTransfer.state == TransferState.IN_PROGRESS &&
                                    fileTransfer.progress.bytesTransferred > 0
                                ) {
                                    currentUploadRemotePath = remotePath
                                }

                                when (fileTransfer.state) {
                                    TransferState.COMPLETED -> {
                                        // Finished files must never be deleted by a later cancel.
                                        currentUploadRemotePath = null
                                        _uiState.value = _uiState.value.copy(uploadProgress = 1f)
                                    }
                                    TransferState.FAILED -> {
                                        throw Exception(fileTransfer.errorMessage ?: "Upload failed")
                                    }
                                    else -> {
                                        // IN_PROGRESS and other transient states.
                                        val progressPercent = fileTransfer.progress.progressPercent
                                        _uiState.value = _uiState.value.copy(
                                            uploadProgress = progressPercent / 100f
                                        )
                                    }
                                }
                            }
                        uploadedCount++
                    } catch (cancellation: kotlinx.coroutines.CancellationException) {
                        // Abort the whole batch; cancelUpload() owns the UI reset/message.
                        throw cancellation
                    } catch (exception: Exception) {
                        // Continue-on-failure: skip this file and keep going.
                        failedCount++
                    } finally {
                        // Always clean up the temporary file, even if cancelled.
                        tempFile?.delete()
                    }
                }

                // Batch complete.
                currentUploadTransferId = null
                currentUploadJob = null
                currentUploadRemotePath = null
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadProgress = 0f,
                    uploadFileName = "",
                    uploadTotalCount = 0,
                    uploadCurrentIndex = 0,
                    message = buildUploadSummary(uploadedCount, failedCount)
                )
                refresh()
            } catch (exception: Exception) {
                currentUploadTransferId = null
                currentUploadJob = null
                currentUploadRemotePath = null
                if (exception !is kotlinx.coroutines.CancellationException) {
                    // Cancellation UI is handled by cancelUpload(); only report real errors.
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        uploadProgress = 0f,
                        uploadFileName = "",
                        uploadTotalCount = 0,
                        uploadCurrentIndex = 0,
                        error = "Upload failed: ${exception.message}"
                    )
                }
            }
        }
    }

    /** Resolves a content Uri's display name, falling back to a generic name. */
    private fun resolveDisplayName(uri: Uri): String {
        return application.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        } ?: "uploaded_file"
    }

    /** Builds the aggregate snackbar summary after a batch upload finishes. */
    private fun buildUploadSummary(uploadedCount: Int, failedCount: Int): String {
        val parts = mutableListOf<String>()
        if (uploadedCount > 0) parts.add("Uploaded $uploadedCount files")
        if (failedCount > 0) parts.add("failed to upload $failedCount files")
        return if (parts.isNotEmpty()) {
            parts.joinToString(", ").replaceFirstChar { it.uppercase() }
        } else {
            "No files uploaded"
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
            uploadTotalCount = 0,
            uploadCurrentIndex = 0,
            message = "Upload cancelled"
        )
    }

    fun createDirectory(directoryName: String) {
        val connection = currentConnection ?: return
        val currentPath = _uiState.value.currentPath
        // Use appropriate path separator based on protocol
        val separator = if (connection.protocol.name == "SMB") "\\" else "/"

        viewModelScope.launch {
            try {
                val fullPath = if (currentPath.endsWith(separator)) {
                    "$currentPath$directoryName"
                } else {
                    "$currentPath$separator$directoryName"
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
        if (_uiState.value.isSelectionMode) {
            exitSelectionMode()
            return true
        }
        return navigateUp()
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

    /** Saves the selected files offline (into Downloads/Grid), skipping ones that already are. */
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
                var blockedCount = 0
                val skippedDirsCount = selectedFiles.size - filesToDownload.size
                val offlineFolder = offlineStore.folderFor(connection)

                filesToDownload.forEach { file ->
                    try {
                        val target = offlineStore.fileFor(connection, file.path)
                        val saved = if (withContext(ioDispatcher) { isOffline(target, file) }) {
                            target
                        } else {
                            fetchOffline(connection, file, target)
                        }
                        // Only a copy under Downloads counts as downloaded; an unlisted one doesn't.
                        if (saved.startsWith(offlineFolder)) downloadedCount++ else blockedCount++
                    } catch (exception: Exception) {
                        if (exception is kotlinx.coroutines.CancellationException) throw exception
                        failedCount++
                    } finally {
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
                if (blockedCount > 0) {
                    messageParts.add("couldn't place $blockedCount in Downloads (a file of that name is in the way)")
                }
                
                val message = if (messageParts.isNotEmpty()) {
                    val baseMessage = messageParts.joinToString(", ").replaceFirstChar { it.uppercase() }
                    if (downloadedCount > 0) {
                        "$baseMessage to ${offlineFolder.absolutePath}"
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
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                // Clear any remaining download progress on error
                _uiState.value = _uiState.value.copy(
                    downloadingFiles = _uiState.value.downloadingFiles - filesToDownload.map { it.path }.toSet(),
                    downloadProgress = _uiState.value.downloadProgress - filesToDownload.map { it.path }.toSet(),
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
        // Only ever delete what is actually listed in the current folder.
        val visiblePaths = _uiState.value.files.mapTo(HashSet()) { it.path }
        val selectedFiles = _uiState.value.selectedFiles.filter { it in visiblePaths }
        
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
    // The connection's top folder; back navigation never goes above it
    val rootPath: String = "/",
    // Where this connection's offline copies live (Downloads/Grid/<connection>)
    val offlineFolder: File? = null,
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val uploadFileName: String = "",
    val uploadTotalCount: Int = 0,
    val uploadCurrentIndex: Int = 0,
    val downloadingFiles: Set<String> = emptySet(),
    val downloadProgress: Map<String, Float> = emptyMap(),
    // Streamed media still saving offline in the background, by remote path
    val fillProgress: Map<String, Float> = emptyMap(),
    // Listed files' offline copies, by remote path (for thumbnails)
    val offlineCopies: Map<String, File> = emptyMap(),
    val isSelectionMode: Boolean = false,
    val selectedFiles: Set<String> = emptySet(),
    val showDeleteConfirmation: Boolean = false,
    // Set when the SFTP server's host key no longer matches the trusted one
    val hostKeyChange: HostKeyChangedException? = null,
    val error: String? = null,
    val message: String? = null
) {
    val canNavigateUp: Boolean
        get() = BrowsePaths.parentWithinRoot(currentPath, rootPath, protocol == "SMB") != null
}