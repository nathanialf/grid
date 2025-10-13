package com.grid.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class Protocol(val displayName: String, val defaultPort: Int) {
    FTP("FTP", 21),
    SFTP("SFTP", 22),
    SMB("SMB", 445);

    companion object {
        fun fromDisplayName(displayName: String): Protocol? {
            return entries.find { it.displayName == displayName }
        }
    }
}