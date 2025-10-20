package com.grid.app.presentation.fileviewer.composables

import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.grid.app.presentation.fileviewer.AudioPlayerService
import com.grid.app.presentation.fileviewer.MediaControllerManager
import com.grid.app.presentation.fileviewer.components.WavyProgressBar
import com.grid.app.presentation.fileviewer.models.AudioMetadata
import com.grid.app.presentation.fileviewer.utils.extractAudioMetadata
import com.grid.app.presentation.fileviewer.utils.formatTime
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun AudioPlayer(
    file: File,
    modifier: Modifier = Modifier,
    fileName: String? = null,
    connectionId: String? = null,
    remotePath: String? = null
) {
    val context = LocalContext.current
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var audioMetadata by remember { mutableStateOf<AudioMetadata>(AudioMetadata()) }
    
    // Initialize MediaController with streaming support
    LaunchedEffect(file.absolutePath) {
        try {
            isLoading = true
            error = null
            
            // Extract metadata first
            val metadata = extractAudioMetadata(file)
            audioMetadata = metadata
            
            // Get or create MediaController through manager
            val controller = MediaControllerManager.getAudioController(
                context, file, fileName, connectionId, remotePath
            )
            
            if (controller != null) {
                // Only set media if this is a new controller or different file
                if (controller.currentMediaItem?.localConfiguration?.uri?.toString() != file.toURI().toString()) {
                    // Create media item with metadata
                    val mediaMetadata = MediaMetadata.Builder()
                        .setTitle(metadata.title ?: file.nameWithoutExtension)
                        .setArtist(metadata.artist ?: "Unknown Artist")
                        .setAlbumTitle(metadata.album ?: "Unknown Album")
                        .build()
                    
                    val mediaItem = MediaItem.Builder()
                        .setUri(file.toURI().toString())
                        .setMediaMetadata(mediaMetadata)
                        .build()
                    
                    controller.setMediaItem(mediaItem)
                    controller.prepare()
                    
                    // Auto-play when ready if not already playing
                    if (!controller.isPlaying) {
                        controller.play()
                    }
                }
                
                mediaController = controller
                duration = controller.duration.coerceAtLeast(0L)
                isLoading = false
            } else {
                error = "Error connecting to media service"
                isLoading = false
            }
            
        } catch (e: Exception) {
            error = "Error loading audio: ${e.message}"
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
            delay(100) // Update more frequently for smooth progress
        }
    }
    
    // Cleanup - Don't release here since it's managed by MediaControllerManager
    DisposableEffect(file) {
        onDispose {
            // MediaController is managed by MediaControllerManager
            // Don't release it here to maintain playback across activities
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading audio...")
            }
            error != null -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
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
            else -> {
                // Album art or placeholder
                Card(
                    modifier = Modifier.size(200.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    val albumArt = audioMetadata.albumArt
                    if (albumArt != null) {
                        androidx.compose.foundation.Image(
                            bitmap = albumArt.asImageBitmap(),
                            contentDescription = "Album Art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Audio file",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Track metadata
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = audioMetadata.title ?: file.nameWithoutExtension,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                    
                    audioMetadata.artist?.let { artist ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    audioMetadata.album?.let { album ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = album,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    audioMetadata.year?.let { year ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = year,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Progress section with wavy progress bar
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Time display
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(currentPosition.toInt()),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = formatTime(duration.toInt()),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Wavy progress bar with scrubbing
                        WavyProgressBar(
                            progress = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onSeek = { progress ->
                                mediaController?.let { player ->
                                    val seekPosition = (progress * duration).toLong()
                                    player.seekTo(seekPosition)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Stop button
                    IconButton(
                        onClick = {
                            mediaController?.let { player ->
                                player.pause()
                                player.seekTo(0)
                                isPlaying = false
                            }
                        },
                        enabled = mediaController != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    // Play/Pause button - circular
                    Card(
                        modifier = Modifier.size(64.dp),
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
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}