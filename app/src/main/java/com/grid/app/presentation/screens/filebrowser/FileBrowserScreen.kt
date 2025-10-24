@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.grid.app.presentation.screens.filebrowser

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import android.app.Activity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import com.grid.app.domain.model.RemoteFile
import com.grid.app.presentation.theme.GridTheme
import com.grid.app.presentation.components.WavyCircularProgressIndicator
import com.grid.app.presentation.components.ErrorView
import com.grid.app.presentation.components.EmptyDirectoryView
import com.grid.app.presentation.components.LoadingView
import com.grid.app.presentation.components.WavyLinearProgressIndicator
import java.text.SimpleDateFormat
import java.util.*
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Intent

@Composable
fun FileBrowserScreen(
    connectionId: String,
    onNavigateBack: () -> Unit,
    viewModel: FileBrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Rename dialog state
    var showRenameDialog by remember { mutableStateOf(false) }
    var fileToRename by remember { mutableStateOf<RemoteFile?>(null) }
    
    // Navigation debouncing
    var isNavigating by remember { mutableStateOf(false) }
    val debouncedNavigateBack = remember {
        {
            if (!isNavigating) {
                isNavigating = true
                onNavigateBack()
            }
        }
    }
    
    // Keep screen on during long operations
    val hasActiveOperations = uiState.downloadingFiles.isNotEmpty() || 
                              uiState.isUploading
    
    DisposableEffect(hasActiveOperations) {
        val window = (context as? android.app.Activity)?.window
        if (hasActiveOperations) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            // Get file name from content resolver
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: "uploaded_file"
            
            // Pass the URI directly to the ViewModel - it will handle the content resolution
            viewModel.uploadFile(uri, fileName)
        }
    }
    
    // File viewer activity result launcher
    val fileViewerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Refresh the file list when returning from file viewer (e.g., after archive extraction)
            viewModel.refresh()
        }
    }
    
    LaunchedEffect(connectionId) {
        viewModel.initialize(connectionId)
    }
    

    // Handle messages and errors
    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    BackHandler {
        if (!viewModel.handleBackNavigation()) {
            debouncedNavigateBack()
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        if (uiState.isSelectionMode) {
                            Text(
                                text = "${uiState.selectedFiles.size} selected",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatPath(uiState.currentPath, uiState.connectionName, uiState.protocol, uiState.shareName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = uiState.connectionName,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatPath(uiState.currentPath, uiState.connectionName, uiState.protocol, uiState.shareName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = debouncedNavigateBack
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = "Home"
                        )
                    }
                },
                actions = {
                    // Only show sort dropdown when not loading
                    if (!uiState.isLoading) {
                        // Sort dropdown
                        var expanded by remember { mutableStateOf(false) }
                        
                        Box {
                            IconButton(onClick = { expanded = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort"
                                )
                            }
                        
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            offset = DpOffset(0.dp, 0.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Name") },
                                onClick = {
                                    viewModel.setSortOption(SortOption.NAME)
                                    expanded = false
                                },
                                leadingIcon = {
                                    if (uiState.sortOption == SortOption.NAME) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Type") },
                                onClick = {
                                    viewModel.setSortOption(SortOption.TYPE)
                                    expanded = false
                                },
                                leadingIcon = {
                                    if (uiState.sortOption == SortOption.TYPE) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Last Modified") },
                                onClick = {
                                    viewModel.setSortOption(SortOption.LAST_MODIFIED)
                                    expanded = false
                                },
                                leadingIcon = {
                                    if (uiState.sortOption == SortOption.LAST_MODIFIED) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                        }
                    }
                    }
                }
            )
        },
        floatingActionButton = {
            // Hide the floating toolbar when uploading or loading to prevent UI clutter
            if (!uiState.isUploading && !uiState.isLoading) {
                Card(
                    modifier = Modifier.padding(bottom = 16.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.isSelectionMode) {
                            // Selection mode actions
                            // Clear selection
                            IconButton(
                                onClick = { viewModel.exitSelectionMode() },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear Selection",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            // Show rename button only when exactly 1 file is selected
                            if (uiState.selectedFiles.size == 1) {
                                IconButton(
                                    onClick = { 
                                        val selectedFilePath = uiState.selectedFiles.first()
                                        val selectedFile = uiState.files.find { it.path == selectedFilePath }
                                        selectedFile?.let {
                                            fileToRename = it
                                            showRenameDialog = true
                                        }
                                    },
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Rename",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            
                            // Download selected files
                            IconButton(
                                onClick = { viewModel.downloadSelectedFiles() },
                                modifier = Modifier.size(56.dp),
                                enabled = uiState.selectedFiles.isNotEmpty()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download Selected",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            // Delete selected files
                            FilledIconButton(
                                onClick = { viewModel.deleteSelectedFiles() },
                                modifier = Modifier.size(56.dp),
                                enabled = uiState.selectedFiles.isNotEmpty(),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Selected",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            // Normal mode actions
                            // Select mode
                            IconButton(
                                onClick = { viewModel.enterSelectionMode() },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Checklist,
                                    contentDescription = "Select Files",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            // New Folder - Secondary action
                            IconButton(
                                onClick = { viewModel.createDirectory("New Folder") },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = "Create Folder",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            // Upload - Primary action (more prominent coloring)
                            FilledIconButton(
                                onClick = { filePickerLauncher.launch("*/*") },
                                modifier = Modifier.size(56.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Upload,
                                    contentDescription = "Upload File",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    LoadingView(
                        modifier = Modifier.fillMaxSize(),
                        message = "Connecting to server..."
                    )
                }
            
                uiState.error != null -> {
                    val error = uiState.error
                    ErrorView(
                        error = error ?: "Unknown error",
                        onRetry = viewModel::refresh,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            
                uiState.files.isEmpty() -> {
                    EmptyDirectoryView(
                        modifier = Modifier.fillMaxSize()
                    )
                }
            
                else -> {
                    if (uiState.viewMode == "grid") {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 96.dp // Extra padding for floating toolbar
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Show parent directory if not at root
                        if (uiState.currentPath != "/" && uiState.currentPath.isNotEmpty()) {
                            item {
                                ParentDirectoryGridItem(
                                    onNavigateUp = {
                                        val parentPath = if (uiState.protocol == "SMB") {
                                            // Handle SMB paths with backslashes
                                            if (uiState.currentPath.contains("\\")) {
                                                uiState.currentPath.substringBeforeLast("\\")
                                            } else {
                                                "" // Go to share root if no backslashes
                                            }
                                        } else {
                                            // Handle FTP/SFTP paths with forward slashes
                                            uiState.currentPath.substringBeforeLast("/")
                                                .takeIf { it.isNotEmpty() } ?: "/"
                                        }
                                        viewModel.navigateToDirectory(parentPath)
                                    }
                                )
                            }
                        }
                        
                        items(uiState.files.filter { !it.isHidden || uiState.showHiddenFiles }) { file ->
                            FileGridItem(
                                file = file,
                                onClick = {
                                    if (file.isDirectory) {
                                        viewModel.navigateToDirectory(file.path)
                                    } else {
                                        handleFileOpen(context, viewModel, file, connectionId, fileViewerLauncher, uiState.downloadingFiles.contains(file.path))
                                    }
                                },
                                isSelectionMode = uiState.isSelectionMode,
                                isSelected = uiState.selectedFiles.contains(file.path),
                                onSelectionToggle = { 
                                    viewModel.toggleFileSelection(file.path) 
                                },
                                onLongPress = { 
                                    viewModel.enterSelectionModeWithFile(file.path) 
                                },
                                isDownloading = uiState.downloadingFiles.contains(file.path),
                                downloadProgress = uiState.downloadProgress[file.path] ?: 0f
                            )
                        }
                    }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 96.dp // Extra padding for floating toolbar
                            ),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                        // Show parent directory if not at root
                        if (uiState.currentPath != "/" && uiState.currentPath.isNotEmpty()) {
                            item {
                                ParentDirectoryItem(
                                    onNavigateUp = {
                                        val parentPath = if (uiState.protocol == "SMB") {
                                            // Handle SMB paths with backslashes
                                            if (uiState.currentPath.contains("\\")) {
                                                uiState.currentPath.substringBeforeLast("\\")
                                            } else {
                                                "" // Go to share root if no backslashes
                                            }
                                        } else {
                                            // Handle FTP/SFTP paths with forward slashes
                                            uiState.currentPath.substringBeforeLast("/")
                                                .takeIf { it.isNotEmpty() } ?: "/"
                                        }
                                        viewModel.navigateToDirectory(parentPath)
                                    }
                                )
                            }
                        }
                        
                        items(uiState.files.filter { !it.isHidden || uiState.showHiddenFiles }) { file ->
                            FileItem(
                                file = file,
                                onClick = {
                                    if (file.isDirectory) {
                                        viewModel.navigateToDirectory(file.path)
                                    } else {
                                        handleFileOpen(context, viewModel, file, connectionId, fileViewerLauncher, uiState.downloadingFiles.contains(file.path))
                                    }
                                },
                                isSelectionMode = uiState.isSelectionMode,
                                isSelected = uiState.selectedFiles.contains(file.path),
                                onSelectionToggle = { 
                                    viewModel.toggleFileSelection(file.path) 
                                },
                                onLongPress = { 
                                    viewModel.enterSelectionModeWithFile(file.path) 
                                },
                                isDownloading = uiState.downloadingFiles.contains(file.path),
                                downloadProgress = uiState.downloadProgress[file.path] ?: 0f
                            )
                        }
                    }
                    }
                }
            }
            
            // Upload progress overlay
            if (uiState.isUploading) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Uploading ${uiState.uploadFileName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${(uiState.uploadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        WavyLinearProgressIndicator(
                            progress = uiState.uploadProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
    
    // Rename dialog
    if (showRenameDialog && fileToRename != null) {
        var newName by remember { mutableStateOf(fileToRename!!.name) }
        
        AlertDialog(
            onDismissRequest = { 
                showRenameDialog = false
                fileToRename = null
            },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank() && newName != fileToRename!!.name) {
                            viewModel.renameFile(fileToRename!!.path, newName)
                            showRenameDialog = false
                            fileToRename = null
                        } else {
                            showRenameDialog = false
                            fileToRename = null
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showRenameDialog = false
                        fileToRename = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Delete confirmation dialog
    if (uiState.showDeleteConfirmation) {
        val selectedCount = uiState.selectedFiles.size
        val dialogText = if (selectedCount == 1) {
            "Are you sure you want to delete this item?"
        } else {
            "Are you sure you want to delete $selectedCount items?"
        }
        
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirmation() },
            title = { Text("Confirm Delete") },
            text = { Text(dialogText) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteSelectedFiles() }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissDeleteConfirmation() }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatPath(path: String, connectionName: String, protocol: String, shareName: String?): String {
    return when {
        protocol.equals("SMB", ignoreCase = true) -> {
            // For SMB, use Windows-style backslash paths: \SHARENAME\PATH
            val effectiveShareName = shareName ?: "SHARE"
            
            if (path == "/") {
                "\\$effectiveShareName"
            } else {
                val cleanPath = path.removePrefix("/")
                val pathParts = cleanPath.split("/").filter { it.isNotEmpty() }
                if (pathParts.isEmpty()) {
                    "\\$effectiveShareName"
                } else {
                    "\\$effectiveShareName" + pathParts.joinToString("\\")
                }
            }
        }
        else -> {
            // For other protocols, just clean up the path
            when {
                path == "/" -> connectionName
                else -> {
                    val cleanPath = path.removePrefix("/")
                    if (cleanPath.isEmpty()) connectionName else "$connectionName/$cleanPath"
                }
            }
        }
    }
}


@Composable
private fun ErrorView(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Connection Error",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onRetry) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retry")
        }
    }
}

@Composable
private fun EmptyDirectoryView(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Empty Directory",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "This directory contains no files",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ParentDirectoryItem(
    onNavigateUp: () -> Unit
) {
    Card(
        onClick = onNavigateUp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ArrowUpward,
                contentDescription = "Parent Directory",
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = "..",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun FileItem(
    file: RemoteFile,
    onClick: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionToggle: () -> Unit = {},
    onLongPress: () -> Unit = {},
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val hapticFeedback = LocalHapticFeedback.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(isSelectionMode) {
                    detectTapGestures(
                        onTap = {
                            if (isSelectionMode) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelectionToggle()
                            } else {
                                onClick()
                            }
                        },
                        onLongPress = {
                            if (!isSelectionMode) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongPress()
                            }
                        }
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { 
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelectionToggle() 
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            if (isDownloading && !file.isDirectory) {
                WavyCircularProgressIndicator(
                    progress = downloadProgress,
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4f
                )
            } else {
                Icon(
                    imageVector = if (file.isDirectory) Icons.Default.Folder else getFileIcon(file.name),
                    contentDescription = if (file.isDirectory) "Directory" else "File",
                    tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!file.isDirectory && file.size > 0) {
                        Text(
                            text = formatFileSize(file.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (file.lastModified > 0) {
                        Text(
                            text = dateFormat.format(Date(file.lastModified)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            if (file.isHidden) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "Hidden",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0
    
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    
    return "%.1f %s".format(size, units[unitIndex])
}

private enum class FileType {
    TEXT, CODE, MARKDOWN, IMAGE, PDF, AUDIO, VIDEO, ARCHIVE, EBOOK, UNKNOWN
}

private fun getFileType(fileName: String): FileType {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    
    return when {
        // Image extensions (including animated GIFs)
        extension in setOf("jpg", "jpeg", "png", "bmp", "webp", "svg", "gif") -> FileType.IMAGE
        
        // PDF extensions
        extension == "pdf" -> FileType.PDF
        
        // Audio extensions
        extension in setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus") -> FileType.AUDIO
        
        // Video extensions
        extension in setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts", "mts") -> FileType.VIDEO
        
        // Archive extensions
        extension in setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "lz", "lzma") -> FileType.ARCHIVE
        
        // Ebook extensions
        extension in setOf("epub", "mobi", "azw", "azw3", "fb2", "lit") -> FileType.EBOOK
        
        // Markdown extensions
        extension in setOf("md", "markdown") -> FileType.MARKDOWN
        
        // Code extensions (for syntax highlighting)
        extension in setOf(
            "js", "ts", "jsx", "tsx", "java", "kt", "py", "cpp", "c", "h", "cs", "php", 
            "rb", "go", "rs", "swift", "css", "scss", "sass", "html", "htm", "xml", 
            "json", "yaml", "yml", "sql", "sh", "bat", "ps1", "dockerfile", "gradle",
            "groovy", "scala", "clj", "elm", "dart", "vue", "svelte"
        ) -> FileType.CODE
        
        // Text extensions (plain text)
        extension in setOf(
            "txt", "properties", "ini", "cfg", "conf", "log", "csv", "toml",
            "gitignore", "makefile", "readme", "license", "changelog"
        ) -> FileType.TEXT
        
        // Files without extension or unknown extensions - assume text
        extension.isEmpty() || extension == fileName.lowercase() -> FileType.TEXT
        
        else -> FileType.TEXT // Default to text for unknown extensions
    }
}

private fun getFileIcon(fileName: String): ImageVector {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    
    return when {
        // PDF files
        extension == "pdf" -> Icons.Default.PictureAsPdf
        
        // Images
        extension in setOf("jpg", "jpeg", "png", "bmp", "webp", "svg", "gif") -> Icons.Default.Image
        
        // Audio files
        extension in setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus") -> Icons.Default.AudioFile
        
        // Video files
        extension in setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts", "mts") -> Icons.Filled.PlayArrow
        
        // Archive files
        extension in setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "lz", "lzma") -> Icons.Default.Archive
        
        // Ebook files
        extension in setOf("epub", "mobi", "azw", "azw3", "fb2", "lit") -> Icons.AutoMirrored.Filled.MenuBook
        
        // Markdown files
        extension in setOf("md", "markdown") -> Icons.Default.Description
        
        // Code files
        extension in setOf("json", "xml", "html", "htm", "css", "js", "ts", "java", "kt", "py", 
            "cpp", "c", "h", "cs", "php", "rb", "go", "rs", "swift", "yml", "yaml", "toml",
            "properties", "sql", "sh", "bat", "dockerfile", "gitignore", "gradle", "makefile") -> Icons.Default.Code
        
        // Plain text files
        extension in setOf("txt", "ini", "cfg", "conf", "log", "csv", "readme") -> Icons.AutoMirrored.Filled.TextSnippet
        
        // Files without extension
        extension.isEmpty() -> Icons.AutoMirrored.Filled.TextSnippet
        
        // Default file icon for unsupported types
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

private fun handleFileOpen(
    context: android.content.Context,
    viewModel: FileBrowserViewModel,
    file: RemoteFile,
    connectionId: String?,
    fileViewerLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>,
    isDownloading: Boolean = false
) {
    // If file is already downloading, cancel it
    if (isDownloading) {
        viewModel.cancelDownload(file.path)
        return
    }
    
    val fileType = getFileType(file.name)
    
    when (fileType) {
        FileType.TEXT, FileType.CODE, FileType.MARKDOWN, FileType.IMAGE, FileType.PDF, FileType.AUDIO, FileType.VIDEO, FileType.ARCHIVE, FileType.EBOOK -> {
            viewModel.openFile(file) { tempFile ->
                // Start FileViewerActivity with the downloaded file
                val intent = Intent(context, com.grid.app.presentation.fileviewer.FileViewerActivity::class.java).apply {
                    putExtra("file_path", tempFile.absolutePath)
                    putExtra("file_name", file.name)
                    putExtra("file_type", fileType.name)
                    putExtra("connection_id", connectionId)
                    putExtra("remote_path", file.path)
                }
                fileViewerLauncher.launch(intent)
            }
        }
        FileType.UNKNOWN -> {
            // Fallback to download for unknown types
            viewModel.downloadFile(file, "/tmp/${file.name}")
        }
    }
}

@Composable
private fun ParentDirectoryGridItem(
    onNavigateUp: () -> Unit
) {
    Card(
        onClick = onNavigateUp,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.ArrowUpward,
                contentDescription = "Parent Directory",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "..",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun FileGridItem(
    file: RemoteFile,
    onClick: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionToggle: () -> Unit = {},
    onLongPress: () -> Unit = {},
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f
) {
    val hapticFeedback = LocalHapticFeedback.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.5f),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isSelectionMode) {
                    detectTapGestures(
                        onTap = {
                            if (isSelectionMode) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelectionToggle()
                            } else {
                                onClick()
                            }
                        },
                        onLongPress = {
                            if (!isSelectionMode) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongPress()
                            }
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isDownloading && !file.isDirectory) {
                    WavyCircularProgressIndicator(
                        progress = downloadProgress,
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 5f
                    )
                } else {
                    Icon(
                        imageVector = if (file.isDirectory) Icons.Default.Folder else getFileIcon(file.name),
                        contentDescription = if (file.isDirectory) "Directory" else "File",
                        modifier = Modifier.size(if (file.isDirectory) 40.dp else 32.dp),
                        tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                if (!file.isDirectory && file.size > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatFileSize(file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (file.isHidden) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Hidden",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { 
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelectionToggle() 
                    },
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.TopEnd)
                        .padding(4.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FileBrowserScreenPreview() {
    GridTheme {
        FileBrowserScreen(
            connectionId = "1",
            onNavigateBack = {}
        )
    }
}