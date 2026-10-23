@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.defnf.grid.presentation.screens.filebrowser

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.combinedClickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Sort
import com.defnf.grid.presentation.components.HostKeyChangedDialog
import com.defnf.grid.presentation.components.ShapedIcon
import com.defnf.grid.presentation.components.animatedPressCorner
import com.defnf.grid.presentation.components.launcherIconShape
import com.defnf.grid.presentation.util.getFileIcon
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import com.defnf.grid.presentation.components.FileThumbnailFill
import com.defnf.grid.presentation.components.hasThumbnail
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.defnf.grid.presentation.util.FileType
import com.defnf.grid.presentation.util.getFileType
import java.io.File
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.app.Activity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.defnf.grid.domain.model.RemoteFile
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import com.defnf.grid.presentation.theme.GridTheme
import com.defnf.grid.presentation.components.WavyCircularProgressIndicator
import com.defnf.grid.presentation.components.ErrorView
import com.defnf.grid.presentation.components.EmptyDirectoryView
import com.defnf.grid.presentation.components.LoadingView
import com.defnf.grid.presentation.components.WavyLinearProgressIndicator
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val visibleFiles = remember(uiState.files, uiState.showHiddenFiles) {
        uiState.files.filter { !it.isHidden || uiState.showHiddenFiles }
    }
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
    
    // File picker launcher (multi-select). Display-name resolution and the upload
    // itself are handled by the ViewModel.
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadFiles(uris)
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

    // Offline copies live in Downloads, which Android 10 and below only let apps write with
    // storage access. Without it, opening a file reports the write failure.
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
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
            // Selecting tints the bar so the mode change reads at a glance
            val barColor by animateColorAsState(
                targetValue = if (uiState.isSelectionMode) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "TopBarColor"
            )
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = uiState.isSelectionMode,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "TopBarTitle"
                    ) { selecting ->
                        Text(
                            text = if (selecting) "${uiState.selectedFiles.size} selected" else uiState.connectionName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                subtitle = {
                    Text(
                        text = formatPath(uiState.currentPath, uiState.connectionName, uiState.protocol, uiState.shareName),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = barColor),
                navigationIcon = {
                    IconButton(
                        onClick = debouncedNavigateBack,
                        shapes = IconButtonDefaults.shapes()
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
                        var expanded by remember { mutableStateOf(false) }

                        Box {
                            IconButton(
                                onClick = { expanded = true },
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort"
                                )
                            }

                            SortMenu(
                                expanded = expanded,
                                selected = uiState.sortOption,
                                onSelect = {
                                    viewModel.setSortOption(it)
                                    expanded = false
                                },
                                onDismiss = { expanded = false }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // Material 3 Expressive floating toolbar. It stays mounted while folders load (it used
            // to pop out and back on every tap) and only slides away during an upload, whose
            // progress card takes its place.
            AnimatedVisibility(
                visible = !uiState.isUploading,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                val selecting = uiState.isSelectionMode
                val hasSelection = uiState.selectedFiles.isNotEmpty()
                HorizontalFloatingToolbar(
                    expanded = true,
                    modifier = Modifier.padding(bottom = 16.dp),
                    floatingActionButton = {
                        // Primary action: Upload, or Delete while selecting
                        val fabColor by animateColorAsState(
                            targetValue = when {
                                !selecting -> MaterialTheme.colorScheme.primaryContainer
                                hasSelection -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            label = "ToolbarFabColor"
                        )
                        FloatingToolbarDefaults.VibrantFloatingActionButton(
                            onClick = {
                                if (!selecting) {
                                    filePickerLauncher.launch("*/*")
                                } else if (hasSelection) {
                                    viewModel.deleteSelectedFiles()
                                }
                            },
                            containerColor = fabColor,
                            contentColor = contentColorFor(fabColor)
                        ) {
                            AnimatedContent(targetState = selecting, label = "ToolbarFabIcon") { isSelecting ->
                                Icon(
                                    imageVector = if (isSelecting) Icons.Default.Delete else Icons.Default.Upload,
                                    contentDescription = if (isSelecting) "Delete Selected" else "Upload File"
                                )
                            }
                        }
                    }
                ) {
                    AnimatedContent(
                        targetState = selecting,
                        transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
                        label = "ToolbarActions"
                    ) { isSelecting ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelecting) {
                                IconButton(
                                    onClick = { viewModel.exitSelectionMode() },
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear Selection")
                                }
                                // Rename only applies to exactly one file
                                IconButton(
                                    onClick = {
                                        val selectedFilePath = uiState.selectedFiles.first()
                                        uiState.files.find { it.path == selectedFilePath }?.let {
                                            fileToRename = it
                                            showRenameDialog = true
                                        }
                                    },
                                    enabled = uiState.selectedFiles.size == 1,
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename")
                                }
                                IconButton(
                                    onClick = { viewModel.downloadSelectedFiles() },
                                    enabled = hasSelection,
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "Download Selected")
                                }
                            } else {
                                IconButton(
                                    onClick = { viewModel.enterSelectionMode() },
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Icon(Icons.Default.Checklist, contentDescription = "Select Files")
                                }
                                IconButton(
                                    onClick = { viewModel.createDirectory("New Folder") },
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Icon(Icons.Default.CreateNewFolder, contentDescription = "Create Folder")
                                }
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
                            top = 8.dp,
                            bottom = 96.dp // Extra padding for floating toolbar
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Show the parent directory unless this is the connection's root
                        if (uiState.canNavigateUp) {
                            item {
                                ParentDirectoryGridItem(onNavigateUp = { viewModel.navigateUp() })
                            }
                        }
                        
                        items(
                            items = visibleFiles,
                            key = { file -> file.path },
                            contentType = { file -> if (file.isDirectory) "directory" else "file" }
                        ) { file ->
                            FileGridItem(
                                modifier = Modifier.animateItem(),
                                file = file,
                                thumbnailFile = offlineThumbnailFile(uiState.offlineCopies[file.path], file),
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
                                isDownloading = uiState.downloadingFiles.contains(file.path) ||
                                    uiState.fillProgress.containsKey(file.path),
                                downloadProgress = uiState.downloadProgress[file.path]
                                    ?: uiState.fillProgress[file.path] ?: 0f
                            )
                        }
                    }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 96.dp // Extra padding for floating toolbar
                            ),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                        ) {
                        // One segmented group: the parent row (when shown) and every entry
                        val parentRows = if (uiState.canNavigateUp) 1 else 0
                        val rowCount = visibleFiles.size + parentRows

                        // Show the parent directory unless this is the connection's root
                        if (uiState.canNavigateUp) {
                            item {
                                ParentDirectoryItem(
                                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = rowCount),
                                    onNavigateUp = { viewModel.navigateUp() }
                                )
                            }
                        }
                        
                        itemsIndexed(
                            items = visibleFiles,
                            key = { _, file -> file.path },
                            contentType = { _, file -> if (file.isDirectory) "directory" else "file" }
                        ) { index, file ->
                            FileItem(
                                modifier = Modifier.animateItem(),
                                shapes = ListItemDefaults.segmentedShapes(index = index + parentRows, count = rowCount),
                                file = file,
                                thumbnailFile = offlineThumbnailFile(uiState.offlineCopies[file.path], file),
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
                                isDownloading = uiState.downloadingFiles.contains(file.path) ||
                                    uiState.fillProgress.containsKey(file.path),
                                downloadProgress = uiState.downloadProgress[file.path]
                                    ?: uiState.fillProgress[file.path] ?: 0f
                            )
                        }
                    }
                    }
                }
            }
            
            // Upload progress card, in the floating toolbar's place
            AnimatedVisibility(
                visible = uiState.isUploading,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                UploadProgressCard(
                    fileName = uiState.uploadFileName,
                    progress = uiState.uploadProgress,
                    currentIndex = uiState.uploadCurrentIndex,
                    totalCount = uiState.uploadTotalCount,
                    onCancel = { viewModel.cancelUpload() }
                )
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
            icon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New name") },
                    shape = MaterialTheme.shapes.large,
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
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showRenameDialog = false
                        fileToRename = null
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    uiState.hostKeyChange?.let { change ->
        HostKeyChangedDialog(
            change = change,
            onTrust = { viewModel.trustChangedHostKey() },
            onDismiss = { viewModel.dismissHostKeyChange() }
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
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete?") },
            text = { Text(dialogText) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteSelectedFiles() },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissDeleteConfirmation() },
                    shapes = ButtonDefaults.shapes()
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


/** Sort options as an expressive menu group; the current choice is checked. */
@Composable
private fun SortMenu(
    expanded: Boolean,
    selected: SortOption,
    onSelect: (SortOption) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        SortOption.NAME to "Name",
        SortOption.TYPE to "Type",
        SortOption.LAST_MODIFIED to "Last modified"
    )

    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
            options.forEachIndexed { index, (option, label) ->
                CheckableDropdownMenuItem(
                    checked = selected == option,
                    onCheckedChange = { onSelect(option) },
                    text = { Text(label) },
                    shapes = MenuDefaults.itemShape(index = index, count = options.size),
                    checkedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
private fun UploadProgressCard(
    fileName: String,
    progress: Float,
    currentIndex: Int,
    totalCount: Int,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapedIcon(
                    imageVector = Icons.Default.Upload,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (totalCount > 1) {
                            "Uploading $currentIndex of $totalCount"
                        } else {
                            "Uploading $fileName"
                        },
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (totalCount > 1) {
                            "$fileName · ${(progress * 100).toInt()}%"
                        } else {
                            "${(progress * 100).toInt()}%"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onCancel,
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel upload",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            WavyLinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp)
            )
        }
    }
}

/** Container tone for browser rows: the default segmented Surface vanishes on the Scaffold. */
@Composable
private fun fileRowColors(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer
)

@Composable
private fun ParentDirectoryItem(
    shapes: ListItemShapes,
    onNavigateUp: () -> Unit
) {
    SegmentedListItem(
        onClick = onNavigateUp,
        shapes = shapes,
        colors = fileRowColors(),
        leadingContent = {
            ShapedIcon(
                imageVector = Icons.Default.ArrowUpward,
                shape = launcherIconShape(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
                contentDescription = "Parent Directory"
            )
        },
        supportingContent = { Text("Up one level") }
    ) {
        Text(text = "..")
    }
}

/**
 * The leading visual of a file row or tile: a check while selected, download progress, a real
 * thumbnail when an offline copy has one, or the type icon on an expressive shape.
 */
@Composable
private fun FileLeadingVisual(
    file: RemoteFile,
    isSelected: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    thumbnailFile: File?,
    size: Dp,
    iconSize: Dp
) {
    // Read outside transitionSpec, which isn't a composable context
    val scaleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    AnimatedContent(
        targetState = isSelected,
        transitionSpec = {
            (scaleIn(scaleSpec) + fadeIn()) togetherWith (scaleOut(scaleSpec) + fadeOut())
        },
        label = "FileLeadingVisual"
    ) { selected ->
        when {
            selected -> ShapedIcon(
                imageVector = Icons.Default.Check,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(size),
                iconSize = iconSize,
                contentDescription = "Selected"
            )

            isDownloading && !file.isDirectory -> Box(
                modifier = Modifier
                    .size(size)
                    .clip(launcherIconShape())
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                WavyCircularProgressIndicator(
                    progress = downloadProgress,
                    modifier = Modifier.size(iconSize + 8.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            hasThumbnail(file.name, file.isDirectory, thumbnailFile) -> FileThumbnailFill(
                fileName = file.name,
                localFile = thumbnailFile,
                modifier = Modifier
                    .size(size)
                    .clip(MaterialTheme.shapes.medium)
            )

            file.isDirectory -> ShapedIcon(
                imageVector = Icons.Default.Folder,
                shape = launcherIconShape(),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(size),
                iconSize = iconSize,
                contentDescription = "Directory"
            )

            else -> ShapedIcon(
                imageVector = getFileIcon(file.name),
                shape = launcherIconShape(),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(size),
                iconSize = iconSize,
                contentDescription = "File"
            )
        }
    }
}

@Composable
private fun FileItem(
    file: RemoteFile,
    onClick: () -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionToggle: () -> Unit = {},
    onLongPress: () -> Unit = {},
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f,
    thumbnailFile: File? = null
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val hapticFeedback = LocalHapticFeedback.current

    val details = buildList {
        if (!file.isDirectory && file.size > 0) add(formatFileSize(file.size))
        if (file.lastModified > 0) add(dateFormat.format(Date(file.lastModified)))
    }.joinToString(" · ")

    // A selected row takes the segmented list's selected shape and tone
    SegmentedListItem(
        selected = isSelected,
        onClick = {
            if (isSelectionMode) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onSelectionToggle()
            } else {
                onClick()
            }
        },
        onLongClick = if (isSelectionMode) {
            null
        } else {
            {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongPress()
            }
        },
        onLongClickLabel = "Select",
        shapes = shapes,
        modifier = modifier,
        colors = fileRowColors(),
        leadingContent = {
            FileLeadingVisual(
                file = file,
                isSelected = isSelected,
                isDownloading = isDownloading,
                downloadProgress = downloadProgress,
                thumbnailFile = thumbnailFile,
                size = 48.dp,
                iconSize = 24.dp
            )
        },
        supportingContent = if (details.isNotEmpty()) {
            { Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        trailingContent = if (file.isHidden) {
            {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = "Hidden",
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            null
        }
    ) {
        Text(
            text = file.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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

/**
 * The offline copy to draw a remote image/audio/video file's thumbnail from, if it has one. Only
 * images, audio, and video qualify; everything else keeps its icon.
 */
private fun offlineThumbnailFile(offlineCopy: File?, file: RemoteFile): File? {
    if (offlineCopy == null) return null
    val type = getFileType(file.name)
    return offlineCopy.takeIf { type == FileType.IMAGE || type == FileType.AUDIO || type == FileType.VIDEO }
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
    
    fun viewerIntent() = Intent(context, com.defnf.grid.presentation.fileviewer.FileViewerActivity::class.java).apply {
        putExtra("file_name", file.name)
        putExtra("file_type", fileType.name)
        putExtra("connection_id", connectionId)
        putExtra("remote_path", file.path)
    }

    when (fileType) {
        // Media streams from the server, so playback starts without waiting for a download
        FileType.AUDIO, FileType.VIDEO -> {
            viewModel.openMedia(
                file = file,
                onLocalFile = { cached ->
                    fileViewerLauncher.launch(viewerIntent().putExtra("file_path", cached.absolutePath))
                },
                onStream = { uri ->
                    fileViewerLauncher.launch(viewerIntent().putExtra("stream_uri", uri))
                }
            )
        }
        // UNKNOWN opens in the viewer too, which offers to hand the file to another app.
        FileType.TEXT, FileType.CODE, FileType.MARKDOWN, FileType.IMAGE, FileType.PDF,
        FileType.ARCHIVE, FileType.EBOOK, FileType.APK, FileType.UNKNOWN -> {
            viewModel.openFile(file) { tempFile ->
                // Start FileViewerActivity with the downloaded file
                fileViewerLauncher.launch(viewerIntent().putExtra("file_path", tempFile.absolutePath))
            }
        }
    }
}

@Composable
private fun ParentDirectoryGridItem(
    onNavigateUp: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        onClick = onNavigateUp,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.5f),
        shape = RoundedCornerShape(animatedPressCorner(interactionSource)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        interactionSource = interactionSource
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ShapedIcon(
                imageVector = Icons.Default.ArrowUpward,
                shape = launcherIconShape(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
                contentDescription = "Parent Directory"
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "..",
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
private fun FileGridItem(
    file: RemoteFile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionToggle: () -> Unit = {},
    onLongPress: () -> Unit = {},
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f,
    thumbnailFile: File? = null
) {
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "FileGridItemContainer"
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.5f),
        shape = RoundedCornerShape(animatedPressCorner(interactionSource)),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // combinedClickable always calls the latest lambdas (a pointerInput keyed only on
                // selection mode kept stale ones, so tapping a downloading file started a second
                // download instead of cancelling), and adds ripple + TalkBack actions.
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = {
                        if (isSelectionMode) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelectionToggle()
                        } else {
                            onClick()
                        }
                    },
                    onLongClickLabel = "Select",
                    onLongClick = {
                        if (!isSelectionMode) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                        }
                    }
                )
        ) {
            // When a real thumbnail is cached, let it fill the whole card and lay the name over a
            // gradient scrim so it stays legible against the image.
            val showFullThumbnail = !isDownloading &&
                hasThumbnail(file.name, file.isDirectory, thumbnailFile)

            if (showFullThumbnail) {
                FileThumbnailFill(
                    fileName = file.name,
                    localFile = thumbnailFile,
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                            )
                        )
                        .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 10.dp)
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                    if (file.size > 0) {
                        Text(
                            text = formatFileSize(file.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    FileLeadingVisual(
                        file = file,
                        // The corner badge shows selection on tiles
                        isSelected = false,
                        isDownloading = isDownloading,
                        downloadProgress = downloadProgress,
                        thumbnailFile = thumbnailFile,
                        size = 44.dp,
                        iconSize = 24.dp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    if (!file.isDirectory && file.size > 0) {
                        Text(
                            text = formatFileSize(file.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (file.isHidden) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = "Hidden",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .size(16.dp),
                    tint = if (showFullThumbnail) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelectionToggle()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
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