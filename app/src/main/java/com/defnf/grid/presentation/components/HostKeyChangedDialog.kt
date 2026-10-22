package com.defnf.grid.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.defnf.grid.domain.model.HostKeyChangedException

/**
 * Shown when an SFTP server presents a different host key than the one Grid trusted before.
 * Cancel is the safe default; trusting is styled as the destructive choice.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HostKeyChangedDialog(
    change: HostKeyChangedException,
    onTrust: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Server identity changed") },
        text = {
            Column {
                Text(
                    "${change.host}:${change.port} presented a different key than last time. " +
                        "This is expected if the server was reinstalled or its SSH keys were " +
                        "regenerated. Otherwise, someone may be intercepting the connection."
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "New fingerprint",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SelectionContainer {
                    Text(
                        change.fingerprint,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Cancel") }
        },
        dismissButton = {
            TextButton(
                onClick = onTrust,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Trust new key")
            }
        }
    )
}
