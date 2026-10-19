package com.defnf.grid.presentation.fileviewer.models

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color

data class AudioMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val year: String? = null,
    val albumArt: Bitmap? = null,
    // Seed for the player's Material color scheme, picked from the album art
    val seedColor: Color? = null
)
