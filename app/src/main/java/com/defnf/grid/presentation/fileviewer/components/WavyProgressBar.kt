package com.defnf.grid.presentation.fileviewer.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.*

/**
 * Material 3 expressive style wavy seek bar.
 *
 * Dragging only moves the thumb locally; [onSeek] fires once when the finger lifts (or on tap), so
 * scrubbing doesn't flood the media session with seeks and stutter the audio.
 */
@Composable
fun WavyProgressBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    strokeWidth: Dp = 4.dp
) {
    val haptics = LocalHapticFeedback.current
    val currentOnSeek by rememberUpdatedState(onSeek)
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(progress) }
    // Holds the thumb where the user released it until the player reports the new position,
    // so it doesn't jump back for a frame or two.
    var pendingSeek by remember { mutableStateOf<Float?>(null) }
    val currentProgress by rememberUpdatedState(progress)
    LaunchedEffect(pendingSeek) {
        val target = pendingSeek ?: return@LaunchedEffect
        // Release once the player reports the new position, or after a second regardless
        withTimeoutOrNull(1_000) {
            snapshotFlow { currentProgress }.first { abs(it - target) < 0.01f }
        }
        pendingSeek = null
    }
    val shownProgress = when {
        isDragging -> dragProgress
        else -> pendingSeek ?: progress.coerceIn(0f, 1f)
    }
    val seekTo: (Float) -> Unit = { target ->
        pendingSeek = target
        currentOnSeek(target)
    }

    // The wave only travels while playing; when paused no frames are requested at all.
    val shouldAnimate = isPlaying && !isDragging
    var wavePhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(shouldAnimate) {
        if (!shouldAnimate) return@LaunchedEffect
        var lastFrame = withFrameNanos { it }
        while (true) {
            withFrameNanos { frame ->
                val elapsed = (frame - lastFrame) / 1_000_000_000f
                lastFrame = frame
                wavePhase = (wavePhase + elapsed * WAVE_SPEED) % (2f * PI.toFloat())
            }
        }
    }
    val amplitudeFraction by animateFloatAsState(
        targetValue = if (shouldAnimate) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "AmplitudeTransition"
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.6f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ThumbScale"
    )

    val wavePath = remember { Path() }

    Canvas(
        modifier = modifier
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(shownProgress, 0f..1f)
                setProgress { target ->
                    seekTo(target.coerceIn(0f, 1f))
                    true
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        seekTo(dragProgress)
                        isDragging = false
                    },
                    onDragCancel = { isDragging = false }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    seekTo((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        val stroke = strokeWidth.toPx()
        val centerY = size.height / 2f
        val progressX = size.width * shownProgress
        val amplitude = WAVE_AMPLITUDE.toPx() * amplitudeFraction
        val wavelength = WAVE_LENGTH.toPx()
        val gap = TRACK_GAP.toPx()

        // Remaining (inactive) track, separated from the active part by a small gap.
        val inactiveStart = (progressX + gap).coerceAtMost(size.width)
        if (inactiveStart < size.width) {
            drawLine(
                color = backgroundColor,
                start = Offset(inactiveStart, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }

        // Active track: one path instead of a line per pixel.
        if (progressX > 0f) {
            wavePath.reset()
            wavePath.moveTo(0f, centerY + amplitude * sin(wavePhase))
            if (amplitude > 0f) {
                val step = 2.dp.toPx()
                var x = step
                while (x < progressX) {
                    wavePath.lineTo(x, centerY + amplitude * sin(2f * PI.toFloat() * x / wavelength + wavePhase))
                    x += step
                }
            }
            wavePath.lineTo(
                progressX,
                centerY + amplitude * sin(2f * PI.toFloat() * progressX / wavelength + wavePhase)
            )
            drawPath(wavePath, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }

        drawCircle(
            color = color,
            radius = THUMB_RADIUS.toPx() * thumbScale,
            center = Offset(progressX, centerY)
        )
    }
}

private const val WAVE_SPEED = 4f // radians per second
private val WAVE_AMPLITUDE = 3.dp
private val WAVE_LENGTH = 32.dp
private val TRACK_GAP = 4.dp
private val THUMB_RADIUS = 6.dp
