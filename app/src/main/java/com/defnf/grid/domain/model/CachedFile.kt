package com.defnf.grid.domain.model

data class CachedFile(
    val name: String,
    val path: String,
    val remotePath: String,
    val size: Long,
    val cachedAt: Long,
    val connectionId: String
) {
    val extension: String
        get() = name.substringAfterLast('.', "")
}
