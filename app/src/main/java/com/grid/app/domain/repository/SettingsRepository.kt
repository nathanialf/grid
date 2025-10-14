package com.grid.app.domain.repository

import com.grid.app.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettings(): Flow<UserSettings>
    suspend fun updateSettings(settings: UserSettings)
    suspend fun getBiometricEnabled(): Boolean
    suspend fun setBiometricEnabled(enabled: Boolean)
    suspend fun getShowHiddenFiles(): Boolean
    suspend fun setShowHiddenFiles(show: Boolean)
    suspend fun getDefaultViewMode(): String
    suspend fun setDefaultViewMode(viewMode: String)
    suspend fun getThemeMode(): String
    suspend fun setThemeMode(theme: String)
}