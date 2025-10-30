package com.defnf.grid.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class EbookReadingPosition(
    val filePath: String,
    val spineIndex: Int, // Current chapter/section index in EPUB spine
    val scrollOffset: Float = 0f, // Scroll position within the chapter
    val progress: Float = 0f, // Overall progress through the book (0.0 to 1.0)
    val lastReadTime: Long, // Timestamp when last read
    val chapterTitle: String? = null, // Title of current chapter
    val totalChapters: Int = 0 // Total number of chapters in the book
)