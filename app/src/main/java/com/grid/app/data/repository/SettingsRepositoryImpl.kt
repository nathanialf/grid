package com.grid.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.grid.app.domain.model.UserSettings
import com.grid.app.domain.model.ViewMode
import com.grid.app.domain.model.ThemeMode
import com.grid.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    companion object {
        private val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        private val SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
        private val DEFAULT_VIEW_MODE = stringPreferencesKey("default_view_mode")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    override fun getSettings(): Flow<UserSettings> {
        return dataStore.data.map { preferences ->
            val settings = UserSettings(
                biometricEnabled = preferences[BIOMETRIC_ENABLED] ?: false,
                showHiddenFiles = preferences[SHOW_HIDDEN_FILES] ?: false,
                defaultViewMode = ViewMode.valueOf(preferences[DEFAULT_VIEW_MODE] ?: "LIST"),
                themeMode = ThemeMode.valueOf(preferences[THEME_MODE] ?: "SYSTEM")
            )
            println("SettingsRepository: Retrieved settings from DataStore: $settings")
            settings
        }
    }

    override suspend fun updateSettings(settings: UserSettings) {
        println("SettingsRepository: Saving settings to DataStore: $settings")
        dataStore.edit { preferences ->
            preferences[BIOMETRIC_ENABLED] = settings.biometricEnabled
            preferences[SHOW_HIDDEN_FILES] = settings.showHiddenFiles
            preferences[DEFAULT_VIEW_MODE] = settings.defaultViewMode.name
            preferences[THEME_MODE] = settings.themeMode.name
            println("SettingsRepository: Settings saved to DataStore successfully")
        }
    }

    override suspend fun getBiometricEnabled(): Boolean {
        return dataStore.data.first()[BIOMETRIC_ENABLED] ?: false
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BIOMETRIC_ENABLED] = enabled
        }
    }

    override suspend fun getShowHiddenFiles(): Boolean {
        return dataStore.data.first()[SHOW_HIDDEN_FILES] ?: false
    }

    override suspend fun setShowHiddenFiles(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_HIDDEN_FILES] = show
        }
    }

    override suspend fun getDefaultViewMode(): String {
        return dataStore.data.first()[DEFAULT_VIEW_MODE] ?: "LIST"
    }

    override suspend fun setDefaultViewMode(viewMode: String) {
        dataStore.edit { preferences ->
            preferences[DEFAULT_VIEW_MODE] = viewMode
        }
    }

    override suspend fun getThemeMode(): String {
        return dataStore.data.first()[THEME_MODE] ?: "SYSTEM"
    }

    override suspend fun setThemeMode(theme: String) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE] = theme
        }
    }
}