package com.defnf.grid.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.defnf.grid.R

/**
 * Grid's launcher icon, drawn from the same adaptive-icon layers the launcher uses so the two never
 * drift apart. A launcher shows the middle 72 of each layer's 108 units through its mask, so the
 * layers are drawn at 1.5x [size] and clipped to [shape].
 */
@Composable
fun GridAppIcon(
    shape: Shape,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_background),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.5f)
        )
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Grid app icon",
            modifier = Modifier.requiredSize(size * 1.5f)
        )
    }
}
