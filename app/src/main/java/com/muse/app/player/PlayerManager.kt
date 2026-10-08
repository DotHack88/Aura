package com.muse.app.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
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
import kotlinx.coroutines.SupervisorJob
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
    // Dispatchers.Default: non blocca il Main thread e non viene throttlato in background/lockscreen.
    // SupervisorJob: un figlio che fallisce non cancella gli altri job (es. errore su un brano non blocca il next()).
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : Player.Listener {

    // WakeLock parziale: mantiene la CPU attiva durante l'estrazione dello stream rete.
    // Viene acquisito all'inizio di extractJob e rilasciato appena ExoPlayer è pronto.
    private val wakeLock: PowerManager.WakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Muse::StreamExtractionWakeLock")

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
        .setLoadControl(
            androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    50000, // min buffer 50s
                    120000, // max buffer 2 mins
                    2500, // buffer for playback
                    5000 // buffer for playback after rebuffer
                ).build()
        )
        .build().apply {
        addListener(this@PlayerManager)
    }

    private var progressTickerJob: Job? = null
    private var extractJob: Job? = null
    private var lyricsJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var crossfadeJob: Job? = null
    private var crossfadeTriggeredForTrack: String? = null // evita doppio trigger per lo stesso brano
    private var currentVideoId: String? = null
    private var consecutiveErrors = 0
    /** True per i ~2s subito dopo STATE_ENDED, per ignorare errori ExoPlayer post-fine naturale */
    private var isNaturalEnd = false
    /** True mentre stiamo già gestendo un STATE_ENDED, per evitare doppia esecuzione (race condition) */
    private var isHandlingEnd = false

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    // ─── Muse Radio Engine ────────────────────────────────────────────────────
    /** Job per il riempimento asincrono della coda radio */
    private var radioRefillJob: Job? = null
    /** True mentre è in corso un fetch di nuovi candidati (evita fetch paralleli) */
    private var isRadioRefilling = false
    /** ID del brano seme corrente della radio: l'ultimo brano ascoltato sostanzialmente (>30s) */
    private var radioSeedTrackId: String? = null
    /** Artisti ascoltati nella sessione corrente — usati per il ranking dei candidati */
    private val sessionArtists = mutableListOf<String>()
    /** Segnali di ascolto — soglie raggiunte per il brano corrente */
    private var signalReached30s = false
    private var signalReached50pct = false
    private var signalReachedComplete = false

    /** Tipi di segnale ascolto per il ranking locale */
    private enum class ListenSignal {
        SKIP_SHORT,   // skip < 10s   → −5
        SKIP_LONG,    // skip < 30s   → −3
        PLAYED_30S,   // ascolto > 30s → +2
        PLAYED_50PCT, // ascolto > 50% → +3
        COMPLETED,    // ascolto completo → +4
        REPLAY        // replay → +5
    }

    /** Aggiorna il punteggio locale di un brano tramite il repository (proxy del peso di gradimento). */
    private fun recordListenSignal(trackId: String, signal: ListenSignal) {
        if (trackId.isBlank()) return
        val delta = when (signal) {
            ListenSignal.SKIP_SHORT   -> -5
            ListenSignal.SKIP_LONG    -> -3
            ListenSignal.PLAYED_30S   ->  2
            ListenSignal.PLAYED_50PCT ->  3
            ListenSignal.COMPLETED    ->  4
            ListenSignal.REPLAY       ->  5
        }
        scope.launch {
            musicRepository.adjustTrackScore(trackId, delta)
            Log.d("MuseRadio", "Signal $signal (Δ$delta) → $trackId")
        }
    }

    /**
     * Genera nuovi candidati radio a partire da [seedId] e li appende alla playlist corrente,
     * escludendo i brani già presenti in coda. I candidati vengono ordinati per playCount locale
     * (proxy di gradimento) con un bonus per gli artisti già ascoltati nella sessione.
     */
    private fun generateRadioQueue(seedId: String, currentPlaylist: List<Track>, params: String? = null, appendTracks: Boolean = true) {
        if (isRadioRefilling) return
        isRadioRefilling = true
        radioRefillJob?.cancel()
        radioRefillJob = scope.launch {
            try {
                Log.d("MuseRadio", "Refill coda da seme: $seedId appendTracks=$appendTracks (sessione artisti: $sessionArtists)")
                val upNext = musicRepository.getUpNext(seedId, params)
                val candidates = upNext.tracks
                
                withContext(Dispatchers.Main) {
                    _playerState.update { it.copy(
                        upNextChips = upNext.chips,
                        selectedChip = upNext.chips.find { c -> c.isSelected }?.title
                    ) }
                }

                // Se appendTracks=false, aggiorniamo solo i chip senza modificare la playlist
                if (!appendTracks) {
                    Log.d("MuseRadio", "Chip aggiornati (${upNext.chips.size}), nessun brano aggiunto alla coda")
                    return@launch
                }
                
                val existingIds = currentPlaylist.map { it.id }.toSet()

                // Recupera i punteggi dalla history locale per il ranking
                val scored = candidates
                    .filter { it.id !in existingIds }
                    .map { track ->
                        val baseScore = musicRepository.getTrackScore(track.id).toFloat()
                        // Bonus sessione: +3 se l'artista è già nella sessione corrente
                        val sessionBonus = if (sessionArtists.any {
                                it.equals(track.artist, ignoreCase = true)
                            }) 3f else 0f
                        Pair(track, baseScore + sessionBonus)
                    }
                    .sortedByDescending { it.second }
                    .map { it.first }
                    .take(15)

                if (scored.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val state = _playerState.value
                        // Ricontrolla che il brano seme sia ancora quello corrente o vicino
                        val newPlaylist = state.playlist + scored
                        _playerState.update { it.copy(playlist = newPlaylist) }
                        Log.d("MuseRadio", "Aggiunti ${scored.size} brani alla coda (tot: ${newPlaylist.size})")
                    }
                }
            } catch (e: Exception) {
                Log.e("MuseRadio", "Errore refill coda radio", e)
            } finally {
                isRadioRefilling = false
            }
        }
    }

    /**
     * Controlla se la coda ha meno di 3 brani in avanti rispetto al brano corrente.
     * Se sì, richiede un refill usando il seme radio attuale (ultimo brano ascoltato >30s).
     */
    private fun maybeRefillQueue() {
        val state = _playerState.value
        val tracksAhead = state.playlist.size - state.currentIndex - 1
        Log.d("MuseRadio", "Brani rimanenti in coda: $tracksAhead")
        if (tracksAhead <= 3) {
            val seed = radioSeedTrackId ?: state.currentTrack?.id ?: return
            val currentChipParams = state.upNextChips.find { it.title == state.selectedChip }?.endpointParams
            generateRadioQueue(seed, state.playlist, currentChipParams)
        }
    }

    /**
     * Applica un filtro (Chip) e ricarica i brani successivi nella coda in base ad esso.
     */
    fun selectChip(chip: com.muse.app.domain.model.Chip) {
        val state = _playerState.value
        val seed = radioSeedTrackId ?: state.currentTrack?.id ?: return
        
        // Manteniamo solo la coda fino al brano corrente
        val newPlaylist = state.playlist.take(state.currentIndex + 1)
        _playerState.update { it.copy(playlist = newPlaylist, selectedChip = chip.title) }
        
        // Forziamo il caricamento dei nuovi brani filtrati
        isRadioRefilling = false
        generateRadioQueue(seed, newPlaylist, chip.endpointParams)
    }

    // ─── Audio Focus ─────────────────────────────────────────────────────────
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    /** True se la pausa è stata causata da noi a seguito di un focus loss (per riprendere al GAIN) */
    private var pausedByFocusLoss = false
    /** Volume pre-duck, per ripristinarlo dopo un AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK */
    private var volumeBeforeDuck = 1f

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Chiamata terminata / altra app ha smesso di usare l'audio
                Log.d("PlayerManager", "AudioFocus: GAIN — ripristino volume e ripresa")
                exoPlayer.volume = volumeBeforeDuck
                if (pausedByFocusLoss && !exoPlayer.isPlaying) {
                    exoPlayer.play()
                }
                pausedByFocusLoss = false
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Perdita definitiva (altra app ha preso il focus per lungo tempo)
                Log.d("PlayerManager", "AudioFocus: LOSS — pausa definitiva")
                if (exoPlayer.isPlaying) {
                    pausedByFocusLoss = false
                    exoPlayer.pause()
                }
                releaseAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Perdita temporanea (es. telefonata, notifica vocale)
                Log.d("PlayerManager", "AudioFocus: LOSS_TRANSIENT — pausa temporanea")
                if (exoPlayer.isPlaying) {
                    pausedByFocusLoss = true
                    exoPlayer.pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Perdita parziale (es. notifica GPS) — abbassa il volume senza pausare
                Log.d("PlayerManager", "AudioFocus: LOSS_TRANSIENT_CAN_DUCK — abbasso volume")
                volumeBeforeDuck = exoPlayer.volume
                exoPlayer.volume = exoPlayer.volume * 0.3f
            }
        }
    }

    @Suppress("DEPRECATION")
    private val audioFocusRequest: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
        } else null

    /** Richiede l'Audio Focus prima di avviare la riproduzione. Ritorna true se il focus è stato ottenuto. */
    private fun requestAudioFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ||
               result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED
    }

    /** Rilascia l'Audio Focus (chiamato quando l'utente mette in pausa manualmente). */
    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    // ─── Becoming Noisy (cuffie staccate) ────────────────────────────────────
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.d("PlayerManager", "Cuffie staccate — pausa automatica")
                if (exoPlayer.isPlaying) {
                    pausedByFocusLoss = false // pausa manuale, non riprendere automaticamente
                    exoPlayer.pause()
                }
            }
        }
    }

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
        // Registra il receiver per cuffie staccate
        val noisyFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        context.registerReceiver(becomingNoisyReceiver, noisyFilter)
    }

    fun playTrack(track: Track, playlist: List<Track> = listOf(track), startPositionMs: Long = 0L) {
        val index = playlist.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        // Ogni nuovo brano avviato esplicitamente resetta il contatore errori
        if (startPositionMs == 0L) {
            consecutiveErrors = 0
        }
        // Resetta il crossfade per il nuovo brano
        crossfadeJob?.cancel()
        crossfadeTriggeredForTrack = null
        // Assicura che il volume sia sempre al massimo all'inizio di un nuovo brano
        exoPlayer.volume = 1f
        isNaturalEnd = false

        // Salva lo stato video PRIMA dell'update per sapere se riattivarlo dopo
        val wasInVideoMode = _playerState.value.isVideoMode

        _playerState.update {
            it.copy(
                currentTrack = track,
                playlist = playlist,
                currentIndex = index,
                isPlaying = false,
                positionMs = startPositionMs,
                durationMs = track.durationMs,
                playerMode = if (it.playerMode == PlayerMode.MINI) PlayerMode.FULL else it.playerMode,
                errorMessage = null,
                // Resetta temporaneamente la modalità video mentre carichiamo l'audio del nuovo brano
                isVideoMode = false,
                isLoading = true
            )
        }
        
        scope.launch {
            musicRepository.recordHistory(track, startPositionMs, true)
        }
        
        _currentLyrics.value = null
        loadLyricsForTrack(track)

        // ── Muse Radio Engine: avvio / reset segnali per il nuovo brano ──
        signalReached30s = false
        signalReached50pct = false
        signalReachedComplete = false
        radioSeedTrackId = track.id
        if (playlist.size == 1) {
            // Brano singolo: genera la coda radio completa (chip + brani)
            sessionArtists.clear()
            sessionArtists.add(track.artist)
            generateRadioQueue(track.id, listOf(track), appendTracks = true)
        } else {
            // Playlist già esistente (es. ricerca, album): recupera solo i chip senza
            // sovrascrivere i brani della coda. Poi controlla se occorre un refill.
            sessionArtists.add(track.artist)
            generateRadioQueue(track.id, playlist, appendTracks = false)
            maybeRefillQueue()
        }

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
            if (startPositionMs > 0) {
                val remoteClient = CastManager.castSession.value?.remoteMediaClient
                remoteClient?.seek(com.google.android.gms.cast.MediaSeekOptions.Builder().setPosition(startPositionMs).build())
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
                    requestAudioFocus()
                    exoPlayer.stop()
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    if (startPositionMs > 0) exoPlayer.seekTo(startPositionMs)
                    exoPlayer.play()
                }
                return@launch
            }

            // 2. Nessun file locale → estrai da YouTube con retry.
            // Acquisisce il WakeLock per mantenere la CPU attiva durante il fetch di rete
            // in background/lockscreen (senza di esso Android può sospendere il processo
            // proprio mentre NewPipe estrae l'URL dello stream).
            if (!wakeLock.isHeld) wakeLock.acquire(30_000L) // timeout massimo 30s

            var streamUrl: String? = null

            try {
                if (track.id.startsWith("http://") || track.id.startsWith("https://")) {
                    // URL diretto (radio italiane, stream HTTP/HTTPS)
                    streamUrl = track.id
                } else if (track.durationMs == -1L) {
                    // Live stream YouTube (radio) — usa Piped API, molto più affidabile per i live
                    Log.d("PlayerManager", "Rilevato live stream YouTube per ${track.id} — uso Piped API")
                    streamUrl = NewPipeStreamExtractor.getLiveStreamUrl(track.id)
                    if (streamUrl == null) {
                        Log.e("PlayerManager", "Impossibile ottenere live stream per ${track.id}")
                    }
                } else {
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
                            delay(1500)
                        }
                    }
                }
            } finally {
                // Rilascia il WakeLock non appena l'URL è stato risolto (o fallito).
                // ExoPlayer gestisce il proprio WakeLock internamente durante il buffering.
                if (wakeLock.isHeld) wakeLock.release()
            }

            if (streamUrl != null) {
                if (startPositionMs == 0L) {
                    consecutiveErrors = 0
                }
                val mediaItem = createMediaItem(streamUrl, track)
                withContext(Dispatchers.Main) {
                    requestAudioFocus()
                    exoPlayer.stop()
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    if (startPositionMs > 0) exoPlayer.seekTo(startPositionMs)
                    exoPlayer.play()
                }
                // Se prima era in modalità video, rilancia automaticamente il video per il nuovo brano
                if (wasInVideoMode) {
                    Log.d("PlayerManager", "Era in video mode — auto-switch video per nuovo brano")
                    delay(600) // Attesa minima per permettere all'audio di avviarsi
                    switchToVideoMode()
                }
            } else {
                Log.e("PlayerManager", "Stream non ottenuto per ${track.id}. Skip.")
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
                        // Forza la qualità video massima disponibile (evita la partenza in bassa qualità)
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setMaxVideoSizeSd() // inizia da SD poi...
                            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE) // ...consenti tutte le risoluzioni
                            .setMinVideoSize(720, 0) // preferisci almeno 720p
                            .setForceHighestSupportedBitrate(true) // scegli sempre il bitrate più alto
                            .build()
                        exoPlayer.play()
                        _playerState.update { it.copy(isVideoMode = true, currentYouTubeVideoId = videoId) }
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
                    // Ripristina la selezione automatica per l'audio
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setForceHighestSupportedBitrate(false)
                        .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                        .setMinVideoSize(0, 0)
                        .build()
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
            .setAlbumTitle(track.album ?: "")
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
        scope.launch(Dispatchers.Main) {
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
                return@launch
            }
            // Riproduzione locale
            if (exoPlayer.isPlaying) {
                pausedByFocusLoss = false // pausa manuale: non riprendere automaticamente al GAIN
                exoPlayer.pause()
                releaseAudioFocus()
            } else {
                requestAudioFocus()
                exoPlayer.play()
            }
        }
    }

    /**
     * Aggiunge un brano alla coda come prossimo brano da riprodurre.
     * Ogni nuovo brano viene inserito subito dopo il brano corrente (currentIndex + 1),
     * scalando indietro i brani aggiunti in precedenza.
     * Esempio: se aggiungi Brano1 poi Brano2, l'ordine sarà: [corrente] → Brano2 → Brano1 → ...
     */
    fun addToQueue(track: Track) {
        val state = _playerState.value
        if (state.currentTrack == null) {
            // Nessuna traccia in riproduzione → avviamo direttamente
            playTrack(track)
            return
        }
        val newPlaylist = state.playlist.toMutableList()
        // Rimuove eventuale duplicato esistente in coda (non tocca la traccia corrente)
        val existingIndex = newPlaylist.indexOfFirst { it.id == track.id }
        if (existingIndex > state.currentIndex) {
            newPlaylist.removeAt(existingIndex)
        }
        // Inserisce SEMPRE in posizione currentIndex + 1 (prossimo da suonare)
        val insertIndex = (state.currentIndex + 1).coerceAtMost(newPlaylist.size)
        newPlaylist.add(insertIndex, track)
        _playerState.update { it.copy(playlist = newPlaylist) }
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
        scope.launch(Dispatchers.Main) {
            val remoteClient = CastManager.castSession.value?.remoteMediaClient
            if (CastManager.isCasting.value && remoteClient != null) {
                remoteClient.seek(com.google.android.gms.cast.MediaSeekOptions.Builder().setPosition(positionMs).build())
            } else {
                exoPlayer.seekTo(positionMs)
            }
            _playerState.update { it.copy(positionMs = positionMs) }
        }
    }

    fun next() {
        scope.launch(Dispatchers.Main) {
            val state = _playerState.value
            if (state.playlist.isEmpty()) return@launch

            // ── Muse Radio: segnale skip sul brano corrente ──
            val currentPos = exoPlayer.currentPosition
            state.currentTrack?.let { current ->
                if (!signalReachedComplete && !signalReached30s) {
                    // Skip prima dei 10 secondi
                    if (currentPos < 10_000L) {
                        recordListenSignal(current.id, ListenSignal.SKIP_SHORT)
                    } else if (currentPos < 30_000L) {
                        recordListenSignal(current.id, ListenSignal.SKIP_LONG)
                    }
                }
            }

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
                        return@launch
                    }
                } else {
                    candidate
                }
            }
            val nextTrack = state.playlist[nextIndex]
            // Controlla se riempire la coda prima di avanzare
            val tracksAhead = state.playlist.size - nextIndex - 1
            if (tracksAhead <= 3) {
                val seed = radioSeedTrackId ?: nextTrack.id
                generateRadioQueue(seed, state.playlist)
            }
            playTrack(nextTrack, state.playlist)
        }
    }

    fun previous() {
        scope.launch(Dispatchers.Main) {
            val state = _playerState.value
            if (state.playlist.isEmpty()) return@launch

            if (state.positionMs > 3000L || exoPlayer.currentPosition > 3000L) {
                seekTo(0L)
                return@launch
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

    /**
     * Imposta la durata del crossfade in ms. 0 = disattivato.
     * Valori consigliati: 3000-8000 ms.
     */
    fun setCrossfadeDuration(durationMs: Long) {
        _playerState.update { it.copy(crossfadeDurationMs = durationMs.coerceIn(0L, 10_000L)) }
        Log.d("PlayerManager", "Crossfade impostato a ${durationMs}ms")
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
        // ExoPlayer deve essere letto dal Main thread.
        progressTickerJob = scope.launch(Dispatchers.Main) {
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

                    // ── Muse Radio: segnali di ascolto progressivi ──
                    val radioState = _playerState.value
                    radioState.currentTrack?.let { current ->
                        if (duration > 0) {
                            // Segnale >30 secondi
                            if (!signalReached30s && currentPos >= 30_000L) {
                                signalReached30s = true
                                recordListenSignal(current.id, ListenSignal.PLAYED_30S)
                                // Aggiorna il seme radio all'ultimo brano ascoltato sostanzialmente
                                radioSeedTrackId = current.id
                                if (!sessionArtists.contains(current.artist)) {
                                    sessionArtists.add(current.artist)
                                }
                                Log.d("MuseRadio", "30s ascoltati: ${current.title} — seme: ${current.id}")
                            }
                            // Segnale >50%
                            if (!signalReached50pct && currentPos >= duration / 2) {
                                signalReached50pct = true
                                recordListenSignal(current.id, ListenSignal.PLAYED_50PCT)
                                Log.d("MuseRadio", "50% ascoltato: ${current.title}")
                            }
                        }
                    }

                    // === CROSSFADE: pre-avvio del prossimo brano ===
                    val state = _playerState.value
                    val crossfadeMs = state.crossfadeDurationMs
                    val trackId = state.currentTrack?.id
                    if (crossfadeMs > 0 && duration > 0 && !state.isVideoMode &&
                        crossfadeTriggeredForTrack != trackId
                    ) {
                        val timeLeft = duration - currentPos
                        if (timeLeft in 500L..crossfadeMs) {
                            crossfadeTriggeredForTrack = trackId
                            Log.d("PlayerManager", "Crossfade avviato: ${timeLeft}ms rimasti, fade=${crossfadeMs}ms")
                            crossfadeJob?.cancel()
                            crossfadeJob = scope.launch {
                                val steps = 20
                                val stepDelay = (timeLeft / steps).coerceAtLeast(50L)
                                for (i in steps downTo 0) {
                                    if (!isActive) break
                                    withContext(Dispatchers.Main) {
                                        exoPlayer.volume = (i.toFloat() / steps)
                                    }
                                    delay(stepDelay)
                                }
                                withContext(Dispatchers.Main) {
                                    val hasNext = state.currentIndex < state.playlist.size - 1
                                    val willRepeat = state.repeat == RepeatMode.ALL
                                    exoPlayer.volume = 1f
                                    if (hasNext || willRepeat) next()
                                }
                            }
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
            // Guard contro doppia esecuzione (race condition ExoPlayer)
            if (isHandlingEnd) return
            isHandlingEnd = true
            isNaturalEnd = true
            val state = _playerState.value

            // ── Muse Radio: segnale COMPLETED sul brano corrente ──
            if (!signalReachedComplete) {
                signalReachedComplete = true
                state.currentTrack?.let { current ->
                    recordListenSignal(current.id, ListenSignal.COMPLETED)
                    // Aggiorna il seme radio all'ultimo brano completato
                    radioSeedTrackId = current.id
                    if (!sessionArtists.contains(current.artist)) {
                        sessionArtists.add(current.artist)
                    }
                    Log.d("MuseRadio", "Brano completato: ${current.title} — seme aggiornato")
                }
            }

            // IMPORTANTE: onPlaybackStateChanged viene chiamato direttamente da ExoPlayer
            // sul thread della looper di ExoPlayer (non sul Main thread). Per evitare che
            // il passaggio al brano successivo venga throttlato dal sistema quando l'app è
            // in background/lockscreen, launciamo tutto il lavoro pesante su scope (Default).
            scope.launch {
                when (state.repeat) {
                    RepeatMode.ONE -> {
                        // Seek all'inizio + play esplicito per evitare il doppio ascolto
                        signalReachedComplete = false
                        signalReached30s = false
                        signalReached50pct = false
                        recordListenSignal(state.currentTrack?.id ?: "", ListenSignal.REPLAY)
                        withContext(Dispatchers.Main) {
                            exoPlayer.seekTo(0L)
                            exoPlayer.play()
                        }
                    }
                    RepeatMode.ALL -> next()
                    RepeatMode.OFF -> {
                        // Fine playlist: ferma gracefully senza errori
                        val hasNext = state.currentIndex < state.playlist.size - 1
                        if (hasNext) {
                            next()
                        } else {
                            // Ultimo brano — reset stato senza mostrare errori
                            withContext(Dispatchers.Main) {
                                _playerState.update { it.copy(isPlaying = false, positionMs = 0L) }
                            }
                            delay(500)
                            isNaturalEnd = false
                        }
                    }
                }
                // Rilascia il lock dopo un breve delay per immunizzarsi da STATE_ENDED ridondanti
                delay(400)
                isHandlingEnd = false
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

        val pos = exoPlayer.currentPosition
        val duration = exoPlayer.duration
        
        // Risolve il bug della perdita di connessione a fine brano:
        // se c'è un errore negli ultimi 5 secondi, consideriamo il brano come terminato normalmente
        if (duration > 0 && pos >= duration - 5000) {
            Log.w("PlayerManager", "Errore vicino alla fine del brano, forzo il passaggio al successivo")
            next()
            return
        }

        val currentTrack = _playerState.value.currentTrack
        if (pos > 0 && currentTrack != null && consecutiveErrors < 2) {
            Log.d("PlayerManager", "Tentativo di ripristino della traccia da $pos ms")
            consecutiveErrors++
            playTrack(currentTrack, _playerState.value.playlist, pos)
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

    /** Rilascia tutte le risorse (chiamare quando il PlayerManager viene distrutto). */
    fun release() {
        releaseAudioFocus()
        try {
            context.unregisterReceiver(becomingNoisyReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w("PlayerManager", "BecomingNoisyReceiver già deregistrato")
        }
        progressTickerJob?.cancel()
        extractJob?.cancel()
        lyricsJob?.cancel()
        sleepTimerJob?.cancel()
        radioRefillJob?.cancel()
        crossfadeJob?.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        equalizerManager.release()
        exoPlayer.release()
    }
}
