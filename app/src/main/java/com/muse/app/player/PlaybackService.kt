package com.muse.app.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.core.app.NotificationCompat
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.muse.app.MainActivity
import com.muse.app.MuseApplication
import com.muse.app.R

@UnstableApi
class PlaybackService : MediaLibraryService() {

    private var mediaLibrarySession: MediaLibrarySession? = null

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

        // Wrapper per intercettare i comandi al volante (bluetooth / notifica)
        val forwardingPlayer = object : androidx.media3.common.ForwardingPlayer(player) {
            override fun getAvailableCommands(): androidx.media3.common.Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT)
                    .add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun seekToNext() {
                playerManager.next()
            }

            override fun seekToPrevious() {
                playerManager.previous()
            }

            override fun seekToNextMediaItem() {
                playerManager.next()
            }

            override fun seekToPreviousMediaItem() {
                playerManager.previous()
            }
        }

        // Crea la MediaSession collegata al ForwardingPlayer esistente
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

        val librarySessionCallback = object : MediaLibrarySession.Callback {
            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {
                val rootItem = MediaItem.Builder()
                    .setMediaId("root")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setIsPlayable(false)
                            .setIsBrowsable(true)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                            .build()
                    )
                    .build()
                return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                // Here you would return the actual playlist or library items
                // For now, we return an empty list or the current track as a basic integration
                val state = playerManager.playerState.value
                val items = state.playlist.map { track ->
                    MediaItem.Builder()
                        .setMediaId(track.id)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(track.artist)
                                .setIsPlayable(true)
                                .setIsBrowsable(false)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                .build()
                        )
                        .build()
                }
                return Futures.immediateFuture(LibraryResult.ofItemList(items, params))
            }
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, forwardingPlayer, librarySessionCallback)
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession?.player
        if (player != null) {
            player.pause()
        }
        stopSelf()
    }

    override fun onDestroy() {
        mediaLibrarySession?.run {
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "aura_playback_channel"
        const val NOTIFICATION_ID = 1001
    }
}

