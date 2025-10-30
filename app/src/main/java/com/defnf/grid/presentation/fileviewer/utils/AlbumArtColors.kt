package com.defnf.grid.presentation.fileviewer.utils

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette

/**
 * Represents colors extracted from album art for theming
 */
@Stable
data class AlbumArtColorScheme(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color
)

/**
 * Extracts a Material 3 color scheme from album art using Android's Palette API
 */
fun extractColorsFromAlbumArt(bitmap: Bitmap?, isDarkTheme: Boolean): AlbumArtColorScheme? {
    if (bitmap == null) return null
    
    return try {
        val palette = Palette.from(bitmap).generate()
        
        // Extract swatches with fallbacks
        val vibrantSwatch = palette.vibrantSwatch
        val mutedSwatch = palette.mutedSwatch
        val dominantSwatch = palette.dominantSwatch
        val darkVibrantSwatch = palette.darkVibrantSwatch
        val lightVibrantSwatch = palette.lightVibrantSwatch
        
        // Choose primary color from available swatches
        val primaryColor = when {
            vibrantSwatch != null -> Color(vibrantSwatch.rgb)
            dominantSwatch != null -> Color(dominantSwatch.rgb)
            mutedSwatch != null -> Color(mutedSwatch.rgb)
            else -> return null
        }
        
        // Choose secondary color
        val secondaryColor = when {
            mutedSwatch != null -> Color(mutedSwatch.rgb)
            darkVibrantSwatch != null -> Color(darkVibrantSwatch.rgb)
            lightVibrantSwatch != null -> Color(lightVibrantSwatch.rgb)
            else -> primaryColor
        }
        
        // Calculate contrast colors
        val onPrimaryColor = if (isDarkTheme) {
            if (isColorLight(primaryColor)) Color.Black else Color.White
        } else {
            if (isColorLight(primaryColor)) Color.Black else Color.White
        }
        
        val onSecondaryColor = if (isDarkTheme) {
            if (isColorLight(secondaryColor)) Color.Black else Color.White
        } else {
            if (isColorLight(secondaryColor)) Color.Black else Color.White
        }
        
        // Create surface colors based on theme
        val backgroundColor = if (isDarkTheme) Color(0xFF121212) else Color(0xFFFFFBFE)
        val surfaceColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFFFFBFE)
        val onSurfaceColor = if (isDarkTheme) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)
        
        // Create container colors (lighter/darker versions of primary)
        val primaryContainer = if (isDarkTheme) {
            darkenColor(primaryColor, 0.3f)
        } else {
            lightenColor(primaryColor, 0.8f)
        }
        
        val onPrimaryContainer = if (isDarkTheme) {
            lightenColor(primaryColor, 0.8f)
        } else {
            darkenColor(primaryColor, 0.3f)
        }
        
        val surfaceVariant = if (isDarkTheme) Color(0xFF49454F) else Color(0xFFE7E0EC)
        val onSurfaceVariant = if (isDarkTheme) Color(0xFFCAC4D0) else Color(0xFF49454F)
        
        AlbumArtColorScheme(
            primary = primaryColor,
            onPrimary = onPrimaryColor,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondaryColor,
            onSecondary = onSecondaryColor,
            background = backgroundColor,
            surface = surfaceColor,
            onSurface = onSurfaceColor,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant
        )
    } catch (e: Exception) {
        null
    }
}

/**
 * Converts AlbumArtColorScheme to Material3 ColorScheme
 */
fun AlbumArtColorScheme.toMaterial3ColorScheme(fallbackColorScheme: ColorScheme): ColorScheme {
    return if (fallbackColorScheme.primary == lightColorScheme().primary) {
        // Light theme
        lightColorScheme(
            primary = this.primary,
            onPrimary = this.onPrimary,
            primaryContainer = this.primaryContainer,
            onPrimaryContainer = this.onPrimaryContainer,
            secondary = this.secondary,
            onSecondary = this.onSecondary,
            background = this.background,
            surface = this.surface,
            onSurface = this.onSurface,
            surfaceVariant = this.surfaceVariant,
            onSurfaceVariant = this.onSurfaceVariant
        )
    } else {
        // Dark theme
        darkColorScheme(
            primary = this.primary,
            onPrimary = this.onPrimary,
            primaryContainer = this.primaryContainer,
            onPrimaryContainer = this.onPrimaryContainer,
            secondary = this.secondary,
            onSecondary = this.onSecondary,
            background = this.background,
            surface = this.surface,
            onSurface = this.onSurface,
            surfaceVariant = this.surfaceVariant,
            onSurfaceVariant = this.onSurfaceVariant
        )
    }
}

/**
 * Determines if a color is light or dark
 */
private fun isColorLight(color: Color): Boolean {
    val rgb = color.toArgb()
    val red = (rgb shr 16) and 0xFF
    val green = (rgb shr 8) and 0xFF
    val blue = rgb and 0xFF
    
    // Calculate luminance
    val luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255
    return luminance > 0.5
}

/**
 * Lightens a color by the specified factor
 */
private fun lightenColor(color: Color, factor: Float): Color {
    val rgb = color.toArgb()
    val red = ((rgb shr 16) and 0xFF)
    val green = ((rgb shr 8) and 0xFF)
    val blue = (rgb and 0xFF)
    
    val newRed = (red + (255 - red) * factor).coerceIn(0f, 255f).toInt()
    val newGreen = (green + (255 - green) * factor).coerceIn(0f, 255f).toInt()
    val newBlue = (blue + (255 - blue) * factor).coerceIn(0f, 255f).toInt()
    
    return Color((0xFF shl 24) or (newRed shl 16) or (newGreen shl 8) or newBlue)
}

/**
 * Darkens a color by the specified factor
 */
private fun darkenColor(color: Color, factor: Float): Color {
    val rgb = color.toArgb()
    val red = ((rgb shr 16) and 0xFF)
    val green = ((rgb shr 8) and 0xFF)
    val blue = (rgb and 0xFF)
    
    val newRed = (red * (1 - factor)).coerceIn(0f, 255f).toInt()
    val newGreen = (green * (1 - factor)).coerceIn(0f, 255f).toInt()
    val newBlue = (blue * (1 - factor)).coerceIn(0f, 255f).toInt()
    
    return Color((0xFF shl 24) or (newRed shl 16) or (newGreen shl 8) or newBlue)
}