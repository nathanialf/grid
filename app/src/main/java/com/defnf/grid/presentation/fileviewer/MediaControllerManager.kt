package com.defnf.grid.presentation.fileviewer

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CompletableDeferred
import java.io.File

/**
 * Singleton manager for MediaController instances to prevent playback restart
 * when navigating back from notifications or other activities.
 */
object MediaControllerManager {
    
    private var audioController: MediaController? = null
    private var videoController: MediaController? = null
    private var currentAudioFile: String? = null
    private var currentVideoFile: String? = null
    private var audioControllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var videoControllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    
    suspend fun getAudioController(
        context: Context,
        file: File,
        fileName: String?,
        connectionId: String?,
        remotePath: String?
    ): MediaController? {
        // Return existing controller if playing the same file
        if (audioController != null && currentAudioFile == file.absolutePath) {
            return audioController
        }
        
        // Release old controller properly if switching files
        if (audioController != null) {
            try {
                // Stop the player before releasing to prevent leaks
                audioController?.stop()
                audioController?.release()
            } catch (e: Exception) {
                // Ignore errors during cleanup
            }
            audioController = null
            currentAudioFile = null
            audioControllerFuture?.cancel(true)
            audioControllerFuture = null
            
            // Give the system a moment to clean up
            kotlinx.coroutines.delay(100)
        }
        
        return try {
            // Start service with file information
            val serviceIntent = android.content.Intent(context, AudioPlayerService::class.java).apply {
                putExtra("file_path", file.absolutePath)
                putExtra("file_name", fileName)
                putExtra("connection_id", connectionId)
                putExtra("remote_path", remotePath)
            }
            context.startService(serviceIntent)
            
            // Create new controller
            val sessionToken = SessionToken(context, ComponentName(context, AudioPlayerService::class.java))
            val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            audioControllerFuture = controllerFuture
            val deferred = CompletableDeferred<MediaController?>()
            
            controllerFuture.addListener({
                try {
                    val controller = controllerFuture.get()
                    audioController = controller
                    currentAudioFile = file.absolutePath
                    deferred.complete(controller)
                } catch (e: Exception) {
                    deferred.complete(null)
                }
            }, ContextCompat.getMainExecutor(context))
            
            deferred.await()
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun getVideoController(
        context: Context,
        file: File,
        fileName: String?,
        connectionId: String?,
        remotePath: String?
    ): MediaController? {
        // Return existing controller if playing the same file
        if (videoController != null && currentVideoFile == file.absolutePath) {
            return videoController
        }
        
        // Release old controller properly if switching files
        if (videoController != null) {
            try {
                // Stop the player before releasing to prevent leaks
                videoController?.stop()
                videoController?.release()
            } catch (e: Exception) {
                // Ignore errors during cleanup
            }
            videoController = null
            currentVideoFile = null
            videoControllerFuture?.cancel(true)
            videoControllerFuture = null
            
            // Give the system a moment to clean up
            kotlinx.coroutines.delay(100)
        }
        
        return try {
            // Start service with file information
            val serviceIntent = android.content.Intent(context, VideoPlayerService::class.java).apply {
                putExtra("file_path", file.absolutePath)
                putExtra("file_name", fileName)
                putExtra("connection_id", connectionId)
                putExtra("remote_path", remotePath)
            }
            context.startService(serviceIntent)
            
            // Create new controller
            val sessionToken = SessionToken(context, ComponentName(context, VideoPlayerService::class.java))
            val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            videoControllerFuture = controllerFuture
            val deferred = CompletableDeferred<MediaController?>()
            
            controllerFuture.addListener({
                try {
                    val controller = controllerFuture.get()
                    videoController = controller
                    currentVideoFile = file.absolutePath
                    deferred.complete(controller)
                } catch (e: Exception) {
                    deferred.complete(null)
                }
            }, ContextCompat.getMainExecutor(context))
            
            deferred.await()
        } catch (e: Exception) {
            null
        }
    }
    
    fun getCurrentAudioFile(): String? = currentAudioFile
    fun getCurrentVideoFile(): String? = currentVideoFile
    
    fun releaseAudioController() {
        audioController?.release()
        audioController = null
        currentAudioFile = null
        audioControllerFuture?.cancel(true)
        audioControllerFuture = null
    }
    
    fun releaseVideoController() {
        videoController?.release()
        videoController = null
        currentVideoFile = null
        videoControllerFuture?.cancel(true)
        videoControllerFuture = null
    }
    
    fun releaseAll() {
        releaseAudioController()
        releaseVideoController()
    }
}