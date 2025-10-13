package com.grid.app.domain.model

data class ConnectionSession(
    val connectionId: String,
    val connection: Connection,
    val currentPath: String = "/",
    val isConnected: Boolean = false,
    val lastActivity: Long = System.currentTimeMillis(),
    val tabId: String = connectionId
)