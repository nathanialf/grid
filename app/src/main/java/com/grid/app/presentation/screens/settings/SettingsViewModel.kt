package com.grid.app.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.app.domain.usecase.settings.GetSettingsUseCase
import com.grid.app.domain.usecase.settings.UpdateSettingsUseCase
import com.grid.app.domain.model.ViewMode
import com.grid.app.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getSettingsUseCase: GetSettingsUseCase,
    private val updateSettingsUseCase: UpdateSettingsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            getSettingsUseCase()
                .catch { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Failed to load settings"
                    )
                }
                .collect { settings ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        biometricEnabled = settings.biometricEnabled,
                        showHiddenFiles = settings.showHiddenFiles,
                        defaultViewMode = settings.defaultViewMode.name,
                        themeMode = settings.themeMode.name,
                        error = null
                    )
                }
        }
    }

    fun updateBiometricEnabled(enabled: Boolean) {
        updateSetting { copy(biometricEnabled = enabled) }
        _uiState.value = _uiState.value.copy(biometricEnabled = enabled)
    }

    fun updateShowHiddenFiles(show: Boolean) {
        updateSetting { copy(showHiddenFiles = show) }
        _uiState.value = _uiState.value.copy(showHiddenFiles = show)
    }

    fun updateDefaultViewMode(viewMode: String) {
        updateSetting { copy(defaultViewMode = ViewMode.valueOf(viewMode)) }
        _uiState.value = _uiState.value.copy(defaultViewMode = viewMode)
    }

    fun updateThemeMode(theme: String) {
        updateSetting { copy(themeMode = ThemeMode.valueOf(theme)) }
        _uiState.value = _uiState.value.copy(themeMode = theme)
    }

    private fun updateSetting(update: com.grid.app.domain.model.UserSettings.() -> com.grid.app.domain.model.UserSettings) {
        viewModelScope.launch {
            try {
                val currentSettings = getCurrentSettingsFromState()
                val updatedSettings = currentSettings.update()
                updateSettingsUseCase(updatedSettings)
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = exception.message ?: "Failed to update setting"
                )
            }
        }
    }

    private fun getCurrentSettingsFromState(): com.grid.app.domain.model.UserSettings {
        val state = _uiState.value
        return com.grid.app.domain.model.UserSettings(
            biometricEnabled = state.biometricEnabled,
            showHiddenFiles = state.showHiddenFiles,
            defaultViewMode = ViewMode.valueOf(state.defaultViewMode),
            themeMode = ThemeMode.valueOf(state.themeMode)
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class SettingsUiState(
    val isLoading: Boolean = false,
    val biometricEnabled: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val defaultViewMode: String = "list",
    val themeMode: String = "system",
    val error: String? = null
)