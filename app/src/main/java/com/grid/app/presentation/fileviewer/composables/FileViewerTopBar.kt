package com.grid.app.presentation.fileviewer.composables

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grid.app.presentation.components.WavyCircularProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerTopBar(
    fileName: String,
    fileType: String? = null,
    canEdit: Boolean = false,
    isEditMode: Boolean = false,
    canSave: Boolean = false,
    isSaving: Boolean = false,
    canExtract: Boolean = false,
    isExtracting: Boolean = false,
    extractionProgress: Float = 0f,
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    onSave: () -> Unit = {},
    onExitEdit: () -> Unit = {},
    onExtract: () -> Unit = {}
) {
    val viewerType = when (fileType?.uppercase()) {
        "AUDIO" -> "Audio"
        "VIDEO" -> "Video"
        "IMAGE" -> "Image"
        "PDF" -> "PDF"
        "TEXT" -> if (isEditMode) "Text Editor" else "Text"
        "CODE" -> if (isEditMode) "Text Editor" else "Code"
        "MARKDOWN" -> if (isEditMode) "Text Editor" else "Markdown"
        "EBOOK" -> "Ebook"
        "ARCHIVE" -> "Archive"
        else -> "File"
    }
    
    CenterAlignedTopAppBar(
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = viewerType,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            when {
                isEditMode && canSave -> {
                    // Save button
                    IconButton(onClick = onSave, enabled = !isSaving) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    // Exit edit button
                    IconButton(onClick = onExitEdit) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit editor",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                canExtract -> {
                    // Extract button for archives
                    IconButton(
                        onClick = onExtract,
                        enabled = !isExtracting
                    ) {
                        if (isExtracting) {
                            WavyCircularProgressIndicator(
                                progress = extractionProgress,
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 4f
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = "Extract archive",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                canEdit && !isEditMode -> {
                    // Edit button for editable files
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit file",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    )
}