package com.defnf.grid.presentation.fileviewer.composables

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.defnf.grid.presentation.components.ShapedIcon
import java.io.File

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UnsupportedFileViewer(
    file: File,
    modifier: Modifier = Modifier,
    displayName: String = file.name
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ShapedIcon(
            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
            shape = MaterialShapes.Cookie9Sided.toShape(),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(128.dp),
            iconSize = 56.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Grid can't preview this file",
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "File size: ${formatFileSize(file.length())}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Hand the file to another app rather than leaving the user at a dead end
        FilledTonalButton(
            onClick = { openWithAnotherApp(context, file, displayName) },
            shapes = ButtonDefaults.shapes()
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text("Open with…")
        }
    }
}

private fun openWithAnotherApp(context: Context, file: File, displayName: String) {
    val extension = displayName.substringAfterLast('.', "").lowercase()
    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, displayName))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    } catch (e: IllegalArgumentException) {
        // The file isn't under a FileProvider path (e.g. not in Downloads/Grid)
        Toast.makeText(context, "This file can't be shared", Toast.LENGTH_SHORT).show()
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kiloBytes = bytes / 1024.0
    if (kiloBytes < 1024) return "%.1f KB".format(kiloBytes)
    val megaBytes = kiloBytes / 1024.0
    if (megaBytes < 1024) return "%.1f MB".format(megaBytes)
    val gigaBytes = megaBytes / 1024.0
    return "%.1f GB".format(gigaBytes)
}
