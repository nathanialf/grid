package com.grid.app.presentation.fileviewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import android.graphics.BitmapFactory
import com.grid.app.presentation.theme.GridTheme
import com.grid.app.presentation.components.LoadingView
import com.grid.app.presentation.fileviewer.composables.AudioPlayer
import com.grid.app.presentation.fileviewer.composables.TextViewer
import com.grid.app.presentation.fileviewer.composables.MarkdownViewer
import com.grid.app.presentation.fileviewer.composables.TextEditor
import com.grid.app.presentation.fileviewer.composables.TextEditorActions
import com.grid.app.presentation.fileviewer.utils.FileUtils
import com.grid.app.domain.usecase.file.UploadFileUseCase
import com.grid.app.domain.usecase.connection.GetConnectionUseCase
import com.grid.app.domain.model.Connection
import com.grid.app.domain.repository.FileRepository
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.widget.TextView
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@AndroidEntryPoint
class FileViewerActivity : ComponentActivity() {
    
    @Inject
    lateinit var uploadFileUseCase: UploadFileUseCase
    
    @Inject
    lateinit var getConnectionUseCase: GetConnectionUseCase
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val filePath = intent.getStringExtra("file_path") ?: ""
        val fileName = intent.getStringExtra("file_name") ?: "Unknown File"
        val fileType = intent.getStringExtra("file_type") ?: "TEXT"
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
                    uploadFileUseCase = uploadFileUseCase,
                    getConnectionUseCase = getConnectionUseCase,
                    onBack = { finish() }
                )
            }
        }
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
    uploadFileUseCase: UploadFileUseCase,
    getConnectionUseCase: GetConnectionUseCase,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val file = File(filePath)
    var isEditMode by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var editorActions by remember { mutableStateOf<TextEditorActions?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    // Save error dialog
    saveError?.let { error ->
        AlertDialog(
            onDismissRequest = { saveError = null },
            title = { Text("Save Error") },
            text = { Text("Failed to save file: $error") },
            confirmButton = {
                TextButton(onClick = { saveError = null }) {
                    Text("OK")
                }
            }
        )
    }
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(fileName) },
                navigationIcon = {
                    if (!isEditMode) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    if (isEditMode && editorActions != null) {
                        // Save button
                        IconButton(
                            onClick = editorActions!!.save,
                            enabled = editorActions!!.hasUnsavedChanges && !editorActions!!.isSaving
                        ) {
                            if (editorActions!!.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Save file",
                                    tint = if (editorActions!!.hasUnsavedChanges) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        
                        // Exit button
                        IconButton(onClick = editorActions!!.exit) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Exit editor",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else if (!isEditMode && FileUtils.isEditableFile(file)) {
                        // Edit button for editable files
                        IconButton(onClick = { isEditMode = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit file",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        FileViewerContent(
            filePath = filePath,
            fileType = fileType,
            modifier = Modifier.padding(innerPadding),
            isEditMode = isEditMode,
            onEditorActionsChanged = { editorActions = it },
            onSaveFile = { content ->
                coroutineScope.launch {
                    try {
                        // First save to local file
                        FileUtils.saveFile(file, content)
                            .onSuccess { 
                                // If we have connection info, upload to server
                                if (connectionId != null && remotePath != null) {
                                    try {
                                        // Get the connection object and upload to server
                                        val connection = getConnectionUseCase(connectionId)
                                        uploadFileUseCase(connection, filePath, remotePath)
                                        isEditMode = false
                                    } catch (e: Exception) {
                                        saveError = "Failed to upload to server: ${e.message}"
                                    }
                                } else {
                                    // No server connection, just local save
                                    isEditMode = false
                                }
                            }
                            .onFailure { error -> 
                                saveError = error.message 
                            }
                    } catch (e: Exception) {
                        saveError = "Save failed: ${e.message}"
                    }
                }
            },
            onExitEditor = {
                isEditMode = false
            }
        )
    }
}

@Composable
fun FileViewerContent(
    filePath: String,
    fileType: String,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    onEditorActionsChanged: (TextEditorActions) -> Unit = {},
    onSaveFile: (String) -> Unit = {},
    onExitEditor: () -> Unit = {}
) {
    val file = File(filePath)
    
    if (!file.exists()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("File not found")
                Text(
                    text = filePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    
    when (fileType) {
        "IMAGE" -> {
            ImageViewer(
                file = file,
                modifier = modifier
            )
        }
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
                TextViewer(
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
        "PDF" -> {
            PdfViewer(
                file = file,
                modifier = modifier
            )
        }
        "AUDIO" -> {
            AudioPlayer(
                file = file,
                modifier = modifier
            )
        }
        "VIDEO" -> {
            VideoPlayer(
                file = file,
                modifier = modifier
            )
        }
        else -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Unsupported file type: $fileType")
            }
        }
    }
}

@Composable
private fun ImageViewer(
    file: File,
    modifier: Modifier = Modifier
) {
    // Zoom and pan state
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f)
        offset += panChange
    }
    
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = file,
            contentDescription = file.name,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .transformable(transformableState)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = 1f
                            offset = Offset.Zero
                        }
                    )
                },
            contentScale = ContentScale.Fit
        )
    }
}


