package com.defnf.grid.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UserSettings(
    val biometricEnabled: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val defaultViewMode: ViewMode = ViewMode.LIST,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

enum class ViewMode {
    LIST,
    GRID
}

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}