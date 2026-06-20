package com.defnf.grid.data.image

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import java.io.File

/**
 * Coil model wrapping an audio file whose embedded album art should be rendered.
 * Using a dedicated type (rather than a bare [File]) lets Coil route audio files to
 * [AudioArtFetcher] while ordinary image files keep using Coil's built-in file path.
 */
data class AudioArtRequest(val file: File)

/**
 * Decodes the embedded album art (cover) of an audio file into a drawable for Coil.
 * Throws when there is no embedded art so the caller's `error` slot (the audio icon)
 * is shown. Runs on Coil's IO dispatcher — never the main thread.
 */
class AudioArtFetcher(
    private val request: AudioArtRequest,
    private val context: Context,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(request.file.absolutePath)
            val bytes = retriever.embeddedPicture
                ?: error("No embedded album art in ${request.file.name}")
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: error("Failed to decode album art in ${request.file.name}")
            return DrawableResult(
                drawable = bitmap.toDrawable(context.resources),
                isSampled = false,
                dataSource = DataSource.DISK,
            )
        } finally {
            retriever.release()
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<AudioArtRequest> {
        override fun create(data: AudioArtRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            AudioArtFetcher(data, context.applicationContext)
    }
}

/** Stable cache key for album art: path + last-modified so re-cached files refresh. */
class AudioArtKeyer : Keyer<AudioArtRequest> {
    override fun key(data: AudioArtRequest, options: Options): String =
        "albumart:${data.file.absolutePath}:${data.file.lastModified()}"
}
