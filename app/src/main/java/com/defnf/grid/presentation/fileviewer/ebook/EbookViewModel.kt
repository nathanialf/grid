package com.defnf.grid.presentation.fileviewer.ebook

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.defnf.grid.domain.model.EbookReadingPosition
import com.defnf.grid.domain.repository.EbookRepository
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

    companion object {
        private const val TAG = "EbookViewModel"
    }

    private val _uiState = MutableStateFlow<EbookUiState>(EbookUiState.Loading)
    val uiState: StateFlow<EbookUiState> = _uiState.asStateFlow()

    private val epubParser = EpubParser()
    private var currentFile: File? = null
    private var currentBookInfo: EpubBookInfo? = null

    fun loadEbook(file: File) {
        Log.d(TAG, "loadEbook called for file: ${file.name}")
        currentFile = file
        
        viewModelScope.launch {
            Log.d(TAG, "Setting state to Loading")
            _uiState.value = EbookUiState.Loading
            
            try {
                Log.d(TAG, "Starting EPUB parsing...")
                val parseResult = epubParser.parseEpub(file)
                
                if (parseResult.isSuccess) {
                    val bookInfo = parseResult.getOrThrow()
                    currentBookInfo = bookInfo
                    
                    Log.d(TAG, "EPUB parsed successfully - Title: '${bookInfo.title}', Chapters: ${bookInfo.totalChapters}")
                    
                    // Check if there's a saved reading position
                    Log.d(TAG, "Checking for saved reading position...")
                    val savedPosition = ebookRepository.getReadingPosition(file.absolutePath)
                    
                    if (savedPosition != null) {
                        Log.d(TAG, "Found saved position at chapter ${savedPosition.spineIndex}")
                        // Show resume prompt
                        _uiState.value = EbookUiState.ResumePrompt(bookInfo, savedPosition)
                    } else {
                        Log.d(TAG, "No saved position found, starting from beginning")
                        // Start from beginning
                        _uiState.value = EbookUiState.Success(
                            bookInfo = bookInfo,
                            currentChapterIndex = 0
                        )
                    }
                } else {
                    val error = parseResult.exceptionOrNull()
                    Log.e(TAG, "EPUB parsing failed", error)
                    _uiState.value = EbookUiState.Error(
                        error?.message ?: "Failed to parse EPUB file"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during ebook loading", e)
                _uiState.value = EbookUiState.Error(
                    e.message ?: "An error occurred while loading the ebook"
                )
            }
        }
    }

    fun resumeReading() {
        Log.d(TAG, "resumeReading called")
        val currentState = _uiState.value
        if (currentState is EbookUiState.ResumePrompt) {
            Log.d(TAG, "Resuming reading at chapter ${currentState.savedPosition.spineIndex}")
            _uiState.value = EbookUiState.Success(
                bookInfo = currentState.bookInfo,
                currentChapterIndex = currentState.savedPosition.spineIndex,
                scrollOffset = currentState.savedPosition.scrollOffset
            )
        }
    }

    fun startFromBeginning() {
        Log.d(TAG, "startFromBeginning called")
        val currentState = _uiState.value
        if (currentState is EbookUiState.ResumePrompt) {
            Log.d(TAG, "Starting from beginning")
            _uiState.value = EbookUiState.Success(
                bookInfo = currentState.bookInfo,
                currentChapterIndex = 0
            )
        }
    }

    fun navigateToChapter(chapterIndex: Int) {
        Log.d(TAG, "navigateToChapter called with index: $chapterIndex")
        val currentState = _uiState.value
        if (currentState is EbookUiState.Success) {
            val newIndex = chapterIndex.coerceIn(0, currentState.bookInfo.totalChapters - 1)
            Log.d(TAG, "Navigating to chapter $newIndex (clamped from $chapterIndex)")
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