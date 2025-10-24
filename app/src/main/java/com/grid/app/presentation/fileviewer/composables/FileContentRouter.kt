package com.grid.app.presentation.fileviewer.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.io.File

@Composable
fun FileContentRouter(
    file: File,
    fileType: String,
    isEditMode: Boolean,
    modifier: Modifier = Modifier,
    fileName: String? = null,
    connectionId: String? = null,
    remotePath: String? = null,
    onEditorActionsChanged: (TextEditorActions) -> Unit = {},
    onArchiveActionsChanged: (ArchiveViewerActions) -> Unit = {},
    onSaveFile: (String) -> Unit = {},
    onExitEditor: () -> Unit = {}
) {
    when (fileType) {
        "TEXT" -> {
            if (isEditMode) {
                TextEditor(
                    file = file,
                    modifier = modifier,
                    onSave = onSaveFile,
                    onExit = onExitEditor,
                    onActionsChanged = onEditorActionsChanged
                )
            } else {
                TextViewer(
                    file = file,
                    modifier = modifier
                )
            }
        }
        
        "CODE" -> {
            if (isEditMode) {
                TextEditor(
                    file = file,
                    modifier = modifier,
                    onSave = onSaveFile,
                    onExit = onExitEditor,
                    onActionsChanged = onEditorActionsChanged
                )
            } else {
                CodeViewer(
                    file = file,
                    modifier = modifier
                )
            }
        }
        
        "MARKDOWN" -> {
            if (isEditMode) {
                TextEditor(
                    file = file,
                    modifier = modifier,
                    onSave = onSaveFile,
                    onExit = onExitEditor,
                    onActionsChanged = onEditorActionsChanged
                )
            } else {
                MarkdownViewer(
                    file = file,
                    modifier = modifier
                )
            }
        }
        
        "IMAGE" -> {
            ImageViewer(
                file = file,
                modifier = modifier
            )
        }
        
        "PDF" -> {
            PDFViewer(
                file = file,
                modifier = modifier
            )
        }
        
        "AUDIO" -> {
            AudioPlayer(
                file = file,
                fileName = fileName,
                connectionId = connectionId,
                remotePath = remotePath,
                modifier = modifier
            )
        }
        
        "VIDEO" -> {
            VideoPlayer(
                file = file,
                fileName = fileName,
                connectionId = connectionId,
                remotePath = remotePath,
                modifier = modifier
            )
        }
        
        "ARCHIVE" -> {
            ZipViewer(
                file = file,
                modifier = modifier,
                onActionsChanged = onArchiveActionsChanged
            )
        }
        
        "EBOOK" -> {
            EbookViewer(
                file = file,
                modifier = modifier
            )
        }
        
        else -> {
            UnsupportedFileViewer(
                file = file,
                modifier = modifier
            )
        }
    }
}