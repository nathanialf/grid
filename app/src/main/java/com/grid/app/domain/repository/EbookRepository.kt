package com.grid.app.domain.repository

import com.grid.app.domain.model.EbookReadingPosition
import kotlinx.coroutines.flow.Flow

interface EbookRepository {
    suspend fun saveReadingPosition(position: EbookReadingPosition)
    suspend fun getReadingPosition(filePath: String): EbookReadingPosition?
    fun getAllReadingPositions(): Flow<List<EbookReadingPosition>>
    suspend fun deleteReadingPosition(filePath: String)
}