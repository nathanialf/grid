package com.defnf.grid.presentation.fileviewer.composables

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
import com.defnf.grid.presentation.components.WavyCircularProgressIndicator

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    // Lets a viewer's own backdrop (e.g. the audio player's album-art gradient) show through
    transparent: Boolean = false,
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
        "APK" -> "App"
        else -> "File"
    }
    
    TopAppBar(
        title = {
            Text(
                text = viewerType,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        },
        subtitle = {
            Text(
                text = fileName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        },
        titleHorizontalAlignment = Alignment.CenterHorizontally,
        colors = if (transparent) {
            TopAppBarDefaults.topAppBarColors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent
            )
        } else {
            TopAppBarDefaults.topAppBarColors()
        },
        navigationIcon = {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
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
                    FilledIconButton(
                        onClick = onSave,
                        enabled = !isSaving,
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        if (isSaving) {
                            WavyCircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = LocalContentColor.current
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save"
                            )
                        }
                    }
                    
                    // Exit edit button
                    IconButton(onClick = onExitEdit, shapes = IconButtonDefaults.shapes()) {
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
                        enabled = !isExtracting,
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        if (isExtracting) {
                            WavyCircularProgressIndicator(
                                progress = extractionProgress,
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary
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
                    IconButton(onClick = onEdit, shapes = IconButtonDefaults.shapes()) {
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