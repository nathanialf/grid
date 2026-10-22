package com.defnf.grid.presentation.screens.connections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Security
import androidx.compose.ui.graphics.vector.ImageVector
import com.defnf.grid.domain.model.Protocol

/** The icon that stands for a protocol on connection cards and in the editor. */
internal val Protocol.icon: ImageVector
    get() = when (this) {
        Protocol.SFTP -> Icons.Default.Security
        Protocol.SMB -> Icons.Default.Computer
    }

/** The protocol's full name, for supporting text. */
internal val Protocol.description: String
    get() = when (this) {
        Protocol.SFTP -> "SSH File Transfer Protocol"
        Protocol.SMB -> "Server Message Block"
    }
