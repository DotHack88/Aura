package com.muse.app.cast

import android.content.Context
import android.util.Log
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.common.images.WebImage
import android.net.Uri
import com.muse.app.domain.model.Track
import com.muse.app.player.NewPipeStreamExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object CastManager {

    private val TAG = "CastManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Stato sessione attiva
    private val _castSession = MutableStateFlow<CastSession?>(null)
    val castSession: StateFlow<CastSession?> = _castSession

    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting

    private var castContext: CastContext? = null

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            Log.d(TAG, "Cast session avviata: ")
            _castSession.value = session
            _isCasting.value = true
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            _castSession.value = session
            _isCasting.value = true
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            Log.d(TAG, "Cast session terminata")
            _castSession.value = null
            _isCasting.value = false
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            _isCasting.value = false
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            Log.e(TAG, "Cast session fallita: ")
            _isCasting.value = false
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
    }

    fun init(context: Context) {
        try {
            castContext = CastContext.getSharedInstance(context)
            castContext?.sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
            // Controlla se c'è già una sessione attiva
            _castSession.value = castContext?.sessionManager?.currentCastSession
            _isCasting.value = _castSession.value != null
        } catch (e: Exception) {
            Log.e(TAG, "Impossibile inizializzare CastContext: ")
        }
    }

    /** Esegui il cast di un singolo brano (sovrascrivendo la coda) */
    fun castTrack(track: Track) {
        val session = _castSession.value ?: return
        scope.launch {
            try {
                val mediaInfo = buildMediaInfo(track) ?: return@launch
                val loadRequest = MediaLoadRequestData.Builder()
                    .setMediaInfo(mediaInfo)
                    .setAutoplay(true)
                    .build()
                session.remoteMediaClient?.load(loadRequest)
                Log.d(TAG, "Cast avviato per: ${track.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Errore durante il cast", e)
            }
        }
    }

    /** Aggiunge un brano alla coda esistente del dispositivo Cast in background */
    fun appendToQueue(track: Track) {
        val session = _castSession.value ?: return
        scope.launch {
            try {
                val mediaInfo = buildMediaInfo(track) ?: return@launch
                val queueItem = com.google.android.gms.cast.MediaQueueItem.Builder(mediaInfo)
                    .setAutoplay(true)
                    .build()
                session.remoteMediaClient?.queueAppendItem(queueItem, null)
                Log.d(TAG, "Aggiunto alla coda Cast: ${track.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Errore durante l'append alla coda", e)
            }
        }
    }

    /** Helper per costruire MediaInfo da un Track estraendo l'URL */
    private suspend fun buildMediaInfo(track: Track): MediaInfo? {
        val streamUrl = withContext(Dispatchers.IO) {
            NewPipeStreamExtractor.getAudioStreamUrl(track.id)
        } ?: run {
            Log.e(TAG, "Impossibile ottenere stream URL per ${track.title}")
            return null
        }

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, track.title)
            putString(MediaMetadata.KEY_ARTIST, track.artist)
            if (track.album != null) putString(MediaMetadata.KEY_ALBUM_TITLE, track.album)
            if (track.thumbnailUrl.isNotEmpty()) {
                addImage(WebImage(Uri.parse(track.thumbnailUrl)))
            }
        }

        return MediaInfo.Builder(streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("audio/mp4")
            .setMetadata(metadata)
            // Impostiamo l'ID del brano come contentId personalizzato (utile per l'associazione)
            .setCustomData(org.json.JSONObject().put("trackId", track.id))
            .build()
    }

    /** Ferma il cast e disconnetti */
    fun stopCasting() {
        castContext?.sessionManager?.endCurrentSession(true)
    }

    /** Verifica se Cast è disponibile (Google Play Services presenti) */
    fun isAvailable() = castContext != null
}
