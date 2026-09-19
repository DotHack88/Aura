package com.muse.app.player

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.muse.app.data.repository.MusicRepository
import com.muse.app.domain.lyrics.DefaultLyricsProvider
import com.muse.app.domain.lyrics.LyricsProvider
import com.muse.app.domain.model.Lyrics
import com.muse.app.domain.model.PlayerMode
import com.muse.app.domain.model.PlayerState
import com.muse.app.domain.model.RepeatMode
import com.muse.app.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.muse.app.cast.CastManager
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult

class PlayerManager(
    private val context: Context,
    private val musicRepository: MusicRepository,
    private val lyricsProvider: LyricsProvider = DefaultLyricsProvider(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : Player.Listener {

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _currentLyrics = MutableStateFlow<Lyrics?>(null)
    val currentLyrics: StateFlow<Lyrics?> = _currentLyrics.asStateFlow()

    val equalizerManager = EqualizerManager(context)

    val exoPlayer: ExoPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                androidx.media3.datasource.DefaultDataSource.Factory(
                    context,
                    androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(
                        okhttp3.OkHttpClient.Builder()
                            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                            .followRedirects(true)
                            .followSslRedirects(true)
                            .build()
                    ).setUserAgent("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36")
                )
            )
        )
        .build().apply {
        addListener(this@PlayerManager)
    }

    private var progressTickerJob: Job? = null
    private var extractJob: Job? = null
    private var lyricsJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var currentVideoId: String? = null
    private var consecutiveErrors = 0
    /** True per i ~2s subito dopo STATE_ENDED, per ignorare errori ExoPlayer post-fine naturale */
    private var isNaturalEnd = false

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            { mediaController = controllerFuture?.get() },
            ContextCompat.getMainExecutor(context)
        )
        // Inizializza l'equalizzatore con l'audio session ID di ExoPlayer
        scope.launch(Dispatchers.Main) {
            equalizerManager.init(exoPlayer.audioSessionId)
        }
    }

    fun playTrack(track: Track, playlist: List<Track> = listOf(track)) {
        val index = playlist.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        // Ogni nuovo brano avviato esplicitamente resetta il contatore errori
        consecutiveErrors = 0
        isNaturalEnd = false

        // Salva lo stato video PRIMA dell'update per sapere se riattivarlo dopo
        val wasInVideoMode = _playerState.value.isVideoMode

        _playerState.update {
            it.copy(
                currentTrack = track,
                playlist = playlist,
                currentIndex = index,
                isPlaying = false,
                positionMs = 0L,
                durationMs = track.durationMs,
                playerMode = if (it.playerMode == PlayerMode.MINI) PlayerMode.FULL else it.playerMode,
                errorMessage = null,
                // Resetta temporaneamente la modalità video mentre carichiamo l'audio del nuovo brano
                isVideoMode = false,
                isLoading = true
            )
        }
        
        _currentLyrics.value = null
        loadLyricsForTrack(track)

        // === CAST: Se c'è una sessione Google Cast attiva, delega il playback a CastManager ===
        if (CastManager.isCasting.value) {
            Log.d("PlayerManager", "Sessione Cast attiva — invio brano a CastManager: ${track.title}")
            exoPlayer.pause()
            CastManager.castTrack(track)
            val nextIndex = if (playlist.size > 1) {
                if (_playerState.value.shuffle) kotlin.random.Random.nextInt(playlist.size) else (index + 1)
            } else -1
            if (nextIndex in playlist.indices && nextIndex != index) {
                CastManager.appendToQueue(playlist[nextIndex])
            }
            _playerState.update { it.copy(isPlaying = true) }
            startProgressTicker()
            return
        }

        extractJob?.cancel()
        extractJob = scope.launch {
            currentVideoId = track.id

            // 1. Controlla se esiste un file locale scaricato
            val localFile = musicRepository.offlineManager?.getLocalFile(track.id)
            if (localFile != null && localFile.exists()) {
                Log.d("PlayerManager", "Riproduzione da file locale: ${localFile.absolutePath}")
                val localUri = "file://${localFile.absolutePath}"
                val mediaItem = createMediaItem(localUri, track)
                withContext(Dispatchers.Main) {
                    exoPlayer.stop()
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
                return@launch
            }

            // 2. Nessun file locale → estrai da YouTube con retry
            var streamUrl: String? = null
            var attempts = 0
            val maxAttempts = 2

            while (attempts < maxAttempts && streamUrl == null) {
                attempts++
                try {
                    Log.d("PlayerManager", "Tentativo $attempts/$maxAttempts per ${track.id}")
                    streamUrl = NewPipeStreamExtractor.getAudioStreamUrl(track.id)
                    if (streamUrl == null) {
                        Log.d("PlayerManager", "Fallback su stream video per ${track.id}")
                        streamUrl = NewPipeStreamExtractor.getVideoStreamUrl(track.id)
                    }
                } catch (e: Exception) {
                    Log.e("PlayerManager", "Eccezione al tentativo $attempts per ${track.id}", e)
                }
                if (streamUrl == null && attempts < maxAttempts) {
                    kotlinx.coroutines.delay(1500)
                }
            }

            if (streamUrl != null) {
                consecutiveErrors = 0
                val mediaItem = createMediaItem(streamUrl, track)
                withContext(Dispatchers.Main) {
                    exoPlayer.stop()
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
                // Se prima era in modalità video, rilancia automaticamente il video per il nuovo brano
                if (wasInVideoMode) {
                    Log.d("PlayerManager", "Era in video mode — auto-switch video per nuovo brano")
                    delay(600) // Attesa minima per permettere all'audio di avviarsi
                    switchToVideoMode()
                }
            } else {
                Log.e("PlayerManager", "Stream non ottenuto dopo $maxAttempts tentativi per ${track.id}. Skip.")
                withContext(Dispatchers.Main) { 
                    _playerState.update { it.copy(isLoading = false) }
                    handlePlaybackError("Impossibile ottenere lo stream audio per questo brano.")
                }
            }
        }
    }


    fun playShuffled(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val shuffledTracks = tracks.shuffled()
        _playerState.update { it.copy(shuffle = true) }
        playTrack(shuffledTracks.first(), shuffledTracks)
    }

    /** Passa a stream video HD cercando il video ufficiale su YouTube.
     *  Aggiunge un fade out/in del volume per evitare il "gracchio" al cambio stream. */
    fun switchToVideoMode() {
        val currentTrack = _playerState.value.currentTrack ?: return
        val savedPosition = exoPlayer.currentPosition
        extractJob?.cancel()
        extractJob = scope.launch {
            // 1. Cerca il video musicale su YouTube normale
            val query = "${currentTrack.artist} ${currentTrack.title} official video"
            val searchResults = NewPipeStreamExtractor.searchTracks(query, 1)
            
            val videoId = if (searchResults.isNotEmpty()) {
                val foundId = searchResults[0]["id"] as String
                Log.d("PlayerManager", "Trovato video ufficiale su YT: $foundId per la query '$query'")
                foundId
            } else {
                Log.w("PlayerManager", "Nessun video ufficiale trovato, uso ID originale: ${currentTrack.id}")
                currentTrack.id
            }

            // 2. Estrai l'URL dello stream per il video trovato
            val videoUrl = NewPipeStreamExtractor.getVideoStreamUrl(videoId)
            if (videoUrl != null) {
                // Fade out graduale per evitare il gracchio al cambio stream (~150ms)
                withContext(Dispatchers.Main) {
                    for (i in 10 downTo 0) {
                        exoPlayer.volume = i / 10f
                        delay(15)
                    }
                }
                val mediaItem = createMediaItem(videoUrl, currentTrack)
                withContext(Dispatchers.Main) {
                    try {
                        exoPlayer.stop()
                        exoPlayer.setMediaItem(mediaItem)
                        exoPlayer.prepare()
                        exoPlayer.seekTo(savedPosition)
                        exoPlayer.play()
                        _playerState.update { it.copy(isVideoMode = true) }
                        Log.d("PlayerManager", "Modalità VIDEO attivata per $videoId")
                    } catch (e: Exception) {
                        Log.e("PlayerManager", "Errore impostazione video: ${e.message}")
                        _playerState.update { it.copy(isVideoMode = false) }
                        exoPlayer.volume = 1f
                    }
                }
                // Fade in graduale dopo che il buffer è partito (~200ms attesa + 150ms fade)
                delay(200)
                withContext(Dispatchers.Main) {
                    for (i in 0..10) {
                        exoPlayer.volume = i / 10f
                        delay(15)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    _playerState.update { it.copy(isVideoMode = false) }
                    exoPlayer.volume = 1f
                }
                Log.w("PlayerManager", "Stream video non disponibile per $videoId")
            }
        }
    }

    /** Torna allo stream solo-audio mantenendo la posizione corrente.
     *  Aggiunge un fade out/in del volume per evitare il "gracchio" al cambio stream. */
    fun switchToAudioMode() {
        val videoId = currentVideoId ?: return
        val savedPosition = exoPlayer.currentPosition
        extractJob?.cancel()
        extractJob = scope.launch {
            val audioUrl = NewPipeStreamExtractor.getAudioStreamUrl(videoId)
            val track = _playerState.value.currentTrack
            if (audioUrl != null && track != null) {
                // Fade out graduale (~150ms)
                withContext(Dispatchers.Main) {
                    for (i in 10 downTo 0) {
                        exoPlayer.volume = i / 10f
                        delay(15)
                    }
                }
                val mediaItem = createMediaItem(audioUrl, track)
                withContext(Dispatchers.Main) {
                    exoPlayer.stop()
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.seekTo(savedPosition)
                    exoPlayer.play()
                    _playerState.update { it.copy(isVideoMode = false) }
                }
                Log.d("PlayerManager", "Modalità AUDIO ripristinata per $videoId")
                // Fade in graduale dopo avvio buffer
                delay(300)
                withContext(Dispatchers.Main) {
                    for (i in 0..10) {
                        exoPlayer.volume = i / 10f
                        delay(15)
                    }
                }
            } else {
                // Se non riesce a trovare l'audio URL, resetta comunque il volume
                withContext(Dispatchers.Main) { exoPlayer.volume = 1f }
            }
        }
    }

    private suspend fun createMediaItem(url: String, track: Track): MediaItem {
        var artworkData: ByteArray? = null
        try {
            val request = ImageRequest.Builder(context)
                .data(track.thumbnailUrl)
                .size(512, 512)
                .allowHardware(false)
                .build()
            val result = Coil.imageLoader(context).execute(request)
            if (result is SuccessResult) {
                val drawable = result.drawable
                val bitmap = if (drawable is android.graphics.drawable.BitmapDrawable) {
                    drawable.bitmap
                } else {
                    val b = android.graphics.Bitmap.createBitmap(
                        drawable.intrinsicWidth.coerceAtLeast(1),
                        drawable.intrinsicHeight.coerceAtLeast(1),
                        android.graphics.Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(b)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    b
                }
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
                artworkData = stream.toByteArray()
            }
        } catch (e: Exception) {
            Log.e("PlayerManager", "Errore download artwork per notifica", e)
        }

        val metadataBuilder = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setArtworkUri(android.net.Uri.parse(track.thumbnailUrl))
            
        if (artworkData != null) {
            metadataBuilder.setArtworkData(artworkData, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
            
        val builder = MediaItem.Builder()
            .setUri(url)
            .setMediaId(track.id)
            .setMediaMetadata(metadataBuilder.build())
            
        if (url.contains("manifest/dash") || url.contains(".mpd") || url.contains("videoplayback?id=")) {
            // Seleziona esplicitamente MPD per i manifest DASH di YouTube
            if (url.contains("manifest/dash") || url.contains(".mpd")) {
                builder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD)
            }
        }
        if (url.contains("manifest/hls") || url.contains(".m3u8")) {
            builder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
        }
        return builder.build()
    }

    fun togglePlayPause() {
        // Se stiamo trasmettendo su Cast, controlliamo il RemoteMediaClient
        val remoteClient = CastManager.castSession.value?.remoteMediaClient
        if (CastManager.isCasting.value && remoteClient != null) {
            if (remoteClient.isPlaying) {
                remoteClient.pause()
                _playerState.update { it.copy(isPlaying = false) }
            } else {
                remoteClient.play()
                _playerState.update { it.copy(isPlaying = true) }
            }
            return
        }
        // Riproduzione locale
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    /** Aggiunge un brano alla fine della coda (playlist corrente) senza interrompere la riproduzione. */
    fun addToQueue(track: Track) {
        val state = _playerState.value
        if (state.currentTrack == null) {
            // Nessuna traccia in riproduzione → avviamo direttamente
            playTrack(track)
            return
        }
        val newPlaylist = state.playlist.toMutableList()
        // Inserisce subito dopo la traccia corrente se non è già in lista
        if (newPlaylist.none { it.id == track.id }) {
            val insertIndex = (state.currentIndex + 1).coerceAtMost(newPlaylist.size)
            newPlaylist.add(insertIndex, track)
            _playerState.update { it.copy(playlist = newPlaylist) }
        }
    }

    fun moveTrack(fromIndex: Int, toIndex: Int) {
        val state = _playerState.value
        if (fromIndex !in state.playlist.indices || toIndex !in state.playlist.indices) return
        if (fromIndex == toIndex) return

        val newPlaylist = state.playlist.toMutableList()
        val track = newPlaylist.removeAt(fromIndex)
        newPlaylist.add(toIndex, track)

        var newCurrentIndex = state.currentIndex
        if (state.currentIndex == fromIndex) {
            newCurrentIndex = toIndex
        } else if (state.currentIndex in (fromIndex + 1)..toIndex) {
            newCurrentIndex--
        } else if (state.currentIndex in toIndex until fromIndex) {
            newCurrentIndex++
        }

        _playerState.update { 
            it.copy(
                playlist = newPlaylist,
                currentIndex = newCurrentIndex
            ) 
        }
    }

    fun removeTrack(index: Int) {
        val state = _playerState.value
        if (index !in state.playlist.indices) return
        // Non permettiamo di rimuovere la traccia correntemente in riproduzione in questo modo
        if (index == state.currentIndex) return 

        val newPlaylist = state.playlist.toMutableList()
        newPlaylist.removeAt(index)

        val newCurrentIndex = if (index < state.currentIndex) {
            state.currentIndex - 1
        } else {
            state.currentIndex
        }

        _playerState.update { 
            it.copy(
                playlist = newPlaylist,
                currentIndex = newCurrentIndex
            ) 
        }
    }
    fun seekTo(positionMs: Long) {
        val remoteClient = CastManager.castSession.value?.remoteMediaClient
        if (CastManager.isCasting.value && remoteClient != null) {
            remoteClient.seek(com.google.android.gms.cast.MediaSeekOptions.Builder().setPosition(positionMs).build())
        } else {
            exoPlayer.seekTo(positionMs)
        }
        _playerState.update { it.copy(positionMs = positionMs) }
    }

    fun next() {
        val state = _playerState.value
        if (state.playlist.isEmpty()) return

        val nextIndex = if (state.shuffle && state.playlist.size > 1) {
            var randomIndex: Int
            do {
                randomIndex = kotlin.random.Random.nextInt(state.playlist.size)
            } while (randomIndex == state.currentIndex)
            randomIndex
        } else {
            val candidate = state.currentIndex + 1
            if (candidate >= state.playlist.size) {
                if (state.repeat == RepeatMode.ALL) {
                    0
                } else {
                    return
                }
            } else {
                candidate
            }
        }
        val nextTrack = state.playlist[nextIndex]
        playTrack(nextTrack, state.playlist)
    }

    fun previous() {
        val state = _playerState.value
        if (state.playlist.isEmpty()) return

        if (state.positionMs > 3000L || exoPlayer.currentPosition > 3000L) {
            seekTo(0L)
            return
        }

        val prevIndex = if (state.shuffle && state.playlist.size > 1) {
            var randomIndex: Int
            do {
                randomIndex = kotlin.random.Random.nextInt(state.playlist.size)
            } while (randomIndex == state.currentIndex)
            randomIndex
        } else {
            if (state.currentIndex > 0) state.currentIndex - 1 else state.playlist.size - 1
        }
        val prevTrack = state.playlist[prevIndex]
        playTrack(prevTrack, state.playlist)
    }

    fun toggleShuffle() {
        _playerState.update { it.copy(shuffle = !it.shuffle) }
    }

    fun cycleRepeatMode() {
        _playerState.update {
            val nextMode = when (it.repeat) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            it.copy(repeat = nextMode)
        }
    }

    fun setPlayerMode(mode: PlayerMode) {
        _playerState.update { it.copy(playerMode = mode) }
    }

    fun toggleFavoriteCurrent() {
        val current = _playerState.value.currentTrack ?: return
        scope.launch {
            val isFav = musicRepository.toggleFavorite(current)
            _playerState.update { state ->
                state.copy(currentTrack = state.currentTrack?.copy(isFavorite = isFav))
            }
        }
    }

    private fun loadLyricsForTrack(track: Track) {
        lyricsJob?.cancel()
        lyricsJob = scope.launch {
            val lyrics = lyricsProvider.getLyrics(track.id, track.artist, track.title)
            _currentLyrics.value = lyrics
        }
    }

    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = scope.launch {
            var lastHistoryRecord = 0L
            while (isActive) {
                val remoteClient = CastManager.castSession.value?.remoteMediaClient
                if (CastManager.isCasting.value && remoteClient != null) {
                    // === CAST: leggi posizione e durata dal RemoteMediaClient ===
                    val currentPos = remoteClient.approximateStreamPosition
                    val duration = remoteClient.mediaInfo?.streamDuration?.coerceAtLeast(0L) ?: _playerState.value.durationMs
                    val isPlaying = remoteClient.isPlaying
                    
                    // Rileva se il dispositivo Cast è passato al brano successivo autonomamente
                    val castTrackId = remoteClient.mediaInfo?.customData?.optString("trackId")
                    val currentTrack = _playerState.value.currentTrack
                    
                    if (castTrackId != null && currentTrack != null && castTrackId != currentTrack.id) {
                        Log.d("PlayerManager", "Il Cast è avanzato automaticamente a: $castTrackId")
                        // Troviamo il nuovo brano nella playlist
                        val newIndex = _playerState.value.playlist.indexOfFirst { it.id == castTrackId }
                        if (newIndex != -1) {
                            val nextTrack = _playerState.value.playlist[newIndex]
                            _playerState.update {
                                it.copy(
                                    currentTrack = nextTrack,
                                    currentIndex = newIndex,
                                    positionMs = currentPos,
                                    durationMs = nextTrack.durationMs,
                                    isPlaying = true
                                )
                            }
                            // Aggiorna testi
                            _currentLyrics.value = null
                            loadLyricsForTrack(nextTrack)
                            
                            // Appendi il *prossimo* brano alla coda per mantenere la catena
                            val nextNextIndex = if (_playerState.value.shuffle) kotlin.random.Random.nextInt(_playerState.value.playlist.size) else (newIndex + 1)
                            if (nextNextIndex in _playerState.value.playlist.indices) {
                                CastManager.appendToQueue(_playerState.value.playlist[nextNextIndex])
                            }
                        }
                    } else {
                        // Aggiornamento normale
                        _playerState.update {
                            it.copy(
                                positionMs = currentPos,
                                durationMs = if (duration > 0) duration else it.durationMs,
                                isPlaying = isPlaying
                            )
                        }
                    }

                    // Se per qualche motivo il Cast finisce la coda senza auto-play, gestiamo il fallback
                    if (remoteClient.playerState == com.google.android.gms.cast.MediaStatus.PLAYER_STATE_IDLE &&
                        remoteClient.idleReason == com.google.android.gms.cast.MediaStatus.IDLE_REASON_FINISHED) {
                        // Aspettiamo un attimo per vedere se riparte
                        delay(1000)
                        if (remoteClient.playerState == com.google.android.gms.cast.MediaStatus.PLAYER_STATE_IDLE) {
                            withContext(Dispatchers.Main) { next() }
                        }
                    }
                } else if (exoPlayer.isPlaying) {
                    // === LOCALE: leggi posizione da ExoPlayer ===
                    val currentPos = exoPlayer.currentPosition
                    val duration = exoPlayer.duration.coerceAtLeast(0L)
                    _playerState.update { 
                        it.copy(
                            positionMs = currentPos,
                            durationMs = if (duration > 0) duration else it.durationMs
                        )
                    }
                    
                    // Salva periodicamente lo stato di ascolto ogni 5 secondi
                    val now = System.currentTimeMillis()
                    if (now - lastHistoryRecord > 5000L) {
                        lastHistoryRecord = now
                        _playerState.value.currentTrack?.let { track ->
                            musicRepository.recordHistory(track, currentPos)
                        }
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopProgressTicker() {
        progressTickerJob?.cancel()
    }
    
    // --- ExoPlayer.Listener ---
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _playerState.update { it.copy(isPlaying = isPlaying) }
        if (isPlaying) {
            startProgressTicker()
        } else {
            stopProgressTicker()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            isNaturalEnd = true
            val state = _playerState.value
            when (state.repeat) {
                RepeatMode.ONE -> seekTo(0L)
                RepeatMode.ALL -> next()
                RepeatMode.OFF -> {
                    // Fine playlist: ferma gracefully senza errori
                    val hasNext = state.currentIndex < state.playlist.size - 1
                    if (hasNext) {
                        next()
                    } else {
                        // Ultimo brano — reset stato senza mostrare errori
                        _playerState.update { it.copy(isPlaying = false, positionMs = 0L) }
                        scope.launch {
                            delay(500)
                            isNaturalEnd = false
                        }
                    }
                }
            }
        }
        
        if (playbackState == Player.STATE_READY) {
            // Riproduzione avviata con successo — reset contatore errori
            consecutiveErrors = 0
            isNaturalEnd = false
            _playerState.update { it.copy(isLoading = false) }
        } else if (playbackState == Player.STATE_BUFFERING) {
            _playerState.update { it.copy(isLoading = true) }
        }
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        super.onPlayerError(error)
        Log.e("PlayerManager", "ExoPlayer Error: ${error.message}", error)
        // Ignora gli errori che scattano subito dopo la fine naturale di un brano
        // (alcuni formati YouTube emettono un PlaybackException durante il cleanup dello stream)
        if (isNaturalEnd) {
            Log.w("PlayerManager", "Errore ignorato: arrivato dopo fine naturale del brano")
            return
        }
        handlePlaybackError("Errore di riproduzione: verifica la tua connessione.")
    }

    fun clearError() {
        _playerState.update { it.copy(errorMessage = null) }
    }

    // ─── Sleep Timer ─────────────────────────────────────────────────────────

    /** Avvia un sleep timer. Ferma la musica dopo [minutes] minuti. */
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        val totalMs = minutes * 60_000L
        _playerState.update { it.copy(sleepTimerRemainingMs = totalMs) }

        sleepTimerJob = scope.launch {
            val tickMs = 1000L
            var remaining = totalMs
            while (remaining > 0 && isActive) {
                delay(tickMs)
                remaining -= tickMs
                _playerState.update { it.copy(sleepTimerRemainingMs = remaining.coerceAtLeast(0L)) }
            }
            if (isActive) {
                // Timer scaduto: ferma la musica
                withContext(Dispatchers.Main) {
                    if (exoPlayer.isPlaying) exoPlayer.pause()
                    _playerState.update { it.copy(isPlaying = false, sleepTimerRemainingMs = 0L) }
                }
            }
        }
    }

    /** Annulla il sleep timer attivo. */
    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _playerState.update { it.copy(sleepTimerRemainingMs = 0L) }
    }

    private fun handlePlaybackError(message: String) {
        consecutiveErrors++
        _playerState.update { 
            it.copy(
                isPlaying = false,
                errorMessage = message
            ) 
        }
        
        if (consecutiveErrors >= 3) {
            Log.e("PlayerManager", "Raggiunto limite errori consecutivi ($consecutiveErrors). Fermo il playback.")
            _playerState.update { it.copy(errorMessage = "Rete instabile o brani non disponibili. Riproduzione interrotta.") }
        } else {
            // Cerca di skippare al prossimo brano
            scope.launch {
                delay(2000)
                next()
            }
        }
    }
}
