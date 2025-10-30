package com.defnf.grid.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RemoteFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val permissions: String? = null,
    val isHidden: Boolean = false,
    val mimeType: String? = null
) {
    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "")
    
    val nameWithoutExtension: String
        get() = if (isDirectory) name else name.substringBeforeLast('.', name)
}