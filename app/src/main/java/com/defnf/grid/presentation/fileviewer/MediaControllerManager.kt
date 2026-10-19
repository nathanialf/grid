package com.defnf.grid.presentation.fileviewer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Singleton manager for MediaController instances to prevent playback restart
 * when navigating back from notifications or other activities.
 *
 * One controller is kept per playback service and reused across files: switching tracks only
 * swaps the media item instead of tearing down and reconnecting to the session.
 */
object MediaControllerManager {

    private const val TAG = "MediaControllerManager"

    private val audio = ControllerSlot(AudioPlayerService::class.java)
    private val video = ControllerSlot(VideoPlayerService::class.java)

    suspend fun getAudioController(
        context: Context,
        file: File,
        fileName: String?,
        connectionId: String?,
        remotePath: String?,
        streamUri: String? = null
    ): MediaController? = audio.get(context, file, fileName, connectionId, remotePath, streamUri)

    suspend fun getVideoController(
        context: Context,
        file: File,
        fileName: String?,
        connectionId: String?,
        remotePath: String?,
        streamUri: String? = null
    ): MediaController? = video.get(context, file, fileName, connectionId, remotePath, streamUri)

    fun getCurrentAudioFile(): String? = audio.currentFile
    fun getCurrentVideoFile(): String? = video.currentFile

    fun releaseAudioController() = audio.release()

    fun releaseVideoController() = video.release()

    fun releaseAll() {
        releaseAudioController()
        releaseVideoController()
    }

    private class ControllerSlot(private val service: Class<out MediaSessionService>) {
        private var controller: MediaController? = null
        private var pending: ListenableFuture<MediaController>? = null
        var currentFile: String? = null
            private set

        suspend fun get(
            context: Context,
            file: File,
            fileName: String?,
            connectionId: String?,
            remotePath: String?,
            streamUri: String?
        ): MediaController? {
            // What is playing: the grid:// stream when streaming, otherwise the local file
            val source = streamUri ?: file.absolutePath
            // Controllers outlive the activity that asked for them, so never hold an Activity.
            val appContext = context.applicationContext

            // (Re)start the service with the file info so the notification opens this file.
            try {
                appContext.startService(
                    Intent(appContext, service).apply {
                        if (streamUri == null) putExtra("file_path", file.absolutePath)
                        putExtra("stream_uri", streamUri)
                        putExtra("file_name", fileName)
                        putExtra("connection_id", connectionId)
                        putExtra("remote_path", remotePath)
                    }
                )
            } catch (e: IllegalStateException) {
                // Background start restrictions; the session can still be bound below.
                Log.w(TAG, "Could not start ${service.simpleName}", e)
            }

            controller?.let { existing ->
                if (existing.isConnected) {
                    currentFile = source
                    return existing
                }
                release()
            }

            val future = MediaController.Builder(
                appContext,
                SessionToken(appContext, ComponentName(appContext, service))
            ).buildAsync()
            pending = future

            val built = suspendCancellableCoroutine { continuation ->
                future.addListener({
                    val result = try {
                        future.get()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to connect to ${service.simpleName}", e)
                        null
                    }
                    continuation.resume(result)
                }, ContextCompat.getMainExecutor(appContext))
                continuation.invokeOnCancellation { future.cancel(true) }
            }
            if (pending === future) pending = null
            if (built != null) {
                controller = built
                currentFile = source
            }
            return built
        }

        fun release() {
            pending?.cancel(true)
            pending = null
            controller?.release()
            controller = null
            currentFile = null
        }
    }
}
