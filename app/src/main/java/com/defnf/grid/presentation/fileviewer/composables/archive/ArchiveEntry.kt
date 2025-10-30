package com.defnf.grid.presentation.fileviewer.composables.archive

import java.util.*

data class ArchiveEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val compressedSize: Long = 0L,
    val lastModified: Date? = null,
    val children: MutableList<ArchiveEntry> = mutableListOf()
) {
    val compressionRatio: Float
        get() = if (size > 0) {
            ((size - compressedSize).toFloat() / size.toFloat()) * 100f
        } else 0f
        
    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "")
}

enum class ArchiveFormat {
    ZIP,
    RAR,
    SEVEN_ZIP,
    TAR,
    TAR_GZ,
    UNKNOWN;
    
    companion object {
        fun fromFileName(fileName: String): ArchiveFormat {
            val extension = fileName.lowercase()
            return when {
                extension.endsWith(".zip") -> ZIP
                extension.endsWith(".rar") -> RAR
                extension.endsWith(".7z") -> SEVEN_ZIP
                extension.endsWith(".tar") -> TAR
                extension.endsWith(".tar.gz") || extension.endsWith(".tgz") -> TAR_GZ
                else -> UNKNOWN
            }
        }
    }
}