package com.defnf.grid.presentation.fileviewer.composables

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.defnf.grid.presentation.components.ShapedIcon
import java.io.File

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

/**
 * Hands a downloaded APK to the system package installer. The installer is launched as soon as
 * the screen opens; if Grid isn't yet allowed to install unknown apps (Android 8+), the user is
 * asked to grant that in Settings first and the install resumes when they come back.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ApkInstaller(
    file: File,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showPermissionDialog by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Only auto-launch once, not again on rotation or when returning from the installer.
    var autoLaunched by rememberSaveable { mutableStateOf(false) }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (canInstallPackages(context)) {
            error = launchInstaller(context, file)
        }
    }

    fun install() {
        if (canInstallPackages(context)) {
            error = launchInstaller(context, file)
        } else {
            showPermissionDialog = true
        }
    }

    LaunchedEffect(Unit) {
        if (!autoLaunched) {
            autoLaunched = true
            install()
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            icon = { Icon(Icons.Default.Security, contentDescription = null) },
            title = { Text("Allow app installs") },
            text = {
                Text("To install ${file.name}, allow Grid to install unknown apps in Settings, then come back.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        settingsLauncher.launch(unknownSourcesSettingsIntent(context))
                    },
                    shapes = ButtonDefaults.shapes()
                ) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ShapedIcon(
            imageVector = Icons.Default.Android,
            shape = MaterialShapes.Cookie9Sided.toShape(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(128.dp),
            iconSize = 56.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = file.name,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center
        )

        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        val buttonHeight = ButtonDefaults.MediumContainerHeight
        Button(
            onClick = { install() },
            shapes = ButtonDefaults.shapesFor(buttonHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight, hasStartIcon = true),
            modifier = Modifier.heightIn(min = buttonHeight)
        ) {
            Icon(
                imageVector = Icons.Default.InstallMobile,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight))
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(buttonHeight)))
            Text("Install", style = ButtonDefaults.textStyleFor(buttonHeight))
        }
    }
}

private fun canInstallPackages(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

private fun unknownSourcesSettingsIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    }

/** Launches the system installer for [file]; returns an error message on failure. */
private fun launchInstaller(context: Context, file: File): String? {
    return try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        null
    } catch (e: ActivityNotFoundException) {
        "No app installer available on this device"
    } catch (e: IllegalArgumentException) {
        "Can't share this file with the installer: ${e.message}"
    }
}
