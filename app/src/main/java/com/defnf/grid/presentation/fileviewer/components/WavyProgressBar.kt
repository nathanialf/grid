package com.defnf.grid.presentation.fileviewer.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.*

// Wavy progress bar component with scrubbing support
@Composable
fun WavyProgressBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    strokeWidth: Float = 8.dp.value
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(progress) }
    
    LaunchedEffect(progress) {
        if (!isDragging) {
            dragProgress = progress
        }
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "WavyProgressBarAnimation")
    
    // Only animate wave when playing and not dragging
    val shouldAnimate = isPlaying && !isDragging
    
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (shouldAnimate) 2f * PI.toFloat() else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (shouldAnimate) 1500 else 1, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )
    
    // Smooth amplitude transition animation
    val targetAmplitude = if (shouldAnimate) strokeWidth * 0.6f else 0f
    val animatedAmplitude by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "AmplitudeTransition"
    )
    
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        dragProgress = newProgress
                        onSeek(newProgress)
                    },
                    onDrag = { change, _ ->
                        val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        dragProgress = newProgress
                        onSeek(newProgress)
                    },
                    onDragEnd = {
                        isDragging = false
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    dragProgress = newProgress
                    onSeek(newProgress)
                }
            }
    ) {
        val progressWidth = size.width * dragProgress
        
        // Draw unified track - wavy progress + flat remaining (same as upload progress bar)
        drawUnifiedLinearTrack(
            width = size.width,
            height = size.height,
            progressWidth = progressWidth,
            progressColor = color,
            trackColor = backgroundColor,
            strokeWidth = strokeWidth,
            wavePhase = wavePhase,
            animatedAmplitude = animatedAmplitude
        )
        
        // Draw thumb at current position
        drawCircle(
            color = color,
            radius = 6.dp.toPx(),
            center = Offset(progressWidth, size.height / 2f)
        )
    }
}

// Same implementation as LoadingView.kt for consistency
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawUnifiedLinearTrack(
    width: Float,
    height: Float,
    progressWidth: Float,
    progressColor: Color,
    trackColor: Color,
    strokeWidth: Float,
    wavePhase: Float,
    animatedAmplitude: Float
) {
    val y = height / 2
    val segments = (width / 1f).toInt().coerceAtLeast(50)
    val stepX = width / segments
    
    // Use fixed wavelength in pixels for consistent appearance
    val wavelengthInPixels = 80f
    
    var previousPoint: Offset? = null
    
    for (i in 0..segments) {
        val x = i * stepX
        val isInProgressSection = x <= progressWidth
        
        val currentY = if (isInProgressSection && animatedAmplitude > 0f) {
            // Wavy for progress section when playing
            val wave = sin((2f * PI * x / wavelengthInPixels + wavePhase).toFloat()) * animatedAmplitude
            y + wave
        } else {
            // Flat for remaining section or when paused/dragging
            y
        }
        
        val currentPoint = Offset(x, currentY)
        
        previousPoint?.let { prev ->
            val segmentColor = if (isInProgressSection) progressColor else trackColor
            drawLine(
                color = segmentColor,
                start = prev,
                end = currentPoint,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        
        previousPoint = currentPoint
    }
}