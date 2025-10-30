package com.defnf.grid.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.defnf.grid.domain.model.ThemeMode
import com.defnf.grid.presentation.screens.settings.SettingsViewModel

@Composable
fun GridThemeProvider(
    content: @Composable (darkTheme: Boolean) -> Unit
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settingsState by settingsViewModel.uiState.collectAsState()
    
    val darkTheme = when (settingsState.themeMode.uppercase()) {
        "LIGHT" -> false
        "DARK" -> true
        "SYSTEM" -> isSystemInDarkTheme()
        else -> isSystemInDarkTheme()
    }
    
    content(darkTheme)
}