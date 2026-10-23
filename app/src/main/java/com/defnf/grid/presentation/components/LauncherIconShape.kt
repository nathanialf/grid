package com.defnf.grid.presentation.components

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/**
 * The mask the system puts on adaptive launcher icons (circle, squircle, rounded square, ... depending
 * on the device and its icon shape setting), so file, folder and connection icons match the home
 * screen's. Adaptive icons arrived in Android 8.0; before that it falls back to an expressive cookie.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun launcherIconShape(): Shape =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) LauncherIconShape else MaterialShapes.Cookie4Sided.toShape()

@RequiresApi(Build.VERSION_CODES.O)
private object LauncherIconShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val mask = AdaptiveIconDrawable(ColorDrawable(), ColorDrawable()).apply {
            setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
        }.iconMask
        return Outline.Generic(mask.asComposePath())
    }
}
