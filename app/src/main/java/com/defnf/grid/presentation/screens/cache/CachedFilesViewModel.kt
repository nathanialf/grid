package com.defnf.grid.presentation.screens.cache

import android.app.Application
import android.content.Intent
import androidx.lifecycle.ViewModel
import com.defnf.grid.presentation.fileviewer.FileViewerActivity
import androidx.lifecycle.viewModelScope
import com.defnf.grid.domain.model.CachedFile
import com.defnf.grid.domain.repository.ConnectionRepository
import com.defnf.grid.domain.repository.FileRepository
import com.defnf.grid.domain.usecase.settings.GetSettingsUseCase
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
    private val fileRepository: FileRepository,
    private val connectionRepository: ConnectionRepository,
    private val getSettingsUseCase: GetSettingsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CachedFilesUiState())
    val uiState: StateFlow<CachedFilesUiState> = _uiState.asStateFlow()

    fun initialize(connectionId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                connectionId = connectionId,
                isLoading = true
            )

            try {
                val connection = connectionRepository.getConnectionById(connectionId)
                val settings = getSettingsUseCase().first()
                _uiState.value = _uiState.value.copy(
                    connectionName = connection.name,
                    viewMode = settings.defaultViewMode.name.lowercase()
                )
                loadCachedFiles(connectionId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load connection: ${e.message}"
                )
            }
        }
    }

    private fun loadCachedFiles(connectionId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val files = withContext(Dispatchers.IO) {
                    fileRepository.getCachedFilesForConnection(connectionId)
                }
                val totalSize = withContext(Dispatchers.IO) {
                    fileRepository.getCacheSizeForConnection(connectionId)
                }

                _uiState.value = _uiState.value.copy(
                    cachedFiles = files,
                    totalCacheSize = totalSize,
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
        val connectionId = _uiState.value.connectionId
        if (connectionId.isNotEmpty()) {
            loadCachedFiles(connectionId)
        }
    }

    fun deleteCachedFile(cachedFile: CachedFile) {
        viewModelScope.launch {
            try {
                val deleted = withContext(Dispatchers.IO) {
                    fileRepository.deleteCachedFile(cachedFile.path)
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
        if (connectionId.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val cleared = withContext(Dispatchers.IO) {
                    fileRepository.clearCacheForConnection(connectionId)
                }

                if (cleared) {
                    _uiState.value = _uiState.value.copy(
                        cachedFiles = emptyList(),
                        totalCacheSize = 0L,
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
            val fileType = getFileType(cachedFile.name)

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

    private fun getFileType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()

        return when {
            extension in setOf("jpg", "jpeg", "png", "bmp", "webp", "svg", "gif") -> "IMAGE"
            extension == "pdf" -> "PDF"
            extension in setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus") -> "AUDIO"
            extension in setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts", "mts") -> "VIDEO"
            extension in setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "lz", "lzma") -> "ARCHIVE"
            extension in setOf("epub", "mobi", "azw", "azw3", "fb2", "lit") -> "EBOOK"
            extension in setOf("md", "markdown") -> "MARKDOWN"
            extension in setOf(
                "js", "ts", "jsx", "tsx", "java", "kt", "py", "cpp", "c", "h", "cs", "php",
                "rb", "go", "rs", "swift", "css", "scss", "sass", "html", "htm", "xml",
                "json", "yaml", "yml", "sql", "sh", "bat", "ps1", "dockerfile", "gradle",
                "groovy", "scala", "clj", "elm", "dart", "vue", "svelte"
            ) -> "CODE"
            else -> "TEXT"
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
    val viewMode: String = "list",
    val cachedFiles: List<CachedFile> = emptyList(),
    val totalCacheSize: Long = 0L,
    val isLoading: Boolean = false,
    val showClearCacheConfirmation: Boolean = false,
    val error: String? = null,
    val message: String? = null
)
