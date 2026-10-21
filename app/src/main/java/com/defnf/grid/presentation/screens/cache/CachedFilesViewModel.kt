package com.defnf.grid.presentation.screens.cache

import android.app.Application
import android.content.Intent
import androidx.lifecycle.ViewModel
import com.defnf.grid.presentation.fileviewer.FileViewerActivity
import androidx.lifecycle.viewModelScope
import com.defnf.grid.data.offline.OfflineFiller
import com.defnf.grid.data.offline.OfflineStore
import com.defnf.grid.domain.model.CachedFile
import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.repository.ConnectionRepository
import com.defnf.grid.domain.usecase.settings.GetSettingsUseCase
import com.defnf.grid.presentation.util.getFileType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CachedFilesViewModel @Inject constructor(
    private val application: Application,
    private val offlineStore: OfflineStore,
    private val offlineFiller: OfflineFiller,
    private val connectionRepository: ConnectionRepository,
    private val getSettingsUseCase: GetSettingsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CachedFilesUiState())
    val uiState: StateFlow<CachedFilesUiState> = _uiState.asStateFlow()

    private var connection: Connection? = null
    private var offlineFolder: File? = null

    fun initialize(connectionId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                connectionId = connectionId,
                isLoading = true
            )

            try {
                val loaded = connectionRepository.getConnectionById(connectionId)
                val folder = offlineStore.folderFor(loaded)
                connection = loaded
                offlineFolder = folder
                val settings = getSettingsUseCase().first()
                _uiState.value = _uiState.value.copy(
                    connectionName = loaded.name,
                    offlineLocation = folder.absolutePath,
                    viewMode = settings.defaultViewMode.name.lowercase()
                )
                loadCachedFiles()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load connection: ${e.message}"
                )
            }
        }
    }

    private fun loadCachedFiles() {
        val connection = connection ?: return
        val folder = offlineFolder ?: return
        viewModelScope.launch {
            // Keep a list that is already showing on screen while it reloads (e.g. after a
            // delete): swapping it for the spinner would lose the scroll position and leave
            // the collapsing app bar collapsed over a list reset to the top.
            _uiState.value = _uiState.value.copy(
                isLoading = _uiState.value.cachedFiles.isEmpty(),
                error = null
            )

            try {
                val files = withContext(Dispatchers.IO) { offlineStore.list(folder, connection) }
                val unfinished = withContext(Dispatchers.IO) { offlineStore.unfinishedBytes(folder) }

                _uiState.value = _uiState.value.copy(
                    cachedFiles = files,
                    totalCacheSize = files.sumOf { it.size },
                    unfinishedSize = unfinished,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load cached files: ${e.message}"
                )
            }
        }
    }

    fun refresh() {
        loadCachedFiles()
    }

    fun deleteCachedFile(cachedFile: CachedFile) {
        viewModelScope.launch {
            try {
                // A save still running for it would put it straight back.
                offlineFiller.cancel(cachedFile.connectionId, cachedFile.remotePath)
                val deleted = withContext(Dispatchers.IO) {
                    offlineStore.delete(File(cachedFile.path))
                }

                if (deleted) {
                    _uiState.value = _uiState.value.copy(
                        message = "Deleted ${cachedFile.name}"
                    )
                    refresh()
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to delete ${cachedFile.name}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to delete file: ${e.message}"
                )
            }
        }
    }

    fun clearAllCache() {
        val connectionId = _uiState.value.connectionId
        val folder = offlineFolder ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // Stop streams still saving here, or they would write their files straight back.
                offlineFiller.cancelAll(connectionId)
                val cleared = withContext(Dispatchers.IO) { offlineStore.clear(folder) }

                if (cleared) {
                    _uiState.value = _uiState.value.copy(
                        cachedFiles = emptyList(),
                        totalCacheSize = 0L,
                        unfinishedSize = 0L,
                        isLoading = false,
                        message = "Cache cleared successfully"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to clear some cached files"
                    )
                    refresh()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to clear cache: ${e.message}"
                )
            }
        }
    }

    fun openCachedFile(cachedFile: CachedFile): Intent? {
        val file = File(cachedFile.path)
        if (!file.exists()) {
            _uiState.value = _uiState.value.copy(
                error = "File no longer exists"
            )
            refresh()
            return null
        }

        return try {
            // Same mapping as the browser, so unknown binaries aren't opened as text
            val fileType = getFileType(cachedFile.name).name

            Intent(application, FileViewerActivity::class.java).apply {
                putExtra("file_path", file.absolutePath)
                putExtra("file_name", cachedFile.name)
                putExtra("file_type", fileType)
                putExtra("connection_id", cachedFile.connectionId)
                putExtra("remote_path", cachedFile.remotePath)
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "Failed to open file: ${e.message}"
            )
            null
        }
    }

    fun showClearCacheConfirmation() {
        _uiState.value = _uiState.value.copy(showClearCacheConfirmation = true)
    }

    fun dismissClearCacheConfirmation() {
        _uiState.value = _uiState.value.copy(showClearCacheConfirmation = false)
    }

    fun confirmClearCache() {
        _uiState.value = _uiState.value.copy(showClearCacheConfirmation = false)
        clearAllCache()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

data class CachedFilesUiState(
    val connectionId: String = "",
    val connectionName: String = "",
    // Where this connection's offline files live, e.g. /storage/emulated/0/Download/Grid/NAS
    val offlineLocation: String = "",
    val viewMode: String = "list",
    val cachedFiles: List<CachedFile> = emptyList(),
    val totalCacheSize: Long = 0L,
    // Hidden, unlisted partial saves (e.g. a stream left part-way), which still take space
    val unfinishedSize: Long = 0L,
    val isLoading: Boolean = false,
    val showClearCacheConfirmation: Boolean = false,
    val error: String? = null,
    val message: String? = null
)
