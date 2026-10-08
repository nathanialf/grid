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
import java.io.File

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

/**
 * Hands a downloaded APK to the system package installer. The installer is launched as soon as
 * the screen opens; if Grid isn't yet allowed to install unknown apps (Android 8+), the user is
 * asked to grant that in Settings first and the install resumes when they come back.
 */
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
            title = { Text("Allow app installs") },
            text = {
                Text("To install ${file.name}, allow Grid to install unknown apps in Settings, then come back.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    settingsLauncher.launch(unknownSourcesSettingsIntent(context))
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("Cancel") }
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
        Icon(
            imageVector = Icons.Default.Android,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = file.name,
            style = MaterialTheme.typography.titleMedium,
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

        Button(onClick = { install() }) {
            Text("Install")
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
