@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.grid.app.presentation.screens.filebrowser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.grid.app.domain.model.RemoteFile
import com.grid.app.presentation.theme.GridTheme
import com.grid.app.presentation.components.ErrorView
import com.grid.app.presentation.components.EmptyDirectoryView
import com.grid.app.presentation.components.LoadingView
import java.text.SimpleDateFormat
import java.util.*
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun FileBrowserScreen(
    connectionId: String,
    onNavigateBack: () -> Unit,
    viewModel: FileBrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(connectionId) {
        viewModel.initialize(connectionId)
    }

    BackHandler {
        if (!viewModel.handleBackNavigation()) {
            onNavigateBack()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.connectionName,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = uiState.currentPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!viewModel.handleBackNavigation()) {
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
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
                        onClick = { /* TODO: Implement file picker */ },
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
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                LoadingView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            
            uiState.error != null -> {
                val error = uiState.error
                ErrorView(
                    error = error ?: "Unknown error",
                    onRetry = viewModel::refresh,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            
            uiState.files.isEmpty() -> {
                EmptyDirectoryView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            
            else -> {
                if (uiState.viewMode == "grid") {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Show parent directory if not at root
                        if (uiState.currentPath != "/") {
                            item {
                                ParentDirectoryGridItem(
                                    onNavigateUp = {
                                        val parentPath = uiState.currentPath.substringBeforeLast("/")
                                            .takeIf { it.isNotEmpty() } ?: "/"
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
                                        // TODO: Implement download with proper local path
                                        viewModel.downloadFile(file, "/tmp/${file.name}")
                                    }
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Show parent directory if not at root
                        if (uiState.currentPath != "/") {
                            item {
                                ParentDirectoryItem(
                                    onNavigateUp = {
                                        val parentPath = uiState.currentPath.substringBeforeLast("/")
                                            .takeIf { it.isNotEmpty() } ?: "/"
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
                                        // TODO: Implement download with proper local path
                                        viewModel.downloadFile(file, "/tmp/${file.name}")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingView(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Connecting to server...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = if (file.isDirectory) "Directory" else "File",
                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            
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
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = if (file.isDirectory) "Directory" else "File",
                modifier = Modifier.size(if (file.isDirectory) 40.dp else 32.dp),
                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            
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