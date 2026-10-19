package com.defnf.grid.presentation.fileviewer.composables

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.defnf.grid.presentation.fileviewer.MediaControllerManager
import com.defnf.grid.presentation.fileviewer.components.WavyProgressBar
import com.defnf.grid.presentation.fileviewer.models.AudioMetadata
import com.defnf.grid.presentation.fileviewer.utils.extractAudioMetadata
import com.defnf.grid.presentation.fileviewer.utils.formatTime
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import java.io.File

private const val SEEK_INCREMENT_MS = 10_000L

/**
 * Full-screen audio player.
 *
 * The player itself only uses the ambient [MaterialTheme]; the color seed picked from the album
 * art is reported through [onThemeSeedChanged] so the host can theme the whole screen (top bar,
 * background and controls) rather than just this composable.
 */
@Composable
fun AudioPlayer(
    file: File,
    modifier: Modifier = Modifier,
    fileName: String? = null,
    connectionId: String? = null,
    remotePath: String? = null,
    onThemeSeedChanged: (Color?) -> Unit = {}
) {
    val context = LocalContext.current
    val currentOnThemeSeedChanged by rememberUpdatedState(onThemeSeedChanged)
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var metadata by remember { mutableStateOf<AudioMetadata?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(file.absolutePath, retryKey) {
        error = null
        try {
            // Connect to the playback service while the tags and cover are being read.
            val controllerJob = async {
                MediaControllerManager.getAudioController(context, file, fileName, connectionId, remotePath)
            }
            val loaded = extractAudioMetadata(file)
            metadata = loaded
            currentOnThemeSeedChanged(loaded.seedColor)

            val controller = controllerJob.await()
            if (controller == null) {
                error = "Couldn't connect to the media service"
                return@LaunchedEffect
            }
            // Only set media if this is a new controller or different file
            if (controller.currentMediaItem?.localConfiguration?.uri?.toString() != file.toURI().toString()) {
                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(loaded.title ?: file.nameWithoutExtension)
                    .setArtist(loaded.artist ?: "Unknown Artist")
                    .setAlbumTitle(loaded.album ?: "Unknown Album")
                    .build()
                controller.setMediaItem(
                    MediaItem.Builder()
                        .setUri(file.toURI().toString())
                        .setMediaMetadata(mediaMetadata)
                        .build()
                )
                controller.playWhenReady = true
                controller.prepare()
            }
            mediaController = controller
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = "Error loading audio: ${e.message}"
        }
    }

    val playback = rememberPlaybackState(mediaController)

    AnimatedContent(
        targetState = when {
            error != null -> AudioScreenState.Error
            metadata == null -> AudioScreenState.Loading
            else -> AudioScreenState.Ready
        },
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) },
        modifier = modifier.fillMaxSize(),
        label = "AudioPlayerContent"
    ) { state ->
        when (state) {
            AudioScreenState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            AudioScreenState.Error -> ErrorState(
                message = error.orEmpty(),
                onRetry = { retryKey++ }
            )
            AudioScreenState.Ready -> NowPlaying(
                metadata = metadata ?: AudioMetadata(),
                fallbackTitle = fileName?.substringBeforeLast('.') ?: file.nameWithoutExtension,
                controller = mediaController,
                playback = playback
            )
        }
    }
}

private enum class AudioScreenState { Loading, Error, Ready }

/** Player state driven by [Player.Listener] callbacks instead of polling every field. */
@Stable
private class PlaybackState {
    var isPlaying by mutableStateOf(false)
    var durationMs by mutableLongStateOf(0L)
    var positionMs by mutableLongStateOf(0L)
}

@Composable
private fun rememberPlaybackState(controller: MediaController?): PlaybackState {
    val state = remember { PlaybackState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose { }
        fun sync(player: Player) {
            state.isPlaying = player.isPlaying
            state.durationMs = player.duration.coerceAtLeast(0L)
            state.positionMs = player.currentPosition
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync(player)
        }
        sync(controller)
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }

    // Position has no callback; poll it only while playing and while the screen is visible.
    LaunchedEffect(controller, state.isPlaying) {
        if (controller == null || !state.isPlaying) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                state.positionMs = controller.currentPosition
                delay(200)
            }
        }
    }
    return state
}

@Composable
private fun NowPlaying(
    metadata: AudioMetadata,
    fallbackTitle: String,
    controller: MediaController?,
    playback: PlaybackState
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArt(
                    metadata = metadata,
                    isPlaying = playback.isPlaying,
                    size = minOf(maxHeight, maxWidth * 0.45f)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    TrackInfo(metadata, fallbackTitle)
                    Controls(controller, playback)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AlbumArt(
                        metadata = metadata,
                        isPlaying = playback.isPlaying,
                        size = minOf(maxWidth, maxHeight * 0.5f)
                    )
                }
                Spacer(Modifier.height(24.dp))
                TrackInfo(metadata, fallbackTitle)
                Spacer(Modifier.height(24.dp))
                Controls(controller, playback)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AlbumArt(metadata: AudioMetadata, isPlaying: Boolean, size: Dp) {
    // Art settles back slightly while paused, like a record lifting off the platter.
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.9f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "AlbumArtScale"
    )
    val shape = MaterialTheme.shapes.extraLarge
    val art = metadata.albumArt
    val imageBitmap = remember(art) { art?.asImageBitmap() }

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation = 12.dp, shape = shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Album art",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(size * 0.35f),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun TrackInfo(metadata: AudioMetadata, fallbackTitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = metadata.title ?: fallbackTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        metadata.artist?.let { artist ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val albumLine = listOfNotNull(metadata.album, metadata.year).joinToString(" · ")
        if (albumLine.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = albumLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun Controls(controller: MediaController?, playback: PlaybackState) {
    val haptics = LocalHapticFeedback.current
    val enabled = controller != null
    val duration = playback.durationMs

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        WavyProgressBar(
            progress = if (duration > 0) playback.positionMs.toFloat() / duration else 0f,
            isPlaying = playback.isPlaying,
            onSeek = { fraction ->
                controller?.let { player ->
                    val target = (fraction * duration).toLong()
                    player.seekTo(target)
                    playback.positionMs = target
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(playback.positionMs.toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatTime(duration.toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = {
                    controller?.let { player ->
                        val target = (player.currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(0L)
                        player.seekTo(target)
                        playback.positionMs = target
                    }
                },
                enabled = enabled,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Replay10, contentDescription = "Back 10 seconds")
            }

            // Expressive shape morph: round while paused, squircle while playing.
            val corner by animateDpAsState(
                targetValue = if (playback.isPlaying) 28.dp else 44.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "PlayButtonCorner"
            )
            FilledIconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    controller?.let { player ->
                        if (player.isPlaying) player.pause() else player.play()
                    }
                },
                enabled = enabled,
                shape = RoundedCornerShape(corner),
                modifier = Modifier.size(88.dp)
            ) {
                Crossfade(targetState = playback.isPlaying, label = "PlayPauseIcon") { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            FilledTonalIconButton(
                onClick = {
                    controller?.let { player ->
                        val end = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                        val target = (player.currentPosition + SEEK_INCREMENT_MS).coerceAtMost(end)
                        player.seekTo(target)
                        playback.positionMs = target
                    }
                },
                enabled = enabled,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Forward10, contentDescription = "Forward 10 seconds")
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onRetry) { Text("Retry") }
    }
}
