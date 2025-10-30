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
    
    /**
     * Check if a file is editable based on its extension
     * @param file The file to check
     * @return true if the file can be edited
     */
    fun isEditableFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in setOf(
            // Text files
            "txt", "text", "log", "csv", "tsv",
            // Code files
            "js", "ts", "jsx", "tsx", "java", "kt", "py", "cpp", "c", "h", "cs", "php", 
            "rb", "go", "rs", "swift", "css", "scss", "sass", "html", "htm", "xml", 
            "json", "yaml", "yml", "sql", "sh", "bat", "ps1", "dockerfile", "gradle",
            "groovy", "scala", "clj", "elm", "dart", "vue", "svelte",
            // Markdown files
            "md", "markdown", "mdown", "mkd",
            // Config files
            "properties", "ini", "cfg", "conf", "toml",
            "gitignore", "makefile", "readme", "license", "changelog"
        )
    }
}