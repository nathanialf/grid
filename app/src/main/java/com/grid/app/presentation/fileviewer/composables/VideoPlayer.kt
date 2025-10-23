package com.grid.app.presentation.fileviewer.composables

import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.grid.app.presentation.fileviewer.VideoPlayerService
import com.grid.app.presentation.fileviewer.MediaControllerManager
import com.grid.app.presentation.fileviewer.components.WavyProgressBar
import com.grid.app.presentation.fileviewer.utils.formatTime
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun VideoPlayer(
    file: File,
    fileName: String? = null,
    connectionId: String? = null,
    remotePath: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    
    // Display name - use original fileName if available, otherwise file name
    val displayName = fileName ?: file.name

    // Auto-hide controls after 5 seconds
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(5000)
            showControls = false
        }
    }

    // Initialize MediaController with video service
    LaunchedEffect(file.absolutePath) { // Use file path as key to prevent recreation on rotation
        try {
            isLoading = true
            error = null
            
            // Get or create MediaController through manager
            val controller = MediaControllerManager.getVideoController(
                context, file, fileName, connectionId, remotePath
            )
            
            if (controller != null) {
                // Only set media if this is a new controller or different file
                if (controller.currentMediaItem?.localConfiguration?.uri?.toString() != file.toURI().toString()) {
                    // Create media item with metadata
                    val mediaMetadata = MediaMetadata.Builder()
                        .setTitle(displayName)
                        .setDisplayTitle(displayName)
                        .build()
                    
                    val mediaItem = MediaItem.Builder()
                        .setUri(file.toURI().toString())
                        .setMediaMetadata(mediaMetadata)
                        .build()
                    
                    controller.setMediaItem(mediaItem)
                    controller.prepare()
                }
                
                mediaController = controller
                duration = controller.duration.coerceAtLeast(0L)
                isLoading = false
            } else {
                error = "Error connecting to media service"
                isLoading = false
            }
            
        } catch (e: Exception) {
            error = "Error loading video: ${e.message}"
            isLoading = false
        }
    }

    // Update progress
    LaunchedEffect(mediaController) {
        while (true) {
            mediaController?.let { player ->
                currentPosition = player.currentPosition
                duration = player.duration.coerceAtLeast(0L)
                isPlaying = player.isPlaying
            }
            delay(100) // Update frequently for smooth progress
        }
    }

    // Cleanup - Don't release here since it's managed by MediaControllerManager
    DisposableEffect(file) {
        onDispose {
            // MediaController is managed by MediaControllerManager
            // Don't release it here to maintain playback across activities
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
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
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoFile,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Failed to load video",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    
                    if (error!!.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            mediaController != null -> {
                // Video player without default controls - maintain playback state across rotations
                key(file.absolutePath) { // Use file path as key to prevent recreation
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            PlayerView(context).apply {
                                player = mediaController
                                useController = false // Disable default controls
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                keepScreenOn = true // Prevent screen from turning off during video playback
                            }
                        },
                        update = { playerView ->
                            // Update player reference but don't recreate
                            if (playerView.player != mediaController) {
                                playerView.player = mediaController
                            }
                            // Update screen wake lock based on playback state
                            playerView.keepScreenOn = isPlaying
                        }
                    )
                }
                
                // Tap to show controls
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { showControls = true }
                )
                
                // Custom overlay controls with wavy progress bar
                if (showControls) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                // File name - use original name for clarity
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // Time display
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatTime(currentPosition.toInt()),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = formatTime(duration.toInt()),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Wavy progress bar with scrubbing
                                WavyProgressBar(
                                    progress = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                                    isPlaying = isPlaying,
                                    onSeek = { progress ->
                                        mediaController?.let { player ->
                                            val seekPosition = (progress * duration).toLong()
                                            player.seekTo(seekPosition)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Play/Pause control
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Card(
                                        modifier = Modifier.size(56.dp),
                                        shape = CircleShape,
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ),
                                        onClick = {
                                            mediaController?.let { player ->
                                                if (isPlaying) {
                                                    player.pause()
                                                } else {
                                                    player.play()
                                                }
                                            }
                                        }
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlaying) "Pause" else "Play",
                                                modifier = Modifier.size(28.dp),
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No video content to display")
                }
            }
        }
    }
}