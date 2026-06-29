@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.defnf.grid.presentation.screens.cache

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.defnf.grid.domain.model.CachedFile
import com.defnf.grid.presentation.components.FileThumbnail
import com.defnf.grid.presentation.components.FileThumbnailFill
import com.defnf.grid.presentation.components.hasThumbnail
import com.defnf.grid.presentation.components.WavyCircularProgressIndicator
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CachedFilesScreen(
    connectionId: String,
    onNavigateBack: () -> Unit,
    viewModel: CachedFilesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(connectionId) {
        viewModel.initialize(connectionId)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Offline Files",
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.connectionName.isNotEmpty()) {
                            Text(
                                text = uiState.connectionName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState.cachedFiles.isNotEmpty()) {
                        IconButton(onClick = { viewModel.showClearCacheConfirmation() }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear all cache"
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        WavyCircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                uiState.cachedFiles.isEmpty() -> {
                    EmptyCacheView(
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    val onFileClick: (CachedFile) -> Unit = { cachedFile ->
                        viewModel.openCachedFile(cachedFile)?.let { intent ->
                            context.startActivity(intent)
                        }
                    }
                    val onFileDelete: (CachedFile) -> Unit = { cachedFile ->
                        viewModel.deleteCachedFile(cachedFile)
                    }
                    if (uiState.viewMode == "grid") {
                        CachedFilesGrid(
                            cachedFiles = uiState.cachedFiles,
                            totalSize = uiState.totalCacheSize,
                            onFileClick = onFileClick,
                            onFileDelete = onFileDelete
                        )
                    } else {
                        CachedFilesList(
                            cachedFiles = uiState.cachedFiles,
                            totalSize = uiState.totalCacheSize,
                            onFileClick = onFileClick,
                            onFileDelete = onFileDelete
                        )
                    }
                }
            }
        }
    }

    // Clear cache confirmation dialog
    if (uiState.showClearCacheConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissClearCacheConfirmation() },
            title = { Text("Clear Offline Files") },
            text = {
                Text(
                    "Are you sure you want to delete all ${uiState.cachedFiles.size} offline files? " +
                            "This will free up ${Formatter.formatFileSize(context, uiState.totalCacheSize)}."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmClearCache() }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClearCacheConfirmation() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmptyCacheView(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.FolderOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No offline files",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Files you open from this connection will be available offline",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CachedFilesList(
    cachedFiles: List<CachedFile>,
    totalSize: Long,
    onFileClick: (CachedFile) -> Unit,
    onFileDelete: (CachedFile) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header with total size
        item {
            CacheSummaryCard(fileCount = cachedFiles.size, totalSize = totalSize)
        }

        items(cachedFiles, key = { it.path }) { cachedFile ->
            CachedFileItem(
                cachedFile = cachedFile,
                onClick = { onFileClick(cachedFile) },
                onDelete = { onFileDelete(cachedFile) }
            )
        }
    }
}

@Composable
private fun CachedFilesGrid(
    cachedFiles: List<CachedFile>,
    totalSize: Long,
    onFileClick: (CachedFile) -> Unit,
    onFileDelete: (CachedFile) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header spans the full row width.
        item(span = { GridItemSpan(maxLineSpan) }) {
            CacheSummaryCard(fileCount = cachedFiles.size, totalSize = totalSize)
        }

        items(cachedFiles, key = { it.path }) { cachedFile ->
            CachedFileGridItem(
                cachedFile = cachedFile,
                onClick = { onFileClick(cachedFile) },
                onDelete = { onFileDelete(cachedFile) }
            )
        }
    }
}

@Composable
private fun CacheSummaryCard(fileCount: Int, totalSize: Long) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "$fileCount files available offline",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Total size: ${Formatter.formatFileSize(context, totalSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun CachedFileGridItem(
    cachedFile: CachedFile,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val localFile = remember(cachedFile.path) { File(cachedFile.path) }
    val showFullThumbnail = hasThumbnail(cachedFile.name, isDirectory = false, localFile = localFile)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.5f),
        onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (showFullThumbnail) {
                FileThumbnailFill(
                    fileName = cachedFile.name,
                    localFile = localFile,
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
                        .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = cachedFile.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                    Text(
                        text = Formatter.formatFileSize(context, cachedFile.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    FileThumbnail(
                        fileName = cachedFile.name,
                        isDirectory = false,
                        localFile = localFile,
                        size = 48.dp,
                        folderTint = MaterialTheme.colorScheme.primary,
                        fileTint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = cachedFile.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = Formatter.formatFileSize(context, cachedFile.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = { showDeleteConfirmation = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Offline File") },
            text = { Text("Delete \"${cachedFile.name}\" from offline storage?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CachedFileItem(
    cachedFile: CachedFile,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FileThumbnail(
                    fileName = cachedFile.name,
                    isDirectory = false,
                    localFile = File(cachedFile.path),
                    size = 40.dp,
                    folderTint = MaterialTheme.colorScheme.primary,
                    fileTint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cachedFile.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = cachedFile.remotePath.substringBeforeLast('/') + "/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = Formatter.formatFileSize(context, cachedFile.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDate(cachedFile.cachedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(onClick = { showDeleteConfirmation = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Offline File") },
            text = { Text("Delete \"${cachedFile.name}\" from offline storage?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
