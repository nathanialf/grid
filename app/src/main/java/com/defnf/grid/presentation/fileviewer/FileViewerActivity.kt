package com.defnf.grid.presentation.fileviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.defnf.grid.presentation.theme.GridTheme
import com.defnf.grid.presentation.theme.GridThemeProvider
import com.defnf.grid.presentation.fileviewer.composables.*
import com.defnf.grid.presentation.fileviewer.utils.FileUtils
import com.defnf.grid.presentation.fileviewer.utils.animateColorScheme
import com.defnf.grid.presentation.fileviewer.utils.seedColorScheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class FileViewerActivity : ComponentActivity() {
    
    private val viewModel: FileViewerViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Media can be streamed from the server (grid:// URI) instead of opened from a local copy
        val streamUri = intent.getStringExtra("stream_uri")
        val filePath = intent.getStringExtra("file_path") ?: if (streamUri != null) "" else return finish()
        val fileName = intent.getStringExtra("file_name") ?: "Unknown File"
        val fileType = intent.getStringExtra("file_type") ?: "UNKNOWN"
        val connectionId = intent.getStringExtra("connection_id")
        val remotePath = intent.getStringExtra("remote_path")
        setContent {
            // Follow the theme chosen in Settings, same as MainActivity
            GridThemeProvider { darkTheme ->
                GridTheme(darkTheme = darkTheme) {
                    FileViewerScreen(
                        filePath = filePath,
                        fileName = fileName,
                        fileType = fileType,
                        connectionId = connectionId,
                        remotePath = remotePath,
                        streamUri = streamUri,
                        viewModel = viewModel,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
    
    override fun onDestroy() {
        // Clean up MediaControllers when activity is destroyed
        // This prevents service connection leaks. Keep them across rotation so playback
        // UI reattaches instantly instead of reconnecting to the session.
        if (!isChangingConfigurations) {
            MediaControllerManager.releaseAll()
        }
        super.onDestroy()
    }
}

private val EDITABLE_TYPES = setOf("TEXT", "CODE", "MARKDOWN")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    filePath: String,
    fileName: String,
    fileType: String,
    connectionId: String?,
    remotePath: String?,
    streamUri: String?,
    viewModel: FileViewerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val file = File(filePath)
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    var editorActions by remember { mutableStateOf<TextEditorActions?>(null) }
    var archiveActions by remember { mutableStateOf<ArchiveViewerActions?>(null) }

    // Media viewers (the audio player's album art) can re-theme the whole screen from a seed color
    var themeSeed by remember { mutableStateOf<Color?>(null) }
    val baseColorScheme = MaterialTheme.colorScheme
    val isDarkTheme = baseColorScheme.surface.luminance() < 0.5f
    val targetColorScheme = remember(themeSeed, isDarkTheme, baseColorScheme) {
        themeSeed?.let { seedColorScheme(it, isDarkTheme) } ?: baseColorScheme
    }
    val isAudio = fileType == "AUDIO"

    val exitEditor: () -> Unit = {
        if (editorActions?.hasUnsavedChanges == true) {
            editorActions?.exit?.invoke()
        } else {
            viewModel.exitEditMode()
        }
    }
    // While editing, back leaves the editor (through the unsaved-changes prompt) rather than
    // closing the viewer and silently dropping edits.
    BackHandler(enabled = uiState.isEditMode, onBack = exitEditor)
    
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
    
    MaterialTheme(colorScheme = animateColorScheme(targetColorScheme)) {
        val colors = MaterialTheme.colorScheme
        Box(
            modifier = modifier
                .fillMaxSize()
                .then(
                    if (isAudio) {
                        Modifier.background(
                            Brush.verticalGradient(listOf(colors.primaryContainer, colors.surface))
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                // Audio paints its own album-art tinted backdrop behind the bars
                containerColor = if (isAudio) Color.Transparent else MaterialTheme.colorScheme.background,
                topBar = {
                    FileViewerTopBar(
                        fileName = fileName,
                        fileType = fileType,
                        // Only these routes have an editor (streams have no local file at all)
                        canEdit = fileType in EDITABLE_TYPES && FileUtils.isEditableFile(File(fileName)),
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
                        transparent = isAudio,
                        onBack = if (uiState.isEditMode) exitEditor else onBack,
                        onEdit = { viewModel.enterEditMode() },
                        onSave = { 
                            editorActions?.save?.invoke()
                        },
                        onExitEdit = exitEditor,
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
                    streamUri = streamUri,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    onEditorActionsChanged = { editorActions = it },
                    onArchiveActionsChanged = { archiveActions = it },
                    onThemeSeedChanged = { themeSeed = it },
                    onSaveFile = { content ->
                        viewModel.saveFile(file, content, connectionId, remotePath)
                    },
                    onExitEditor = {
                        viewModel.exitEditMode()
                    }
                )
            }
        }
    }
}