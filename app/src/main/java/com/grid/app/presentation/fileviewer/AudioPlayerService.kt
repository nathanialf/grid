package com.grid.app.presentation.fileviewer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.ui.PlayerNotificationManager
import com.grid.app.R
import com.grid.app.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AudioPlayerService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var currentFilePath: String? = null
    private var currentFileName: String? = null
    private var currentConnectionId: String? = null
    private var currentRemotePath: String? = null

    override fun onCreate() {
        super.onCreate()
        
        // Create notification channel for media playback
        createNotificationChannel()
        
        // Create ExoPlayer with audio attributes
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Create MediaSession with proper notification setup
        mediaSession = MediaSession.Builder(this, player)
            .setId("audio_session_${System.currentTimeMillis()}")
            .setSessionActivity(createSessionActivityPendingIntent())
            .build()
            
        // Set up custom notification provider with monochrome icon
        setupMediaNotificationProvider()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Store current file information for notification intents
        intent?.let {
            currentFilePath = it.getStringExtra("file_path")
            currentFileName = it.getStringExtra("file_name")
            currentConnectionId = it.getStringExtra("connection_id")
            currentRemotePath = it.getStringExtra("remote_path")
            
            // Update session activity with current file info
            if (currentFilePath != null) {
                mediaSession?.setSessionActivity(createFileSessionActivityPendingIntent())
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "media_playback_channel"
            val channelName = "Media Playback"
            val channelDescription = "Notifications for audio playback controls"
            val importance = NotificationManager.IMPORTANCE_LOW
            
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createSessionActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun createFileSessionActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, FileViewerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("file_path", currentFilePath)
            putExtra("file_name", currentFileName)
            putExtra("file_type", "AUDIO")
            putExtra("connection_id", currentConnectionId)
            putExtra("remote_path", currentRemotePath)
            putExtra("from_notification", true) // Flag to indicate this came from notification
        }
        return PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun setupMediaNotificationProvider() {
        // Create custom notification provider that sets monochrome icon
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId("media_playback_channel")
            .setNotificationId(1001)
            .build()
        
        // Set the small icon through the notification provider
        notificationProvider.setSmallIcon(R.drawable.ic_launcher_monochrome)
        setMediaNotificationProvider(notificationProvider)
    }
}