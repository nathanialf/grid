package com.defnf.grid.presentation.fileviewer.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.media3.common.MediaMetadata
import com.defnf.grid.presentation.fileviewer.models.AudioMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "MediaUtils"

// Album art is shown at most ~screen width; decoding a 3000px cover at full size costs ~36 MB.
private const val MAX_ALBUM_ART_PX = 1024

// Formats a playback position as m:ss, or h:mm:ss for long tracks (audiobooks, mixes).
fun formatTime(milliseconds: Int): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

// Extracts tags, a downsampled cover and its theme seed color in one pass over the file.
suspend fun extractAudioMetadata(file: File): AudioMetadata = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        fun tag(key: Int) = retriever.extractMetadata(key)?.takeIf { it.isNotBlank() }

        val albumArt = retriever.embeddedPicture?.let(::decodeAlbumArt)
        AudioMetadata(
            title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE),
            artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST),
            album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM),
            year = tag(MediaMetadataRetriever.METADATA_KEY_YEAR),
            albumArt = albumArt,
            seedColor = albumArt?.let(::extractSeedColor)
        )
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read audio metadata for ${file.name}", e)
        AudioMetadata()
    } finally {
        retriever.release()
    }
}

// Builds metadata from what the player parsed out of a streamed file (ID3/Vorbis/MP4 tags + cover).
suspend fun audioMetadataFrom(metadata: MediaMetadata): AudioMetadata = withContext(Dispatchers.Default) {
    fun text(value: CharSequence?) = value?.toString()?.takeIf { it.isNotBlank() }
    val albumArt = metadata.artworkData?.let(::decodeAlbumArt)
    AudioMetadata(
        title = text(metadata.title),
        artist = text(metadata.artist) ?: text(metadata.albumArtist),
        album = text(metadata.albumTitle),
        year = (metadata.recordingYear ?: metadata.releaseYear)?.toString(),
        albumArt = albumArt,
        seedColor = albumArt?.let(::extractSeedColor)
    )
}

private fun decodeAlbumArt(bytes: ByteArray): Bitmap? {
    if (bytes.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_ALBUM_ART_PX) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}
