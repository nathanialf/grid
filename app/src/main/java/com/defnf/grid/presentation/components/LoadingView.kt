package com.defnf.grid.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Full-area loading state using the Material 3 Expressive shape-morphing loading indicator. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        LoadingIndicator(modifier = Modifier.size(64.dp))

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Circular progress: Material 3's wavy indicator, determinate when [progress] is 0..1 and
 * indeterminate when it is negative.
 */
@Composable
fun WavyCircularProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float = -1f, // -1 means indeterminate
    color: Color = MaterialTheme.colorScheme.primary
) {
    if (progress < 0f) {
        CircularWavyProgressIndicator(modifier = modifier, color = color)
    } else {
        // Transfers report in steps; ease between them instead of jumping
        val animatedProgress by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "CircularProgress"
        )
        CircularWavyProgressIndicator(
            progress = { animatedProgress },
            modifier = modifier,
            color = color
        )
    }
}

/** Linear determinate progress using Material 3's wavy indicator. */
@Composable
fun WavyLinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.secondaryContainer
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "LinearProgress"
    )
    LinearWavyProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier,
        color = color,
        trackColor = backgroundColor
    )
}
