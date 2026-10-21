@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.defnf.grid.presentation.screens.cache

import android.text.format.Formatter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.defnf.grid.domain.model.CachedFile
import com.defnf.grid.presentation.components.FileThumbnailFill
import com.defnf.grid.presentation.components.hasThumbnail
import com.defnf.grid.presentation.util.getFileIcon
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // Expressive flexible app bar: a large title that collapses as the list scrolls
            LargeFlexibleTopAppBar(
                title = { Text("Offline files") },
                subtitle = if (uiState.connectionName.isNotEmpty()) {
                    { Text(uiState.connectionName) }
                } else {
                    null
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
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
                    LoadingIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(64.dp)
                    )
                }

                uiState.cachedFiles.isEmpty() -> {
                    EmptyCacheView(
                        unfinishedSize = uiState.unfinishedSize,
                        onClear = { viewModel.showClearCacheConfirmation() },
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
                    val onClearAll = { viewModel.showClearCacheConfirmation() }
                    if (uiState.viewMode == "grid") {
                        CachedFilesGrid(
                            cachedFiles = uiState.cachedFiles,
                            totalSize = uiState.totalCacheSize,
                            unfinishedSize = uiState.unfinishedSize,
                            onFileClick = onFileClick,
                            onFileDelete = onFileDelete,
                            onClearAll = onClearAll
                        )
                    } else {
                        CachedFilesList(
                            cachedFiles = uiState.cachedFiles,
                            totalSize = uiState.totalCacheSize,
                            unfinishedSize = uiState.unfinishedSize,
                            onFileClick = onFileClick,
                            onFileDelete = onFileDelete,
                            onClearAll = onClearAll
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
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
            title = { Text("Clear offline files?") },
            text = {
                val what = when {
                    uiState.cachedFiles.isEmpty() -> "all unfinished saves"
                    uiState.unfinishedSize > 0 -> "all ${uiState.cachedFiles.size} offline files and unfinished saves"
                    else -> "all ${uiState.cachedFiles.size} offline files"
                }
                Text(
                    "Are you sure you want to delete $what? " +
                            "This will free up " +
                            "${Formatter.formatFileSize(context, uiState.totalCacheSize + uiState.unfinishedSize)} " +
                            "and removes them from ${uiState.offlineLocation}."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmClearCache() },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissClearCacheConfirmation() },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmptyCacheView(
    unfinishedSize: Long,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ShapedIcon(
            imageVector = Icons.Default.CloudOff,
            shape = MaterialShapes.Cookie9Sided.toShape(),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(128.dp),
            iconSize = 56.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No offline files",
            style = MaterialTheme.typography.headlineMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Files you open, download or play from this connection are kept in Downloads/Grid",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (unfinishedSize > 0) {
            Spacer(modifier = Modifier.height(24.dp))
            FilledTonalButton(
                onClick = onClear,
                shapes = ButtonDefaults.shapes()
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text("Clear ${Formatter.formatFileSize(context, unfinishedSize)} of unfinished saves")
            }
        }
    }
}

@Composable
private fun CachedFilesList(
    cachedFiles: List<CachedFile>,
    totalSize: Long,
    unfinishedSize: Long,
    onFileClick: (CachedFile) -> Unit,
    onFileDelete: (CachedFile) -> Unit,
    onClearAll: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
    ) {
        item {
            CacheSummaryCard(
                fileCount = cachedFiles.size,
                totalSize = totalSize,
                unfinishedSize = unfinishedSize,
                onClearAll = onClearAll,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // One segmented group: rounded outer corners, tight inner seams
        itemsIndexed(cachedFiles, key = { _, file -> file.path }) { index, cachedFile ->
            CachedFileItem(
                cachedFile = cachedFile,
                shapes = ListItemDefaults.segmentedShapes(index = index, count = cachedFiles.size),
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
    unfinishedSize: Long,
    onFileClick: (CachedFile) -> Unit,
    onFileDelete: (CachedFile) -> Unit,
    onClearAll: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header spans the full row width.
        item(span = { GridItemSpan(maxLineSpan) }) {
            CacheSummaryCard(
                fileCount = cachedFiles.size,
                totalSize = totalSize,
                unfinishedSize = unfinishedSize,
                onClearAll = onClearAll,
                modifier = Modifier.padding(bottom = 8.dp)
            )
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
private fun CacheSummaryCard(
    fileCount: Int,
    totalSize: Long,
    unfinishedSize: Long,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapedIcon(
                    imageVector = Icons.Default.OfflinePin,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(64.dp),
                    iconSize = 32.dp
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = Formatter.formatShortFileSize(context, totalSize),
                        style = MaterialTheme.typography.headlineLargeEmphasized
                    )
                    Text(
                        text = "$fileCount ${if (fileCount == 1) "file" else "files"} available offline",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (unfinishedSize > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "+${Formatter.formatShortFileSize(context, unfinishedSize)} unfinished",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = onClearAll,
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Clear all")
                }
            }
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

    // Expressive press feedback: the tile's corners tighten while held and spring back
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 12.dp else 24.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "GridTileCorner"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(corner),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        onClick = onClick,
        interactionSource = interactionSource
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
                        .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = cachedFile.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                    Text(
                        text = Formatter.formatShortFileSize(context, cachedFile.size),
                        style = MaterialTheme.typography.labelMedium,
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
                    ShapedIcon(
                        imageVector = getFileIcon(cachedFile.name),
                        shape = MaterialShapes.Cookie4Sided.toShape(),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(56.dp),
                        iconSize = 28.dp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = cachedFile.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = Formatter.formatShortFileSize(context, cachedFile.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FilledTonalIconButton(
                onClick = { showDeleteConfirmation = true },
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showDeleteConfirmation) {
        DeleteOfflineFileDialog(
            fileName = cachedFile.name,
            onConfirm = {
                showDeleteConfirmation = false
                onDelete()
            },
            onDismiss = { showDeleteConfirmation = false }
        )
    }
}

@Composable
private fun CachedFileItem(
    cachedFile: CachedFile,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val localFile = remember(cachedFile.path) { File(cachedFile.path) }

    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        leadingContent = {
            if (hasThumbnail(cachedFile.name, isDirectory = false, localFile = localFile)) {
                FileThumbnailFill(
                    fileName = cachedFile.name,
                    localFile = localFile,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.large)
                )
            } else {
                ShapedIcon(
                    imageVector = getFileIcon(cachedFile.name),
                    shape = MaterialShapes.Cookie4Sided.toShape(),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(56.dp),
                    iconSize = 28.dp
                )
            }
        },
        supportingContent = {
            Column {
                Text(
                    text = cachedFile.remotePath.substringBeforeLast('/') + "/",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = Formatter.formatShortFileSize(context, cachedFile.size) +
                        " · " + formatDate(cachedFile.cachedAt),
                    maxLines = 1
                )
            }
        },
        trailingContent = {
            IconButton(
                onClick = { showDeleteConfirmation = true },
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete"
                )
            }
        }
    ) {
        Text(
            text = cachedFile.name,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    if (showDeleteConfirmation) {
        DeleteOfflineFileDialog(
            fileName = cachedFile.name,
            onConfirm = {
                showDeleteConfirmation = false
                onDelete()
            },
            onDismiss = { showDeleteConfirmation = false }
        )
    }
}

/** An icon centred in a filled container clipped to one of the Material 3 Expressive shapes. */
@Composable
private fun ShapedIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun DeleteOfflineFileDialog(
    fileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
        title = { Text("Delete offline file?") },
        text = { Text("Delete \"$fileName\"? This removes it from Downloads/Grid on this device.") },
        confirmButton = {
            TextButton(onClick = onConfirm, shapes = ButtonDefaults.shapes()) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text("Cancel")
            }
        }
    )
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
