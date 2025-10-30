package com.defnf.grid.presentation.fileviewer.models

import android.graphics.Bitmap

data class AudioMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val year: String? = null,
    val albumArt: Bitmap? = null
)