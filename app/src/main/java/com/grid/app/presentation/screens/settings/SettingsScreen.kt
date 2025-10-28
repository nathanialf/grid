@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.grid.app.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.grid.app.presentation.theme.GridTheme
import android.content.Intent
import android.net.Uri

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Security Section
            SettingsSection(title = "Security") {
                SettingsSwitchItem(
                    title = "Biometric Authentication",
                    subtitle = "Use fingerprint or face unlock for app access",
                    checked = uiState.biometricEnabled,
                    onCheckedChange = { enabled ->
                        if (context is FragmentActivity) {
                            viewModel.updateBiometricEnabled(enabled, context)
                        } else {
                            viewModel.updateBiometricEnabled(enabled)
                        }
                    },
                    icon = Icons.Default.Fingerprint
                )
            }
            
            // File Browser Section
            SettingsSection(title = "File Browser") {
                SettingsSwitchItem(
                    title = "Show Hidden Files",
                    subtitle = "Display files and folders that start with a dot",
                    checked = uiState.showHiddenFiles,
                    onCheckedChange = viewModel::updateShowHiddenFiles,
                    icon = Icons.Default.Visibility
                )
                
                SettingsDropdownItem(
                    title = "Default View Mode",
                    subtitle = "How files are displayed in the browser",
                    value = uiState.defaultViewMode,
                    options = listOf("list" to "List", "grid" to "Grid"),
                    onValueChange = viewModel::updateDefaultViewMode,
                    icon = Icons.AutoMirrored.Filled.ViewList
                )
            }
            
            // Appearance Section
            SettingsSection(title = "Appearance") {
                SettingsDropdownItem(
                    title = "Theme",
                    subtitle = "Choose your preferred color scheme",
                    value = uiState.themeMode,
                    options = listOf(
                        "system" to "Follow System",
                        "light" to "Light",
                        "dark" to "Dark"
                    ),
                    onValueChange = viewModel::updateThemeMode,
                    icon = Icons.Default.Palette
                )
            }
            
            // About Section
            SettingsSection(title = "About") {
                SettingsClickableItem(
                    title = "Developed by Nathanial Fine",
                    subtitle = "defnf.com",
                    icon = Icons.Default.Person,
                    onClick = { 
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://defnf.com"))
                        context.startActivity(intent)
                    }
                )
                
                SettingsInfoItem(
                    title = "Version",
                    subtitle = "1.0.0",
                    icon = Icons.Default.Info
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            
            content()
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsDropdownItem(
    title: String,
    subtitle: String,
    value: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = options.find { it.first == value }?.second ?: value
    
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            TextButton(
                onClick = { expanded = true }
            ) {
                Text(
                    text = selectedOption,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(x = (-48).dp, y = 0.dp)
        ) {
            options.forEach { (optionValue, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onValueChange(optionValue)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsClickableItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsInfoItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    GridTheme {
        SettingsScreen(
            onNavigateBack = {}
        )
    }
}