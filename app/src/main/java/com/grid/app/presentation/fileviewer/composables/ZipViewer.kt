package com.grid.app.presentation.fileviewer.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.grid.app.presentation.fileviewer.composables.archive.ArchiveEntry
import com.grid.app.presentation.fileviewer.composables.archive.ArchiveReader
import com.grid.app.presentation.fileviewer.composables.archive.ArchiveExtractor
import com.grid.app.presentation.fileviewer.composables.archive.ExtractionProgress
import android.os.Environment

data class ArchiveViewerActions(
    val extract: () -> Unit,
    val canExtract: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZipViewer(
    file: File,
    modifier: Modifier = Modifier,
    onActionsChanged: (ArchiveViewerActions) -> Unit = {}
) {
    var archiveEntries by remember { mutableStateOf<List<ArchiveEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var expandedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    val archiveReader = remember { ArchiveReader() }
    
    val extractAction = {
        // This will be called by the parent
    }
    
    // Expose actions to parent
    LaunchedEffect(isLoading, error) {
        onActionsChanged(
            ArchiveViewerActions(
                extract = extractAction,
                canExtract = !isLoading && error == null && archiveEntries.isNotEmpty()
            )
        )
    }
    
    // Load archive contents
    LaunchedEffect(file) {
        isLoading = true
        error = null
        
        archiveReader.readArchive(file)
            .onSuccess { entries ->
                archiveEntries = entries
                isLoading = false
            }
            .onFailure { throwable ->
                error = throwable.message
                isLoading = false
            }
    }
    
    
    Column(modifier = modifier.fillMaxSize()) {
        // Header with archive info
        if (!isLoading && archiveEntries.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    val totalFiles = countTotalFiles(archiveEntries)
                    val totalSize = calculateTotalSize(archiveEntries)
                    Text(
                        text = "$totalFiles files • ${formatFileSize(totalSize)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        // Archive contents
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Error reading archive:",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            archiveEntries.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Archive is empty")
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(archiveEntries) { entry ->
                        ArchiveEntryItem(
                            entry = entry,
                            expandedPaths = expandedPaths,
                            onToggleExpanded = { path ->
                                expandedPaths = if (expandedPaths.contains(path)) {
                                    expandedPaths - path
                                } else {
                                    expandedPaths + path
                                }
                            },
                            level = 0
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveEntryItem(
    entry: ArchiveEntry,
    expandedPaths: Set<String>,
    onToggleExpanded: (String) -> Unit,
    level: Int
) {
    val isExpanded = expandedPaths.contains(entry.path)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = entry.isDirectory) {
                        if (entry.isDirectory) {
                            onToggleExpanded(entry.path)
                        }
                    }
                    .padding(
                        start = (16 + level * 24).dp,
                        top = 12.dp,
                        end = 16.dp,
                        bottom = 12.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = getFileIcon(entry),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = getFileIconColor(entry)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (entry.isDirectory) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (!entry.isDirectory) {
                        Row {
                            if (entry.size > 0) {
                                Text(
                                    text = formatFileSize(entry.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                if (entry.compressedSize > 0) {
                                    Text(
                                        text = " • ${entry.compressionRatio.toInt()}% compressed",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            entry.lastModified?.let { date ->
                                val prefix = if (entry.size > 0) " • " else ""
                                Text(
                                    text = "$prefix${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (entry.children.isNotEmpty()) {
                        Text(
                            text = "${entry.children.size} items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (entry.isDirectory && entry.children.isNotEmpty()) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Show children if expanded
            if (entry.isDirectory && isExpanded) {
                Column {
                    for (child in entry.children.sortedWith(compareBy<ArchiveEntry> { !it.isDirectory }.thenBy { it.name })) {
                        ArchiveEntryItem(
                            entry = child,
                            expandedPaths = expandedPaths,
                            onToggleExpanded = onToggleExpanded,
                            level = level + 1
                        )
                    }
                }
            }
        }
    }
}

private fun getFileIcon(entry: ArchiveEntry): ImageVector {
    return if (entry.isDirectory) {
        Icons.Default.Folder
    } else {
        when (entry.extension.lowercase()) {
            "txt", "md", "json", "xml", "html", "css", "js", "py", "kt", "java" -> Icons.Default.Description
            "jpg", "jpeg", "png", "gif", "bmp", "webp" -> Icons.Default.Image
            "mp3", "wav", "flac", "ogg", "m4a" -> Icons.Default.AudioFile
            "mp4", "avi", "mkv", "mov", "wmv" -> Icons.Default.VideoFile
            "pdf" -> Icons.Default.PictureAsPdf
            "zip", "rar", "7z", "tar", "gz" -> Icons.Default.Archive
            else -> Icons.Default.Description
        }
    }
}

@Composable
private fun getFileIconColor(entry: ArchiveEntry): androidx.compose.ui.graphics.Color {
    return if (entry.isDirectory) {
        MaterialTheme.colorScheme.primary
    } else {
        when (entry.extension.lowercase()) {
            "jpg", "jpeg", "png", "gif", "bmp", "webp" -> MaterialTheme.colorScheme.secondary
            "mp3", "wav", "flac", "ogg", "m4a" -> MaterialTheme.colorScheme.tertiary
            "mp4", "avi", "mkv", "mov", "wmv" -> MaterialTheme.colorScheme.tertiary
            "pdf" -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
}

private fun countTotalFiles(entries: List<ArchiveEntry>): Int {
    return entries.sumOf { entry ->
        if (entry.isDirectory) {
            countTotalFiles(entry.children)
        } else {
            1
        }
    }
}

private fun calculateTotalSize(entries: List<ArchiveEntry>): Long {
    return entries.sumOf { entry ->
        if (entry.isDirectory) {
            calculateTotalSize(entry.children)
        } else {
            entry.size
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kiloBytes = bytes / 1024.0
    if (kiloBytes < 1024) return "%.1f KB".format(kiloBytes)
    val megaBytes = kiloBytes / 1024.0
    if (megaBytes < 1024) return "%.1f MB".format(megaBytes)
    val gigaBytes = megaBytes / 1024.0
    return "%.1f GB".format(gigaBytes)
}