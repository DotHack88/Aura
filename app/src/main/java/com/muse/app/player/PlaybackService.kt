package com.muse.app.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.core.app.NotificationCompat
import com.muse.app.MainActivity
import com.muse.app.MuseApplication
import com.muse.app.R

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // Crea il canale notifiche (obbligatorio su Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Riproduzione Musicale",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifica per il controllo della riproduzione musicale in Aura"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        // Recupera l'istanza globale di PlayerManager
        val playerManager = (application as MuseApplication).playerManager
        val player = playerManager.exoPlayer

        // Crea la MediaSession collegata al Player esistente
        // setSessionActivity: quando si clicca sul badge/notifica apre l'app sul player
        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setId("AuraMediaSession")
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        // Configura il provider di notifiche Media3 con icona personalizzata
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setChannelName(R.string.app_name)
                .setNotificationId(NOTIFICATION_ID)
                .build()
                .apply {
                    setSmallIcon(R.drawable.ic_notification)
                }
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null) {
            player.pause()
        }
        stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "aura_playback_channel"
        const val NOTIFICATION_ID = 1001
    }
}

