@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.defnf.grid.presentation.screens.settings

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.defnf.grid.BuildConfig
import com.defnf.grid.presentation.components.ShapedIcon
import com.defnf.grid.presentation.theme.GridTheme

/** One option of a connected button group: stored value, label and icon. */
private data class SettingsChoice(
    val value: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Failed saves and a failed biometric check used to vanish silently
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
        ) {
            SettingsSectionHeader("Security")
            SettingsSwitchItem(
                title = "Biometric unlock",
                subtitle = "Use fingerprint or face unlock to open Grid",
                checked = uiState.biometricEnabled,
                onCheckedChange = { enabled ->
                    if (context is FragmentActivity) {
                        viewModel.updateBiometricEnabled(enabled, context)
                    } else {
                        viewModel.updateBiometricEnabled(enabled)
                    }
                },
                icon = Icons.Default.Fingerprint,
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1)
            )

            SettingsSectionHeader("File browser")
            SettingsSwitchItem(
                title = "Show hidden files",
                subtitle = "Display files and folders that start with a dot",
                checked = uiState.showHiddenFiles,
                onCheckedChange = viewModel::updateShowHiddenFiles,
                icon = Icons.Default.Visibility,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2)
            )
            Spacer(modifier = Modifier.height(ListItemDefaults.SegmentedGap))
            SettingsChoiceItem(
                title = "Default view",
                subtitle = "How folders are shown when you open them",
                icon = Icons.Default.GridView,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                value = uiState.defaultViewMode,
                choices = listOf(
                    SettingsChoice("list", "List", Icons.AutoMirrored.Filled.ViewList),
                    SettingsChoice("grid", "Grid", Icons.Default.GridView)
                ),
                onValueChange = viewModel::updateDefaultViewMode,
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2)
            )

            SettingsSectionHeader("Appearance")
            SettingsChoiceItem(
                title = "Theme",
                subtitle = "Choose your preferred color scheme",
                icon = Icons.Default.Palette,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                value = uiState.themeMode,
                choices = listOf(
                    SettingsChoice("system", "System", Icons.Default.BrightnessAuto),
                    SettingsChoice("light", "Light", Icons.Default.LightMode),
                    SettingsChoice("dark", "Dark", Icons.Default.DarkMode)
                ),
                onValueChange = viewModel::updateThemeMode,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1)
            )

            SettingsSectionHeader("About")
            AboutCard(
                onOpenWebsite = {
                    val intent = Intent(Intent.ACTION_VIEW, "https://defnf.com".toUri())
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

/** The leading icon of a settings row: a small icon on an expressive cookie shape. */
@Composable
private fun SettingsIcon(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color
) {
    ShapedIcon(
        imageVector = icon,
        shape = MaterialShapes.Cookie4Sided.toShape(),
        containerColor = containerColor,
        contentColor = contentColor,
        modifier = Modifier.size(40.dp),
        iconSize = 22.dp
    )
}

/** Container tone for settings rows: the default segmented Surface vanishes on the Scaffold. */
@Composable
private fun settingsItemColors(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    // A toggleable row reports itself as selected while on; keep it the same tone
    selectedContainerColor = MaterialTheme.colorScheme.surfaceContainer
)

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    iconContainerColor: Color,
    iconContentColor: Color,
    shapes: ListItemShapes
) {
    // The whole row toggles; the switch only mirrors the state
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = shapes,
        colors = settingsItemColors(),
        leadingContent = { SettingsIcon(icon, iconContainerColor, iconContentColor) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                thumbContent = if (checked) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                } else {
                    null
                }
            )
        }
    ) {
        Text(title)
    }
}

/**
 * A settings row whose value is picked from a connected button group laid out under the title,
 * so every option is visible and one tap away.
 */
@Composable
private fun SettingsChoiceItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconContentColor: Color,
    value: String,
    choices: List<SettingsChoice>,
    onValueChange: (String) -> Unit,
    shapes: ListItemShapes
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shapes.shape,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsIcon(icon, iconContainerColor, iconContentColor)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ConnectedChoiceGroup(
                value = value,
                choices = choices,
                onValueChange = onValueChange
            )
        }
    }
}

/** A single-select connected button group spanning the full width. */
@Composable
private fun ConnectedChoiceGroup(
    value: String,
    choices: List<SettingsChoice>,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        choices.forEachIndexed { index, choice ->
            ToggleButton(
                checked = value == choice.value,
                onCheckedChange = { onValueChange(choice.value) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                icon = {
                    Icon(
                        imageVector = choice.icon,
                        contentDescription = null,
                        modifier = Modifier.size(ToggleButtonDefaults.IconSize)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton }
            ) {
                Text(
                    text = choice.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AboutCard(onOpenWebsite: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = ListItemDefaults.segmentedShapes(index = 0, count = 2).shape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShapedIcon(
                    imageVector = Icons.Default.GridView,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(64.dp),
                    iconSize = 32.dp
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Grid",
                        style = MaterialTheme.typography.headlineMediumEmphasized
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        SegmentedListItem(
            onClick = onOpenWebsite,
            shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
            colors = settingsItemColors(),
            leadingContent = {
                SettingsIcon(
                    icon = Icons.Default.Person,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            },
            supportingContent = { Text("defnf.com") },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open website"
                )
            }
        ) {
            Text("Developed by Nathanial Fine")
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
