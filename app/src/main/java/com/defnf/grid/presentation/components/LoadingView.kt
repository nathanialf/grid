package com.defnf.grid.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun LoadingView(
    modifier: Modifier = Modifier,
    message: String = "Loading..."
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WavyCircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun WavyCircularProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float = -1f, // -1 means indeterminate
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Float = 5.dp.value
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WavyProgress")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )
    
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )
    
    // Add smooth animation for progress changes
    val animatedProgress by animateFloatAsState(
        targetValue = if (progress >= 0f) progress.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = EaseOutQuart
        ),
        label = "ProgressAnimation"
    )
    
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = (size.minDimension / 2) - strokeWidth
        val trackColor = Color.Gray.copy(alpha = 0.4f)
        
        if (progress < 0f) {
            // Indeterminate progress - rotate the entire wavy circle
            rotate(rotation, center) {
                drawWavyCircle(
                    center = center,
                    radius = radius,
                    color = color,
                    strokeWidth = strokeWidth,
                    wavePhase = wavePhase
                )
            }
        } else {
            // Determinate progress - draw unified track with wavy progress and flat remaining
            drawUnifiedTrack(
                center = center,
                radius = radius,
                progressColor = color,
                trackColor = trackColor,
                strokeWidth = strokeWidth,
                wavePhase = wavePhase,
                sweepAngle = animatedProgress * 360f
            )
        }
    }
}

@Composable
fun WavyLinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    strokeWidth: Float = 8.dp.value
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WavyLinearProgress")
    
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )
    
    // Add smooth animation for progress changes
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = 300,
            easing = EaseOutQuart
        ),
        label = "ProgressAnimation"
    )
    
    Canvas(modifier = modifier.height(strokeWidth.dp)) {
        val progressWidth = size.width * animatedProgress
        
        // Draw unified track - wavy progress + flat remaining
        drawUnifiedLinearTrack(
            width = size.width,
            height = size.height,
            progressWidth = progressWidth,
            progressColor = color,
            trackColor = backgroundColor,
            strokeWidth = strokeWidth,
            wavePhase = wavePhase
        )
    }
}

private fun DrawScope.drawUnifiedLinearTrack(
    width: Float,
    height: Float,
    progressWidth: Float,
    progressColor: Color,
    trackColor: Color,
    strokeWidth: Float,
    wavePhase: Float
) {
    val y = height / 2
    val waveAmplitude = strokeWidth * 0.6f
    val segments = (width / 1f).toInt().coerceAtLeast(50)
    val stepX = width / segments
    
    // Use fixed wavelength in pixels for consistent appearance
    val wavelengthInPixels = 80f
    
    var previousPoint: Offset? = null
    
    for (i in 0..segments) {
        val x = i * stepX
        val isInProgressSection = x <= progressWidth
        
        val currentY = if (isInProgressSection) {
            // Wavy for progress section
            val wave = sin((2f * PI * x / wavelengthInPixels + wavePhase).toFloat()) * waveAmplitude
            y + wave
        } else {
            // Flat for remaining section
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

private fun DrawScope.drawWavyLine(
    startX: Float,
    endX: Float,
    y: Float,
    color: Color,
    strokeWidth: Float,
    wavePhase: Float,
    waveAmplitude: Float = strokeWidth * 0.3f
) {
    val segments = ((endX - startX) / 1f).toInt().coerceAtLeast(50)
    val stepX = (endX - startX) / segments
    
    // Use fixed wavelength in pixels for consistent appearance across all devices
    val wavelengthInPixels = 80f  // Fixed 80px wavelength for consistent wave appearance
    
    var previousPoint: Offset? = null
    
    for (i in 0..segments) {
        val x = startX + i * stepX
        // Calculate wave using proper wavelength: 2π completes one full wave cycle
        val wave = sin((2f * PI * (x - startX) / wavelengthInPixels + wavePhase).toFloat()) * waveAmplitude
        val currentPoint = Offset(x, y + wave)
        
        previousPoint?.let { prev ->
            drawLine(
                color = color,
                start = prev,
                end = currentPoint,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        
        previousPoint = currentPoint
    }
}

private fun DrawScope.drawUnifiedTrack(
    center: Offset,
    radius: Float,
    progressColor: Color,
    trackColor: Color,
    strokeWidth: Float,
    wavePhase: Float,
    sweepAngle: Float
) {
    val segments = 360
    val angleStep = 360f / segments
    val waveAmplitude = strokeWidth * 0.6f
    val waveFrequency = 10f
    
    var previousPoint: Offset? = null
    
    for (i in 0..segments) {
        val angle = i * angleStep - 90f // Start from top (-90 degrees)
        val angleRad = (angle * PI / 180f).toFloat()
        val isInProgressSection = (angle + 90f) <= sweepAngle
        
        val adjustedRadius = if (isInProgressSection) {
            // Wavy for progress section
            val wave = sin(waveFrequency * angleRad + wavePhase) * waveAmplitude
            radius + wave
        } else {
            // Flat for remaining section
            radius
        }
        
        val x = center.x + adjustedRadius * cos(angleRad)
        val y = center.y + adjustedRadius * sin(angleRad)
        val currentPoint = Offset(x, y)
        
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

private fun DrawScope.drawWavyCircle(
    center: Offset,
    radius: Float,
    color: Color,
    strokeWidth: Float,
    wavePhase: Float
) {
    val segments = 360
    val angleStep = 360f / segments
    val waveAmplitude = strokeWidth * 0.6f
    val waveFrequency = 10f
    
    var previousPoint: Offset? = null
    
    for (i in 0..segments) {
        val angle = i * angleStep
        val angleRad = (angle * PI / 180f).toFloat()
        
        // Add wave effect to the radius
        val wave = sin(waveFrequency * angleRad + wavePhase) * waveAmplitude
        val adjustedRadius = radius + wave
        
        val x = center.x + adjustedRadius * cos(angleRad)
        val y = center.y + adjustedRadius * sin(angleRad)
        val currentPoint = Offset(x, y)
        
        previousPoint?.let { prev ->
            drawLine(
                color = color,
                start = prev,
                end = currentPoint,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        
        previousPoint = currentPoint
    }
}

private fun DrawScope.drawWavyArc(
    center: Offset,
    radius: Float,
    color: Color,
    strokeWidth: Float,
    wavePhase: Float,
    sweepAngle: Float
) {
    val segments = (sweepAngle / 360f * 360f).toInt().coerceAtLeast(1)
    val angleStep = sweepAngle / segments
    val waveAmplitude = strokeWidth * 0.6f
    val waveFrequency = 10f
    
    var previousPoint: Offset? = null
    
    for (i in 0..segments) {
        val angle = i * angleStep - 90f // Start from top (-90 degrees)
        val angleRad = (angle * PI / 180f).toFloat()
        
        // Add wave effect to the radius
        val wave = sin(waveFrequency * angleRad + wavePhase) * waveAmplitude
        val adjustedRadius = radius + wave
        
        val x = center.x + adjustedRadius * cos(angleRad)
        val y = center.y + adjustedRadius * sin(angleRad)
        val currentPoint = Offset(x, y)
        
        previousPoint?.let { prev ->
            drawLine(
                color = color,
                start = prev,
                end = currentPoint,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        
        previousPoint = currentPoint
    }
}