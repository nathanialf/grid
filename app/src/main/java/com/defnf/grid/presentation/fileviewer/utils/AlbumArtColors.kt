package com.defnf.grid.presentation.fileviewer.utils

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * Picks a seed color from album art. Prefers the vibrant swatches (what the eye reads as "the
 * color of the cover") and falls back to the dominant one for muted or monochrome art.
 *
 * Palette work is CPU-bound; call this off the main thread.
 */
fun extractSeedColor(bitmap: Bitmap): Color? = try {
    val palette = Palette.from(bitmap).maximumColorCount(16).generate()
    val swatch = palette.vibrantSwatch
        ?: palette.lightVibrantSwatch
        ?: palette.darkVibrantSwatch
        ?: palette.dominantSwatch
        ?: palette.mutedSwatch
    swatch?.let { Color(it.rgb) }
} catch (e: Exception) {
    null
}

/**
 * Builds a complete Material 3 color scheme from a single seed color, following the tonal-palette
 * model M3 uses for dynamic color: each role is the seed's hue at a fixed tone (CIE L*), with
 * chroma reduced per palette (primary / secondary / tertiary / neutral / neutral-variant).
 *
 * Every role is filled - including the surface container roles - so components such as top app
 * bars, cards and sliders all pick up the album art's color instead of mixing with the app theme.
 */
fun seedColorScheme(seed: Color, isDark: Boolean): ColorScheme {
    val lch = Lch.from(seed)
    // Near-grey art keeps a near-grey theme instead of inventing a hue.
    val primaryChroma = if (lch.c < 8f) lch.c else lch.c.coerceAtLeast(36f)
    val primary = TonalPalette(lch.h, primaryChroma)
    val secondary = TonalPalette(lch.h, (primaryChroma / 3f).coerceAtMost(16f))
    val tertiary = TonalPalette(lch.h + 60f, (primaryChroma / 2f).coerceAtMost(24f))
    val neutral = TonalPalette(lch.h, (primaryChroma / 12f).coerceAtMost(4f))
    val neutralVariant = TonalPalette(lch.h, (primaryChroma / 6f).coerceAtMost(8f))
    val error = TonalPalette(25f, 60f)

    return if (isDark) {
        darkColorScheme(
            primary = primary.tone(80),
            onPrimary = primary.tone(20),
            primaryContainer = primary.tone(30),
            onPrimaryContainer = primary.tone(90),
            inversePrimary = primary.tone(40),
            secondary = secondary.tone(80),
            onSecondary = secondary.tone(20),
            secondaryContainer = secondary.tone(30),
            onSecondaryContainer = secondary.tone(90),
            tertiary = tertiary.tone(80),
            onTertiary = tertiary.tone(20),
            tertiaryContainer = tertiary.tone(30),
            onTertiaryContainer = tertiary.tone(90),
            background = neutral.tone(6),
            onBackground = neutral.tone(90),
            surface = neutral.tone(6),
            onSurface = neutral.tone(90),
            surfaceVariant = neutralVariant.tone(30),
            onSurfaceVariant = neutralVariant.tone(80),
            surfaceTint = primary.tone(80),
            inverseSurface = neutral.tone(90),
            inverseOnSurface = neutral.tone(20),
            error = error.tone(80),
            onError = error.tone(20),
            errorContainer = error.tone(30),
            onErrorContainer = error.tone(90),
            outline = neutralVariant.tone(60),
            outlineVariant = neutralVariant.tone(30),
            scrim = Color.Black,
            surfaceBright = neutral.tone(24),
            surfaceDim = neutral.tone(6),
            surfaceContainerLowest = neutral.tone(4),
            surfaceContainerLow = neutral.tone(10),
            surfaceContainer = neutral.tone(12),
            surfaceContainerHigh = neutral.tone(17),
            surfaceContainerHighest = neutral.tone(22)
        )
    } else {
        lightColorScheme(
            primary = primary.tone(40),
            onPrimary = primary.tone(100),
            primaryContainer = primary.tone(90),
            onPrimaryContainer = primary.tone(10),
            inversePrimary = primary.tone(80),
            secondary = secondary.tone(40),
            onSecondary = secondary.tone(100),
            secondaryContainer = secondary.tone(90),
            onSecondaryContainer = secondary.tone(10),
            tertiary = tertiary.tone(40),
            onTertiary = tertiary.tone(100),
            tertiaryContainer = tertiary.tone(90),
            onTertiaryContainer = tertiary.tone(10),
            background = neutral.tone(98),
            onBackground = neutral.tone(10),
            surface = neutral.tone(98),
            onSurface = neutral.tone(10),
            surfaceVariant = neutralVariant.tone(90),
            onSurfaceVariant = neutralVariant.tone(30),
            surfaceTint = primary.tone(40),
            inverseSurface = neutral.tone(20),
            inverseOnSurface = neutral.tone(95),
            error = error.tone(40),
            onError = error.tone(100),
            errorContainer = error.tone(90),
            onErrorContainer = error.tone(10),
            outline = neutralVariant.tone(50),
            outlineVariant = neutralVariant.tone(80),
            scrim = Color.Black,
            surfaceBright = neutral.tone(98),
            surfaceDim = neutral.tone(87),
            surfaceContainerLowest = neutral.tone(100),
            surfaceContainerLow = neutral.tone(96),
            surfaceContainer = neutral.tone(94),
            surfaceContainerHigh = neutral.tone(92),
            surfaceContainerHighest = neutral.tone(90)
        )
    }
}

/**
 * Animates every role of a color scheme so a theme change (e.g. album art finishing loading)
 * cross-fades instead of snapping.
 */
@Composable
fun animateColorScheme(
    target: ColorScheme,
    animationSpec: AnimationSpec<Color> = tween(durationMillis = 600)
): ColorScheme {
    @Composable
    fun Color.animate(label: String): Color {
        val value by animateColorAsState(this, animationSpec, label = label)
        return value
    }
    return target.copy(
        primary = target.primary.animate("primary"),
        onPrimary = target.onPrimary.animate("onPrimary"),
        primaryContainer = target.primaryContainer.animate("primaryContainer"),
        onPrimaryContainer = target.onPrimaryContainer.animate("onPrimaryContainer"),
        inversePrimary = target.inversePrimary.animate("inversePrimary"),
        secondary = target.secondary.animate("secondary"),
        onSecondary = target.onSecondary.animate("onSecondary"),
        secondaryContainer = target.secondaryContainer.animate("secondaryContainer"),
        onSecondaryContainer = target.onSecondaryContainer.animate("onSecondaryContainer"),
        tertiary = target.tertiary.animate("tertiary"),
        onTertiary = target.onTertiary.animate("onTertiary"),
        tertiaryContainer = target.tertiaryContainer.animate("tertiaryContainer"),
        onTertiaryContainer = target.onTertiaryContainer.animate("onTertiaryContainer"),
        background = target.background.animate("background"),
        onBackground = target.onBackground.animate("onBackground"),
        surface = target.surface.animate("surface"),
        onSurface = target.onSurface.animate("onSurface"),
        surfaceVariant = target.surfaceVariant.animate("surfaceVariant"),
        onSurfaceVariant = target.onSurfaceVariant.animate("onSurfaceVariant"),
        surfaceTint = target.surfaceTint.animate("surfaceTint"),
        inverseSurface = target.inverseSurface.animate("inverseSurface"),
        inverseOnSurface = target.inverseOnSurface.animate("inverseOnSurface"),
        error = target.error.animate("error"),
        onError = target.onError.animate("onError"),
        errorContainer = target.errorContainer.animate("errorContainer"),
        onErrorContainer = target.onErrorContainer.animate("onErrorContainer"),
        outline = target.outline.animate("outline"),
        outlineVariant = target.outlineVariant.animate("outlineVariant"),
        surfaceBright = target.surfaceBright.animate("surfaceBright"),
        surfaceDim = target.surfaceDim.animate("surfaceDim"),
        surfaceContainerLowest = target.surfaceContainerLowest.animate("surfaceContainerLowest"),
        surfaceContainerLow = target.surfaceContainerLow.animate("surfaceContainerLow"),
        surfaceContainer = target.surfaceContainer.animate("surfaceContainer"),
        surfaceContainerHigh = target.surfaceContainerHigh.animate("surfaceContainerHigh"),
        surfaceContainerHighest = target.surfaceContainerHighest.animate("surfaceContainerHighest")
    )
}

/** A hue/chroma pair that can be rendered at any tone (CIE L*, 0-100). */
private class TonalPalette(private val hue: Float, private val chroma: Float) {
    fun tone(tone: Int): Color = Lch(tone.toFloat(), chroma, hue).toSrgbInGamut()
}

/** CIE LCh(ab), D65 white point. */
private data class Lch(val l: Float, val c: Float, val h: Float) {

    /**
     * Converts to sRGB, reducing chroma until the color fits the gamut. Keeping L* and hue fixed
     * (rather than clamping channels) preserves the tone contrast M3 roles rely on.
     */
    fun toSrgbInGamut(): Color {
        if (l <= 0f) return Color.Black
        if (l >= 100f) return Color.White
        toSrgbOrNull(c)?.let { return it }
        var low = 0f
        var high = c
        repeat(12) {
            val mid = (low + high) / 2f
            if (toSrgbOrNull(mid) != null) low = mid else high = mid
        }
        return toSrgbOrNull(low) ?: Color(l / 100f, l / 100f, l / 100f)
    }

    private fun toSrgbOrNull(chroma: Float): Color? {
        val rad = Math.toRadians(h.toDouble())
        val a = chroma * cos(rad)
        val b = chroma * sin(rad)
        val fy = (l + 16.0) / 116.0
        val fx = fy + a / 500.0
        val fz = fy - b / 200.0
        val x = WHITE_X * labInv(fx)
        val y = WHITE_Y * labInv(fy)
        val z = WHITE_Z * labInv(fz)
        val r = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
        val g = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
        val bl = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z
        val eps = 1e-4
        if (r < -eps || r > 1 + eps || g < -eps || g > 1 + eps || bl < -eps || bl > 1 + eps) return null
        return Color(
            red = gammaEncode(r.coerceIn(0.0, 1.0)),
            green = gammaEncode(g.coerceIn(0.0, 1.0)),
            blue = gammaEncode(bl.coerceIn(0.0, 1.0))
        )
    }

    companion object {
        private const val WHITE_X = 0.95047
        private const val WHITE_Y = 1.0
        private const val WHITE_Z = 1.08883

        fun from(color: Color): Lch {
            val r = gammaDecode(color.red)
            val g = gammaDecode(color.green)
            val b = gammaDecode(color.blue)
            val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / WHITE_X
            val y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * b) / WHITE_Y
            val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / WHITE_Z
            val fx = labF(x)
            val fy = labF(y)
            val fz = labF(z)
            val l = 116 * fy - 16
            val a = 500 * (fx - fy)
            val bb = 200 * (fy - fz)
            val hue = Math.toDegrees(atan2(bb, a)).let { if (it < 0) it + 360 else it }
            return Lch(l.toFloat(), hypot(a, bb).toFloat(), hue.toFloat())
        }

        private fun labF(t: Double): Double =
            if (t > 216.0 / 24389.0) cbrt(t) else (24389.0 / 27.0 * t + 16) / 116

        private fun labInv(f: Double): Double {
            val f3 = f * f * f
            return if (f3 > 216.0 / 24389.0) f3 else (116 * f - 16) / (24389.0 / 27.0)
        }

        private fun gammaDecode(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.04045) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
        }

        private fun gammaEncode(v: Double): Float =
            (if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055).toFloat()
    }
}
