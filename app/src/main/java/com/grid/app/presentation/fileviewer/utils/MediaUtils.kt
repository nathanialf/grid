package com.grid.app.presentation.fileviewer.utils

import com.grid.app.presentation.fileviewer.models.AudioMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Helper function to format time in mm:ss format
fun formatTime(milliseconds: Int): String {
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / (1000 * 60)) % 60
    return "%02d:%02d".format(minutes, seconds)
}

// Extract audio metadata from file
suspend fun extractAudioMetadata(file: File): AudioMetadata {
    return withContext(Dispatchers.IO) {
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            
            val title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val year = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_YEAR)
            
            // Extract album art with better format support
            val albumArt = extractAlbumArt(retriever, file)
            
            retriever.release()
            
            AudioMetadata(
                title = title?.takeIf { it.isNotBlank() },
                artist = artist?.takeIf { it.isNotBlank() },
                album = album?.takeIf { it.isNotBlank() },
                year = year?.takeIf { it.isNotBlank() },
                albumArt = albumArt
            )
        } catch (e: Exception) {
            AudioMetadata() // Return empty metadata if extraction fails
        }
    }
}

// Enhanced album art extraction with format-specific handling
private fun extractAlbumArt(retriever: android.media.MediaMetadataRetriever, file: File): android.graphics.Bitmap? {
    android.util.Log.d("AlbumArt", "Starting album art extraction for: ${file.absolutePath}")
    
    try {
        // Primary method - works best for MP3
        val artBytes = retriever.embeddedPicture
        if (artBytes != null && artBytes.isNotEmpty()) {
            android.util.Log.d("AlbumArt", "Found embedded picture data (${artBytes.size} bytes)")
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
            if (bitmap != null) {
                android.util.Log.d("AlbumArt", "Successfully decoded embedded album art")
                return bitmap
            } else {
                android.util.Log.w("AlbumArt", "Failed to decode embedded picture data")
            }
        } else {
            android.util.Log.d("AlbumArt", "No embedded picture data found")
        }
        
        // For M4A/MP4 files, try alternative extraction methods
        val extension = file.extension.lowercase()
        if (extension in setOf("m4a", "mp4", "aac")) {
            android.util.Log.d("AlbumArt", "Trying M4A-specific extraction")
            val embeddedArt = extractM4AAlbumArt(retriever, file)
            if (embeddedArt != null) {
                android.util.Log.d("AlbumArt", "M4A extraction successful")
                return embeddedArt
            } else {
                android.util.Log.d("AlbumArt", "M4A extraction failed")
            }
        }
        
        // For FLAC files, the standard method usually works but with some quirks
        if (extension == "flac") {
            android.util.Log.d("AlbumArt", "Trying FLAC-specific extraction")
            val embeddedArt = extractFLACAlbumArt(retriever, file)
            if (embeddedArt != null) {
                android.util.Log.d("AlbumArt", "FLAC extraction successful")
                return embeddedArt
            } else {
                android.util.Log.d("AlbumArt", "FLAC extraction failed")
            }
        }
        
        // No embedded album art found
        android.util.Log.d("AlbumArt", "No embedded album art found")
        return null
        
    } catch (e: Exception) {
        android.util.Log.e("AlbumArt", "Exception during embedded extraction: ${e.message}", e)
        return null
    }
}

// M4A-specific album art extraction
private fun extractM4AAlbumArt(retriever: android.media.MediaMetadataRetriever, file: File): android.graphics.Bitmap? {
    return try {
        android.util.Log.d("AlbumArt", "M4A extraction: Trying standard embedded picture")
        
        // Method 1: Standard embedded picture approach
        val artBytes = retriever.embeddedPicture
        if (artBytes != null && artBytes.isNotEmpty()) {
            android.util.Log.d("AlbumArt", "M4A: Found ${artBytes.size} bytes of embedded data")
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
            if (bitmap != null) {
                android.util.Log.d("AlbumArt", "M4A: Successfully decoded embedded picture")
                return bitmap
            } else {
                android.util.Log.w("AlbumArt", "M4A: Failed to decode embedded picture data")
            }
        } else {
            android.util.Log.d("AlbumArt", "M4A: No embedded picture data found")
        }
        
        // Method 2: Try with a fresh retriever instance
        android.util.Log.d("AlbumArt", "M4A: Trying with fresh retriever instance")
        try {
            val freshRetriever = android.media.MediaMetadataRetriever()
            freshRetriever.setDataSource(file.absolutePath)
            
            // Try to get embedded picture again with fresh instance
            val freshArtBytes = freshRetriever.embeddedPicture
            if (freshArtBytes != null && freshArtBytes.isNotEmpty()) {
                android.util.Log.d("AlbumArt", "M4A: Fresh retriever found ${freshArtBytes.size} bytes")
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(freshArtBytes, 0, freshArtBytes.size)
                if (bitmap != null) {
                    android.util.Log.d("AlbumArt", "M4A: Fresh retriever extraction successful")
                    freshRetriever.release()
                    return bitmap
                }
            }
            freshRetriever.release()
        } catch (e: Exception) {
            android.util.Log.d("AlbumArt", "M4A: Fresh retriever failed: ${e.message}")
        }
        
        android.util.Log.d("AlbumArt", "M4A: No album art found")
        null
    } catch (e: Exception) {
        android.util.Log.e("AlbumArt", "M4A extraction error: ${e.message}")
        null
    }
}

// FLAC-specific album art extraction
private fun extractFLACAlbumArt(retriever: android.media.MediaMetadataRetriever, file: File): android.graphics.Bitmap? {
    return try {
        android.util.Log.d("AlbumArt", "FLAC extraction: Trying standard embedded picture")
        
        // Method 1: Standard embedded picture
        val artBytes = retriever.embeddedPicture
        if (artBytes != null && artBytes.isNotEmpty()) {
            android.util.Log.d("AlbumArt", "FLAC: Found ${artBytes.size} bytes of embedded data")
            // FLAC sometimes returns invalid data, so validate the bitmap
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
            if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                android.util.Log.d("AlbumArt", "FLAC: Successfully decoded embedded picture")
                return bitmap
            } else {
                android.util.Log.w("AlbumArt", "FLAC: Invalid bitmap data or failed to decode")
            }
        } else {
            android.util.Log.d("AlbumArt", "FLAC: No embedded picture data found")
        }
        
        // Method 2: Try with fresh retriever for FLAC
        android.util.Log.d("AlbumArt", "FLAC: Trying with fresh retriever instance")
        try {
            val freshRetriever = android.media.MediaMetadataRetriever()
            freshRetriever.setDataSource(file.absolutePath)
            
            val freshArtBytes = freshRetriever.embeddedPicture
            if (freshArtBytes != null && freshArtBytes.isNotEmpty()) {
                android.util.Log.d("AlbumArt", "FLAC: Fresh retriever found ${freshArtBytes.size} bytes")
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(freshArtBytes, 0, freshArtBytes.size)
                if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                    android.util.Log.d("AlbumArt", "FLAC: Fresh retriever extraction successful")
                    freshRetriever.release()
                    return bitmap
                }
            }
            freshRetriever.release()
        } catch (e: Exception) {
            android.util.Log.d("AlbumArt", "FLAC: Fresh retriever failed: ${e.message}")
        }
        
        android.util.Log.d("AlbumArt", "FLAC: No album art found")
        null
    } catch (e: Exception) {
        android.util.Log.e("AlbumArt", "FLAC extraction error: ${e.message}")
        null
    }
}

