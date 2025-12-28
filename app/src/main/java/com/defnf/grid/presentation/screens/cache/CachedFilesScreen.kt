@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.defnf.grid.presentation.screens.cache

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
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
import com.defnf.grid.presentation.components.WavyCircularProgressIndicator
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
                    CachedFilesList(
                        cachedFiles = uiState.cachedFiles,
                        totalSize = uiState.totalCacheSize,
                        onFileClick = { cachedFile ->
                            viewModel.openCachedFile(cachedFile)?.let { intent ->
                                context.startActivity(intent)
                            }
                        },
                        onFileDelete = { cachedFile ->
                            viewModel.deleteCachedFile(cachedFile)
                        }
                    )
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
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header with total size
        item {
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
                            text = "${cachedFiles.size} files available offline",
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
                Icon(
                    imageVector = getFileIcon(cachedFile.extension),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
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

@Composable
private fun getFileIcon(extension: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (extension.lowercase()) {
        "pdf" -> Icons.Default.PictureAsPdf
        "jpg", "jpeg", "png", "gif", "bmp", "webp" -> Icons.Default.Image
        "mp3", "wav", "flac", "aac", "ogg", "m4a" -> Icons.Default.AudioFile
        "mp4", "mkv", "avi", "mov", "webm" -> Icons.Default.VideoFile
        "doc", "docx", "txt", "rtf" -> Icons.Default.Description
        "xls", "xlsx", "csv" -> Icons.Default.TableChart
        "zip", "rar", "7z", "tar", "gz" -> Icons.Default.FolderZip
        "apk" -> Icons.Default.Android
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
