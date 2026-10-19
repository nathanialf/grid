package com.defnf.grid.presentation.fileviewer.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FileUtils {
    
    /**
     * Save content to a file
     * @param file The file to save to
     * @param content The content to save
     * @return Result indicating success or failure
     */
    suspend fun saveFile(file: File, content: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                file.writeText(content)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Read content from a file
     * @param file The file to read from
     * @return Result with content or error
     */
    suspend fun readFile(file: File): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val content = file.readText()
                Result.success(content)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}