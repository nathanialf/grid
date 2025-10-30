package com.defnf.grid.presentation.fileviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.defnf.grid.presentation.theme.GridTheme
import com.defnf.grid.presentation.fileviewer.composables.*
import com.defnf.grid.presentation.fileviewer.utils.FileUtils
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class FileViewerActivity : ComponentActivity() {
    
    private val viewModel: FileViewerViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val filePath = intent.getStringExtra("file_path") ?: return finish()
        val fileName = intent.getStringExtra("file_name") ?: "Unknown File"
        val fileType = intent.getStringExtra("file_type") ?: "UNKNOWN"
        val connectionId = intent.getStringExtra("connection_id")
        val remotePath = intent.getStringExtra("remote_path")
        setContent {
            GridTheme {
                FileViewerScreen(
                    filePath = filePath,
                    fileName = fileName,
                    fileType = fileType,
                    connectionId = connectionId,
                    remotePath = remotePath,
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }
    
    override fun onDestroy() {
        // Clean up MediaControllers when activity is destroyed
        // This prevents service connection leaks
        MediaControllerManager.releaseAll()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    filePath: String,
    fileName: String,
    fileType: String,
    connectionId: String?,
    remotePath: String?,
    viewModel: FileViewerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val file = File(filePath)
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    var editorActions by remember { mutableStateOf<TextEditorActions?>(null) }
    var archiveActions by remember { mutableStateOf<ArchiveViewerActions?>(null) }
    
    // Keep screen on during extraction and upload operations
    DisposableEffect(uiState.isExtracting, uiState.isUploading) {
        val window = (context as? android.app.Activity)?.window
        if (uiState.isExtracting || uiState.isUploading) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    // Handle extraction completion
    LaunchedEffect(uiState.extractionComplete) {
        if (uiState.extractionComplete) {
            (context as? android.app.Activity)?.let { activity ->
                activity.setResult(android.app.Activity.RESULT_OK)
                activity.finish()
            }
        }
    }
    
    // Error dialog
    uiState.saveError?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearSaveError() },
            title = { Text("Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearSaveError() }) {
                    Text("OK")
                }
            }
        )
    }
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            FileViewerTopBar(
                fileName = fileName,
                fileType = fileType,
                canEdit = FileUtils.isEditableFile(file),
                isEditMode = uiState.isEditMode,
                canSave = editorActions?.hasUnsavedChanges == true,
                isSaving = editorActions?.isSaving == true,
                canExtract = archiveActions?.canExtract == true,
                isExtracting = uiState.isExtracting || uiState.isUploading,
                extractionProgress = if (uiState.isUploading) {
                    uiState.uploadProgress
                } else {
                    uiState.extractionProgress?.let { progress ->
                        if (progress.totalFiles > 0) {
                            progress.filesProcessed.toFloat() / progress.totalFiles.toFloat()
                        } else -1f
                    } ?: -1f
                },
                onBack = onBack,
                onEdit = { viewModel.enterEditMode() },
                onSave = { 
                    editorActions?.save?.invoke()
                },
                onExitEdit = {
                    if (editorActions?.hasUnsavedChanges == true) {
                        editorActions?.exit?.invoke()
                    } else {
                        viewModel.exitEditMode()
                    }
                },
                onExtract = {
                    viewModel.extractArchive(file, fileName, connectionId, remotePath)
                }
            )
        }
    ) { innerPadding ->
        FileContentRouter(
            file = file,
            fileType = fileType,
            isEditMode = uiState.isEditMode,
            fileName = fileName,
            connectionId = connectionId,
            remotePath = remotePath,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            onEditorActionsChanged = { editorActions = it },
            onArchiveActionsChanged = { archiveActions = it },
            onSaveFile = { content ->
                viewModel.saveFile(file, content, connectionId, remotePath)
            },
            onExitEditor = {
                viewModel.exitEditMode()
            }
        )
    }
}