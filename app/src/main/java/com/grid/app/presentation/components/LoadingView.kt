package com.grid.app.presentation.components

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
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Float = 4.dp.value,
    progress: Float? = null // Optional progress value for determinate progress
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
    
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = (size.minDimension / 2) - strokeWidth
        
        rotate(rotation, center) {
            drawWavyCircle(
                center = center,
                radius = radius,
                color = color,
                strokeWidth = strokeWidth,
                wavePhase = wavePhase
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
        
        // Draw background
        drawWavyLine(
            startX = 0f,
            endX = size.width,
            y = size.height / 2,
            color = backgroundColor,
            strokeWidth = strokeWidth,
            wavePhase = wavePhase,
            waveAmplitude = strokeWidth * 0.6f
        )
        
        // Draw progress
        if (progressWidth > 0f) {
            drawWavyLine(
                startX = 0f,
                endX = progressWidth,
                y = size.height / 2,
                color = color,
                strokeWidth = strokeWidth,
                wavePhase = wavePhase,
                waveAmplitude = strokeWidth * 0.6f
            )
        }
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

private fun DrawScope.drawWavyCircle(
    center: Offset,
    radius: Float,
    color: Color,
    strokeWidth: Float,
    wavePhase: Float
) {
    val segments = 360
    val angleStep = 360f / segments
    val waveAmplitude = strokeWidth * 0.8f
    val waveFrequency = 6f
    
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