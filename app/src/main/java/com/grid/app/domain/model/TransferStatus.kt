package com.grid.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class TransferState {
    PENDING,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Serializable
data class TransferProgress(
    val bytesTransferred: Long,
    val totalBytes: Long,
    val speed: Long = 0L, // bytes per second
    val timeRemaining: Long = 0L // milliseconds
) {
    val progressPercent: Float
        get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes) * 100f else 0f
}

@Serializable
data class FileTransfer(
    val id: String,
    val localPath: String,
    val remotePath: String,
    val fileName: String,
    val isUpload: Boolean,
    val connectionId: String,
    val state: TransferState = TransferState.PENDING,
    val progress: TransferProgress = TransferProgress(0, 0),
    val errorMessage: String? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null
)