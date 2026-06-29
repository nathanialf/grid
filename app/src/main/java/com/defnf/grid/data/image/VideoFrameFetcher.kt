package com.defnf.grid.data.image

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
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
 * Coil model wrapping a video file whose first frame should be rendered as a thumbnail.
 * Using a dedicated type (rather than a bare [File]) lets Coil route video files to
 * [VideoFrameFetcher] while ordinary image files keep using Coil's built-in file path.
 */
data class VideoFrameRequest(val file: File)

/**
 * Decodes the first frame of a video file into a drawable for Coil. Throws when no frame
 * can be extracted so the caller's `error` slot (the video icon) is shown — keeps the UI
 * backwards compatible with files that have no decodable frame. Runs on Coil's IO
 * dispatcher, never the main thread.
 */
class VideoFrameFetcher(
    private val request: VideoFrameRequest,
    private val context: Context,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(request.file.absolutePath)
            // Grab the first frame (t=0, nearest sync frame). Scale during decode on API 27+
            // to avoid loading a full-resolution bitmap just for a thumbnail.
            val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, MAX_PX, MAX_PX)
            } else {
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } ?: error("No decodable frame in ${request.file.name}")
            return DrawableResult(
                drawable = bitmap.toDrawable(context.resources),
                isSampled = true,
                dataSource = DataSource.DISK,
            )
        } finally {
            retriever.release()
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<VideoFrameRequest> {
        override fun create(data: VideoFrameRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            VideoFrameFetcher(data, context.applicationContext)
    }

    private companion object {
        /** Cap on the decoded frame's longest edge; Coil downsamples further to the view size. */
        const val MAX_PX = 512
    }
}

/** Stable cache key for video frames: path + last-modified so re-cached files refresh. */
class VideoFrameKeyer : Keyer<VideoFrameRequest> {
    override fun key(data: VideoFrameRequest, options: Options): String =
        "videoframe:${data.file.absolutePath}:${data.file.lastModified()}"
}
