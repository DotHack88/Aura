package com.muse.app.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.muse.app.MainActivity
import com.muse.app.MuseApplication
import com.muse.app.R
import com.muse.app.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class PlaybackService : MediaLibraryService() {

    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "aura_playback_channel"
        const val NOTIFICATION_ID = 1001

        // Root e categorie di navigazione per Android Auto
        const val ROOT_ID = "aura_auto_root"
        const val ID_FAVORITES = "folder_favorites"
        const val ID_HISTORY = "folder_history"
        const val ID_TOP_TRACKS = "folder_top"
        const val ID_PLAYLISTS = "folder_playlists"
        const val ID_DOWNLOADED = "folder_downloaded"
        const val ID_QUEUE = "folder_queue"
    }

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

        // Recupera le istanze globali dell'applicazione
        val museApp = application as MuseApplication
        val playerManager = museApp.playerManager
        val musicRepository = museApp.musicRepository
        val player = playerManager.exoPlayer

        // Wrapper per comandi al volante, cuffie Bluetooth e car controls
        val forwardingPlayer = object : androidx.media3.common.ForwardingPlayer(player) {
            override fun getAvailableCommands(): androidx.media3.common.Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(androidx.media3.common.Player.COMMAND_PLAY_PAUSE)
                    .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT)
                    .add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
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

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Callback per l'integrazione completa con Android Auto (Navigazione, Categorie, Playback, Voice Search)
        val librarySessionCallback = object : MediaLibrarySession.Callback {

            // 1. Root di navigazione per Android Auto
            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {
                Log.d("PlaybackService", "Android Auto connected: ${browser.packageName}")
                val rootItem = MediaItem.Builder()
                    .setMediaId(ROOT_ID)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("Aura")
                            .setIsPlayable(false)
                            .setIsBrowsable(true)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                            .build()
                    )
                    .build()
                return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
            }

            // 2. Esplorazione cartelle su Android Auto (Preferiti, Recenti, Top, Playlist, Coda)
            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                serviceScope.launch {
                    try {
                        when (parentId) {
                            ROOT_ID -> {
                                val rootTabs = buildRootTabs()
                                future.set(LibraryResult.ofItemList(rootTabs, params))
                            }
                            ID_FAVORITES -> {
                                val favs = musicRepository.getFavoriteTracks().first()
                                val items = favs.map { it.toMediaItem() }
                                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                            }
                            ID_HISTORY -> {
                                val recents = musicRepository.getRecentTracks().first()
                                val items = recents.map { it.toMediaItem() }
                                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                            }
                            ID_TOP_TRACKS -> {
                                val top = musicRepository.getTopPlayedTracks(30).first()
                                val items = top.map { it.toMediaItem() }
                                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                            }
                            ID_DOWNLOADED -> {
                                val downloads = musicRepository.getDownloadedTracks().first()
                                val items = downloads.map { it.toMediaItem() }
                                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                            }
                            ID_PLAYLISTS -> {
                                val playlists = musicRepository.getPlaylists().first()
                                val items = playlists.map { p ->
                                    MediaItem.Builder()
                                        .setMediaId("playlist_${p.id}")
                                        .setMediaMetadata(
                                            MediaMetadata.Builder()
                                                .setTitle(p.name)
                                                .setIsPlayable(true)
                                                .setIsBrowsable(true)
                                                .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                                .setArtworkUri(p.coverUrl?.let { Uri.parse(it) })
                                                .build()
                                        )
                                        .build()
                                }
                                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                            }
                            ID_QUEUE -> {
                                val queue = playerManager.playerState.value.playlist
                                val items = queue.map { it.toMediaItem() }
                                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                            }
                            else -> {
                                if (parentId.startsWith("playlist_")) {
                                    val playlistId = parentId.removePrefix("playlist_")
                                    val tracks = musicRepository.getPlaylistTracks(playlistId).first()
                                    val items = tracks.map { it.toMediaItem() }
                                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                                } else {
                                    future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PlaybackService", "Errore onGetChildren per $parentId", e)
                        future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                    }
                }
                return future
            }

            // 3. Singolo elemento
            override fun onGetItem(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                mediaId: String
            ): ListenableFuture<LibraryResult<MediaItem>> {
                val future = SettableFuture.create<LibraryResult<MediaItem>>()
                serviceScope.launch {
                    try {
                        val track = musicRepository.getTrackByVideoId(mediaId)
                        if (track != null) {
                            future.set(LibraryResult.ofItem(track.toMediaItem(), null))
                        } else {
                            future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
                        }
                    } catch (e: Exception) {
                        future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
                    }
                }
                return future
            }

            // 4. Avvio riproduzione al tocco di un brano o cartella sullo schermo dell'auto
            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>
            ): ListenableFuture<MutableList<MediaItem>> {
                val future = SettableFuture.create<MutableList<MediaItem>>()
                serviceScope.launch {
                    try {
                        val firstItem = mediaItems.firstOrNull()
                        if (firstItem != null) {
                            val mediaId = firstItem.mediaId

                            val tracksToPlay: List<Track> = when (mediaId) {
                                ID_FAVORITES -> musicRepository.getFavoriteTracks().first()
                                ID_HISTORY -> musicRepository.getRecentTracks().first()
                                ID_TOP_TRACKS -> musicRepository.getTopPlayedTracks(30).first()
                                ID_DOWNLOADED -> musicRepository.getDownloadedTracks().first()
                                ID_QUEUE -> playerManager.playerState.value.playlist
                                else -> {
                                    if (mediaId.startsWith("playlist_")) {
                                        val playlistId = mediaId.removePrefix("playlist_")
                                        musicRepository.getPlaylistTracks(playlistId).first()
                                    } else {
                                        val resolvedList = mutableListOf<Track>()
                                        for (item in mediaItems) {
                                            val t = musicRepository.getTrackByVideoId(item.mediaId) ?: Track(
                                                id = item.mediaId,
                                                title = item.mediaMetadata.title?.toString() ?: "Brano",
                                                artist = item.mediaMetadata.artist?.toString() ?: "Artista",
                                                thumbnailUrl = item.mediaMetadata.artworkUri?.toString() ?: "",
                                                durationMs = 0L
                                            )
                                            resolvedList.add(t)
                                        }
                                        resolvedList
                                    }
                                }
                            }

                            if (tracksToPlay.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    playerManager.playTrack(tracksToPlay.first(), tracksToPlay)
                                }
                            }
                        }
                        future.set(mediaItems)
                    } catch (e: Exception) {
                        Log.e("PlaybackService", "Errore in onAddMediaItems", e)
                        future.set(mediaItems)
                    }
                }
                return future
            }

            // 5. Ripresa della riproduzione automatica all'avvio dell'auto
            override fun onPlaybackResumption(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                serviceScope.launch {
                    try {
                        val state = playerManager.playerState.value
                        val curTrack = state.currentTrack
                        if (curTrack != null && state.playlist.isNotEmpty()) {
                            val mediaItems = state.playlist.map { it.toMediaItem() }
                            future.set(
                                MediaSession.MediaItemsWithStartPosition(
                                    mediaItems,
                                    state.currentIndex,
                                    state.positionMs
                                )
                            )
                        } else {
                            val recents = musicRepository.getRecentTracks().first()
                            if (recents.isNotEmpty()) {
                                val mediaItems = recents.map { it.toMediaItem() }
                                future.set(
                                    MediaSession.MediaItemsWithStartPosition(
                                        mediaItems,
                                        0,
                                        0L
                                    )
                                )
                            } else {
                                val favs = musicRepository.getFavoriteTracks().first()
                                val mediaItems = favs.map { it.toMediaItem() }
                                future.set(
                                    MediaSession.MediaItemsWithStartPosition(
                                        mediaItems,
                                        0,
                                        0L
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PlaybackService", "Errore in onPlaybackResumption", e)
                        future.set(
                            MediaSession.MediaItemsWithStartPosition(
                                emptyList(),
                                0,
                                0L
                            )
                        )
                    }
                }
                return future
            }

            // 6. Ricerca Vocale su Android Auto ("Ok Google, metti ... su Aura")
            override fun onSearch(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<Void>> {
                session.notifySearchResultChanged(browser, query, 20, params)
                return Futures.immediateFuture(LibraryResult.ofVoid(params))
            }

            override fun onGetSearchResult(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                serviceScope.launch {
                    try {
                        val searchResults = musicRepository.searchTracks(query)
                        val items = searchResults.map { it.toMediaItem() }
                        future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                    } catch (e: Exception) {
                        Log.e("PlaybackService", "Errore ricerca Android Auto per $query", e)
                        future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                    }
                }
                return future
            }
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, forwardingPlayer, librarySessionCallback)
            .setId("AuraMediaSession")
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

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

    private fun buildRootTabs(): ImmutableList<MediaItem> {
        val tabs = ImmutableList.builder<MediaItem>()

        // 1. Preferiti
        tabs.add(
            MediaItem.Builder()
                .setMediaId(ID_FAVORITES)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Brani Preferiti")
                        .setIsPlayable(true)
                        .setIsBrowsable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                        .build()
                )
                .build()
        )

        // 2. Recenti
        tabs.add(
            MediaItem.Builder()
                .setMediaId(ID_HISTORY)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Ascoltati di recente")
                        .setIsPlayable(true)
                        .setIsBrowsable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                        .build()
                )
                .build()
        )

        // 3. I più ascoltati
        tabs.add(
            MediaItem.Builder()
                .setMediaId(ID_TOP_TRACKS)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("I più ascoltati")
                        .setIsPlayable(true)
                        .setIsBrowsable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                        .build()
                )
                .build()
        )

        // 4. Playlist
        tabs.add(
            MediaItem.Builder()
                .setMediaId(ID_PLAYLISTS)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Le tue Playlist")
                        .setIsPlayable(false)
                        .setIsBrowsable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                        .build()
                )
                .build()
        )

        // 5. Scaricati offline
        tabs.add(
            MediaItem.Builder()
                .setMediaId(ID_DOWNLOADED)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Scaricati offline")
                        .setIsPlayable(true)
                        .setIsBrowsable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                        .build()
                )
                .build()
        )

        // 6. Coda attuale
        tabs.add(
            MediaItem.Builder()
                .setMediaId(ID_QUEUE)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("In riproduzione")
                        .setIsPlayable(true)
                        .setIsBrowsable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                        .build()
                )
                .build()
        )

        return tabs.build()
    }

    private fun Track.toMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(this.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(this.title)
                    .setArtist(this.artist)
                    .setAlbumTitle(this.album ?: "")
                    .setArtworkUri(if (this.thumbnailUrl.isNotEmpty()) Uri.parse(this.thumbnailUrl) else null)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, true)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val playerManager = (application as? MuseApplication)?.playerManager
        playerManager?.exoPlayer?.stop()
        mediaLibrarySession?.player?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.cancel(NOTIFICATION_ID)
        mediaLibrarySession?.run {
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }
}
