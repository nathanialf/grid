package com.defnf.grid.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Expressive press feedback for tiles: the corner radius tightens while held and springs back. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun animatedPressCorner(
    interactionSource: InteractionSource,
    rest: Dp = 24.dp,
    pressed: Dp = 12.dp
): Dp {
    val isPressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (isPressed) pressed else rest,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "PressCorner"
    )
    return corner
}