@Composable
private fun PdfViewer(
    file: File,
    modifier: Modifier = Modifier
) {
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var currentPage by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Zoom and pan state for PDF pages
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f)
        offset += panChange
    }
    
    // Reset zoom when page changes
    LaunchedEffect(currentPage) {
        scale = 1f
        offset = Offset.Zero
    }

    // Initialize PDF renderer
    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fileDescriptor)
                
                withContext(Dispatchers.Main) {
                    pdfRenderer = renderer
                    totalPages = renderer.pageCount
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = "Error opening PDF: ${e.message}"
                    isLoading = false
                }
            }
        }
    }

    // Render current page
    LaunchedEffect(pdfRenderer, currentPage) {
        pdfRenderer?.let { renderer ->
            withContext(Dispatchers.IO) {
                try {
                    val page = renderer.openPage(currentPage)
                    
                    // Use higher resolution for better text rendering
                    val scaleFactor = 3f
                    val width = (page.width * scaleFactor).toInt()
                    val height = (page.height * scaleFactor).toInt()
                    
                    val bitmap = Bitmap.createBitmap(
                        width,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    
                    // Set bitmap density for better text rendering
                    bitmap.density = android.util.DisplayMetrics.DENSITY_XHIGH
                    
                    // Fill bitmap with white background to avoid transparency issues
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    
                    // Create transform matrix for higher resolution
                    val matrix = android.graphics.Matrix()
                    matrix.setScale(scaleFactor, scaleFactor)
                    
                    // Try RENDER_MODE_FOR_PRINT first for better text quality
                    try {
                        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    } catch (printException: Exception) {
                        // Fallback to display mode if print mode fails
                        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                    
                    page.close()
                    
                    withContext(Dispatchers.Main) {
                        pageBitmap = bitmap
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        error = "Error rendering page: ${e.message}"
                    }
                }
            }
        }
    }

    // Cleanup
    DisposableEffect(file) {
        onDispose {
            pdfRenderer?.close()
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // PDF Controls
        if (totalPages > 1) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (currentPage > 0) currentPage-- },
                        enabled = currentPage > 0
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "Previous page"
                        )
                    }
                    
                    Text(
                        text = "Page ${currentPage + 1} of $totalPages",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    IconButton(
                        onClick = { if (currentPage < totalPages - 1) currentPage++ },
                        enabled = currentPage < totalPages - 1
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Next page"
                        )
                    }
                }
            }
        }

        // PDF Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator()
                }
                error != null -> {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                pageBitmap != null -> {
                    Image(
                        bitmap = pageBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page ${currentPage + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                            .transformable(transformableState)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        scale = 1f
                                        offset = Offset.Zero
                                    }
                                )
                            },
                        contentScale = ContentScale.Fit
                    )
                }
                else -> {
                    Text("No content to display")
                }
            }
        }
    }
}





@Composable
private fun VideoPlayer(
    file: File,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Initialize ExoPlayer
    LaunchedEffect(file) {
        try {
            val player = ExoPlayer.Builder(context).build()
            val mediaItem = MediaItem.fromUri(file.toURI().toString())
            player.setMediaItem(mediaItem)
            player.prepare()
            
            exoPlayer = player
            isLoading = false
        } catch (e: Exception) {
            error = "Error loading video: ${e.message}"
            isLoading = false
        }
    }

    // Cleanup
    DisposableEffect(file) {
        onDispose {
            exoPlayer?.release()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator()
            }
            error != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Video error",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
            exoPlayer != null -> {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        PlayerView(context).apply {
                            player = exoPlayer
                            useController = true
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        }
                    }
                )
            }
            else -> {
                Text("No video content to display")
            }
        }
    }
}



