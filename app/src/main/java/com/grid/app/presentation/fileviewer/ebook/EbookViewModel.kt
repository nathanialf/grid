package com.grid.app.presentation.fileviewer.ebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.app.domain.model.EbookReadingPosition
import com.grid.app.domain.repository.EbookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class EbookUiState {
    object Loading : EbookUiState()
    data class Error(val message: String) : EbookUiState()
    data class ResumePrompt(
        val bookInfo: EpubBookInfo,
        val savedPosition: EbookReadingPosition
    ) : EbookUiState()
    data class Success(
        val bookInfo: EpubBookInfo,
        val currentChapterIndex: Int,
        val scrollOffset: Float = 0f
    ) : EbookUiState()
}

@HiltViewModel
class EbookViewModel @Inject constructor(
    private val ebookRepository: EbookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<EbookUiState>(EbookUiState.Loading)
    val uiState: StateFlow<EbookUiState> = _uiState.asStateFlow()

    private val epubParser = EpubParser()
    private var currentFile: File? = null
    private var currentBookInfo: EpubBookInfo? = null

    fun loadEbook(file: File) {
        currentFile = file
        
        viewModelScope.launch {
            _uiState.value = EbookUiState.Loading
            
            try {
                val parseResult = epubParser.parseEpub(file)
                
                if (parseResult.isSuccess) {
                    val bookInfo = parseResult.getOrThrow()
                    currentBookInfo = bookInfo
                    
                    // Check if there's a saved reading position
                    val savedPosition = ebookRepository.getReadingPosition(file.absolutePath)
                    
                    if (savedPosition != null) {
                        // Show resume prompt
                        _uiState.value = EbookUiState.ResumePrompt(bookInfo, savedPosition)
                    } else {
                        // Start from beginning
                        _uiState.value = EbookUiState.Success(
                            bookInfo = bookInfo,
                            currentChapterIndex = 0
                        )
                    }
                } else {
                    val error = parseResult.exceptionOrNull()
                    _uiState.value = EbookUiState.Error(
                        error?.message ?: "Failed to parse EPUB file"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = EbookUiState.Error(
                    e.message ?: "An error occurred while loading the ebook"
                )
            }
        }
    }

    fun resumeReading() {
        val currentState = _uiState.value
        if (currentState is EbookUiState.ResumePrompt) {
            _uiState.value = EbookUiState.Success(
                bookInfo = currentState.bookInfo,
                currentChapterIndex = currentState.savedPosition.spineIndex,
                scrollOffset = currentState.savedPosition.scrollOffset
            )
        }
    }

    fun startFromBeginning() {
        val currentState = _uiState.value
        if (currentState is EbookUiState.ResumePrompt) {
            _uiState.value = EbookUiState.Success(
                bookInfo = currentState.bookInfo,
                currentChapterIndex = 0
            )
        }
    }

    fun navigateToChapter(chapterIndex: Int) {
        val currentState = _uiState.value
        if (currentState is EbookUiState.Success) {
            val newIndex = chapterIndex.coerceIn(0, currentState.bookInfo.totalChapters - 1)
            _uiState.value = currentState.copy(
                currentChapterIndex = newIndex,
                scrollOffset = 0f
            )
            saveCurrentPosition()
        }
    }

    fun previousChapter() {
        val currentState = _uiState.value
        if (currentState is EbookUiState.Success && currentState.currentChapterIndex > 0) {
            navigateToChapter(currentState.currentChapterIndex - 1)
        }
    }

    fun nextChapter() {
        val currentState = _uiState.value
        if (currentState is EbookUiState.Success && 
            currentState.currentChapterIndex < currentState.bookInfo.totalChapters - 1) {
            navigateToChapter(currentState.currentChapterIndex + 1)
        }
    }

    fun updateScrollPosition(scrollOffset: Float) {
        val currentState = _uiState.value
        if (currentState is EbookUiState.Success) {
            _uiState.value = currentState.copy(scrollOffset = scrollOffset)
            saveCurrentPosition()
        }
    }

    private fun saveCurrentPosition() {
        val file = currentFile ?: return
        val bookInfo = currentBookInfo ?: return
        val currentState = _uiState.value as? EbookUiState.Success ?: return

        viewModelScope.launch {
            val currentChapter = bookInfo.chapters.getOrNull(currentState.currentChapterIndex)
            val progress = (currentState.currentChapterIndex + 1).toFloat() / bookInfo.totalChapters

            val position = EbookReadingPosition(
                filePath = file.absolutePath,
                spineIndex = currentState.currentChapterIndex,
                scrollOffset = currentState.scrollOffset,
                progress = progress,
                lastReadTime = System.currentTimeMillis(),
                chapterTitle = currentChapter?.title,
                totalChapters = bookInfo.totalChapters
            )

            ebookRepository.saveReadingPosition(position)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Save position when ViewModel is cleared
        saveCurrentPosition()
    }
}