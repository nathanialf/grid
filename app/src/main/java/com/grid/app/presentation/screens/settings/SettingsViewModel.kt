package com.grid.app.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.app.domain.usecase.settings.GetSettingsUseCase
import com.grid.app.domain.usecase.settings.UpdateSettingsUseCase
import com.grid.app.domain.model.ViewMode
import com.grid.app.domain.model.ThemeMode
import com.grid.app.data.local.BiometricManager
import androidx.fragment.app.FragmentActivity
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
    private val updateSettingsUseCase: UpdateSettingsUseCase,
    private val biometricManager: BiometricManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            println("SettingsViewModel: Loading settings...")
            _uiState.value = _uiState.value.copy(isLoading = true)

            getSettingsUseCase()
                .catch { exception ->
                    println("SettingsViewModel: Error loading settings: ${exception.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Failed to load settings"
                    )
                }
                .collect { settings ->
                    println("SettingsViewModel: Loaded settings - biometric: ${settings.biometricEnabled}, hidden: ${settings.showHiddenFiles}, viewMode: ${settings.defaultViewMode}, theme: ${settings.themeMode}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        biometricEnabled = settings.biometricEnabled,
                        showHiddenFiles = settings.showHiddenFiles,
                        defaultViewMode = settings.defaultViewMode.name.lowercase(),
                        themeMode = settings.themeMode.name.lowercase(),
                        error = null
                    )
                }
        }
    }

    fun updateBiometricEnabled(enabled: Boolean, activity: FragmentActivity? = null) {
        println("SettingsViewModel: Updating biometric enabled to: $enabled")
        
        if (enabled && activity != null && biometricManager.isBiometricAvailable()) {
            // Require authentication before enabling biometrics
            viewModelScope.launch {
                try {
                    val result = biometricManager.authenticate(activity)
                    result.fold(
                        onSuccess = {
                            // Authentication successful, enable biometrics
                            updateSetting { copy(biometricEnabled = true) }
                            _uiState.value = _uiState.value.copy(biometricEnabled = true)
                        },
                        onFailure = { exception ->
                            // Authentication failed, don't enable biometrics
                            println("SettingsViewModel: Biometric authentication failed: ${exception.message}")
                            _uiState.value = _uiState.value.copy(
                                error = "Biometric authentication failed: ${exception.message}"
                            )
                        }
                    )
                } catch (e: Exception) {
                    println("SettingsViewModel: Biometric authentication error: ${e.message}")
                    _uiState.value = _uiState.value.copy(
                        error = "Biometric authentication error: ${e.message}"
                    )
                }
            }
        } else {
            // Disabling biometrics or no activity provided, update directly
            updateSetting { copy(biometricEnabled = enabled) }
            _uiState.value = _uiState.value.copy(biometricEnabled = enabled)
        }
    }

    fun updateShowHiddenFiles(show: Boolean) {
        println("SettingsViewModel: Updating show hidden files to: $show")
        updateSetting { copy(showHiddenFiles = show) }
        _uiState.value = _uiState.value.copy(showHiddenFiles = show)
    }

    fun updateDefaultViewMode(viewMode: String) {
        println("SettingsViewModel: Updating default view mode to: $viewMode")
        updateSetting { copy(defaultViewMode = ViewMode.valueOf(viewMode.uppercase())) }
        _uiState.value = _uiState.value.copy(defaultViewMode = viewMode)
    }

    fun updateThemeMode(theme: String) {
        println("SettingsViewModel: Updating theme mode to: $theme")
        updateSetting { copy(themeMode = ThemeMode.valueOf(theme.uppercase())) }
        _uiState.value = _uiState.value.copy(themeMode = theme)
    }

    private fun updateSetting(update: com.grid.app.domain.model.UserSettings.() -> com.grid.app.domain.model.UserSettings) {
        viewModelScope.launch {
            try {
                val currentSettings = getCurrentSettingsFromState()
                println("SettingsViewModel: Current settings before update: $currentSettings")
                val updatedSettings = currentSettings.update()
                println("SettingsViewModel: Updated settings: $updatedSettings")
                updateSettingsUseCase(updatedSettings)
                println("SettingsViewModel: Settings saved successfully")
            } catch (exception: Exception) {
                println("SettingsViewModel: Error saving settings: ${exception.message}")
                exception.printStackTrace()
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
            defaultViewMode = ViewMode.valueOf(state.defaultViewMode.uppercase()),
            themeMode = ThemeMode.valueOf(state.themeMode.uppercase())
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