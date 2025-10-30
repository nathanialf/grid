package com.defnf.grid.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Connection(
    val id: String,
    val name: String,
    val hostname: String,
    val protocol: Protocol,
    val port: Int? = null,
    val credentialId: String,
    val shareName: String? = null, // For SMB connections
    val startingDirectory: String? = null, // Starting directory path
    val createdAt: Long = System.currentTimeMillis(),
    val lastConnectedAt: Long? = null,
    val order: Int = 0 // For custom ordering
) {
    val effectivePort: Int
        get() = port ?: protocol.defaultPort
}