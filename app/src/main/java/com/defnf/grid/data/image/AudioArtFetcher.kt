package com.defnf.grid.data.image

import android.content.Context
import android.graphics.Bitmap
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
import coil.size.pxOrElse
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
    private val options: Options,
    private val context: Context,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(request.file.absolutePath)
            val bytes = retriever.embeddedPicture
                ?: error("No embedded album art in ${request.file.name}")
            // Covers are often 1500-3000px; decode only as large as the requested thumbnail so a
            // 40dp row doesn't hold (and memory-cache) a ~36 MB bitmap.
            val targetPx = maxOf(
                options.size.width.pxOrElse { 0 },
                options.size.height.pxOrElse { 0 }
            ).takeIf { it > 0 } ?: DEFAULT_TARGET_PX
            val (bitmap, sampleSize) = decodeSampledBitmap(bytes, targetPx)
                ?: error("Failed to decode album art in ${request.file.name}")
            return DrawableResult(
                drawable = bitmap.toDrawable(context.resources),
                isSampled = sampleSize > 1,
                dataSource = DataSource.DISK,
            )
        } finally {
            retriever.release()
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<AudioArtRequest> {
        override fun create(data: AudioArtRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            AudioArtFetcher(data, options, context.applicationContext)
    }

    private companion object {
        const val DEFAULT_TARGET_PX = 512
    }
}

/**
 * Decodes [bytes] downsampled by a power of two, keeping the longer side at least [targetPx], and
 * returns the bitmap with the sample size used, or null if the bytes aren't a decodable image.
 */
fun decodeSampledBitmap(bytes: ByteArray, targetPx: Int): Pair<Bitmap, Int>? {
    if (bytes.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= targetPx) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    return bitmap to sampleSize
}

/** Stable cache key for album art: path + last-modified so re-cached files refresh. */
class AudioArtKeyer : Keyer<AudioArtRequest> {
    override fun key(data: AudioArtRequest, options: Options): String =
        "albumart:${data.file.absolutePath}:${data.file.lastModified()}"
}
