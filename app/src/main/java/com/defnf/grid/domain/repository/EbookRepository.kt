package com.defnf.grid.domain.repository

import com.defnf.grid.domain.model.EbookReadingPosition
import kotlinx.coroutines.flow.Flow

interface EbookRepository {
    suspend fun saveReadingPosition(position: EbookReadingPosition)
    suspend fun getReadingPosition(filePath: String): EbookReadingPosition?
    fun getAllReadingPositions(): Flow<List<EbookReadingPosition>>
    suspend fun deleteReadingPosition(filePath: String)
}