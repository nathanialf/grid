package com.defnf.grid.presentation.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Single source of truth for mapping a file name to its logical type and leading icon.
 * Shared by the file browser and the offline/cached files screen (and the thumbnail
 * component), replacing the per-screen copies that previously drifted apart.
 */
enum class FileType {
    TEXT, CODE, MARKDOWN, IMAGE, PDF, AUDIO, VIDEO, ARCHIVE, EBOOK, UNKNOWN
}

/** Extensions whose embedded album art can be shown as a thumbnail. */
val AUDIO_EXTENSIONS = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus")

/** Extensions whose pixels can be shown directly as a thumbnail. */
val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "bmp", "webp", "svg", "gif")

fun getFileType(fileName: String): FileType {
    val extension = fileName.substringAfterLast('.', "").lowercase()

    return when {
        extension in IMAGE_EXTENSIONS -> FileType.IMAGE
        extension == "pdf" -> FileType.PDF
        extension in AUDIO_EXTENSIONS -> FileType.AUDIO
        extension in setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts", "mts") -> FileType.VIDEO
        extension in setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "lz", "lzma") -> FileType.ARCHIVE
        extension in setOf("epub", "mobi", "azw", "azw3", "fb2", "lit") -> FileType.EBOOK
        extension in setOf("md", "markdown") -> FileType.MARKDOWN
        extension in setOf(
            "js", "ts", "jsx", "tsx", "java", "kt", "py", "cpp", "c", "h", "cs", "php",
            "rb", "go", "rs", "swift", "css", "scss", "sass", "html", "htm", "xml",
            "json", "yaml", "yml", "sql", "sh", "bat", "ps1", "dockerfile", "gradle",
            "groovy", "scala", "clj", "elm", "dart", "vue", "svelte"
        ) -> FileType.CODE
        extension in setOf(
            "txt", "properties", "ini", "cfg", "conf", "log", "csv", "toml",
            "gitignore", "makefile", "readme", "license", "changelog"
        ) -> FileType.TEXT
        extension.isEmpty() || extension == fileName.lowercase() -> FileType.TEXT
        else -> FileType.TEXT
    }
}

fun getFileIcon(fileName: String): ImageVector {
    val extension = fileName.substringAfterLast('.', "").lowercase()

    return when {
        extension == "pdf" -> Icons.Default.PictureAsPdf
        extension in IMAGE_EXTENSIONS -> Icons.Default.Image
        extension in AUDIO_EXTENSIONS -> Icons.Default.AudioFile
        extension in setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts", "mts") -> Icons.Filled.PlayArrow
        extension in setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "lz", "lzma") -> Icons.Default.Archive
        extension in setOf("epub", "mobi", "azw", "azw3", "fb2", "lit") -> Icons.AutoMirrored.Filled.MenuBook
        extension in setOf("md", "markdown") -> Icons.Default.Description
        extension in setOf(
            "json", "xml", "html", "htm", "css", "js", "ts", "java", "kt", "py",
            "cpp", "c", "h", "cs", "php", "rb", "go", "rs", "swift", "yml", "yaml", "toml",
            "properties", "sql", "sh", "bat", "dockerfile", "gitignore", "gradle", "makefile"
        ) -> Icons.Default.Code
        extension in setOf("txt", "ini", "cfg", "conf", "log", "csv", "readme") -> Icons.AutoMirrored.Filled.TextSnippet
        extension.isEmpty() -> Icons.AutoMirrored.Filled.TextSnippet
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}
