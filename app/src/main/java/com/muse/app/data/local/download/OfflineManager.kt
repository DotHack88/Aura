package com.muse.app.data.local.download

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.muse.app.data.local.AppDatabase
import com.muse.app.domain.model.Track
import com.muse.app.player.NewPipeStreamExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class DownloadProgress {
    object Preparing : DownloadProgress()
    data class Downloading(val percent: Int) : DownloadProgress()
    data class Done(val filePath: String) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}

class OfflineManager(
    private val context: Context,
    private val database: AppDatabase
) {
    private val TAG = "OfflineManager"
    private val managerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val gson = Gson()
    private val prefs: SharedPreferences = context.getSharedPreferences("offline_manager_prefs", Context.MODE_PRIVATE)

    data class ActiveDownload(val track: Track, val progress: DownloadProgress)

    private val _activeDownloads = kotlinx.coroutines.flow.MutableStateFlow<Map<String, ActiveDownload>>(emptyMap())
    val activeDownloads: kotlinx.coroutines.flow.StateFlow<Map<String, ActiveDownload>> = _activeDownloads
    private val _activeJobs = mutableMapOf<String, Job>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Cartella interna dove vengono salvati i file audio scaricati */
    private val downloadDir: File
        get() = File(context.filesDir, "offline_tracks").also { it.mkdirs() }

    /** Restituisce il file locale per un dato track ID */
    fun getLocalFile(trackId: String): File = File(downloadDir, "$trackId.m4a")

    // ── SharedPreferences helpers ────────────────────────────────────────────

    /** Salva un track come "fallito" nelle prefs, per renderlo persistente */
    private fun persistFailedTrack(track: Track) {
        val failedIds = prefs.getStringSet(KEY_FAILED_IDS, mutableSetOf())!!.toMutableSet()
        failedIds.add(track.id)
        prefs.edit()
            .putStringSet(KEY_FAILED_IDS, failedIds)
            .putString("${KEY_TRACK_PREFIX}${track.id}", gson.toJson(track))
            .apply()
    }

    /** Rimuove un track dai falliti (download avviato o completato) */
    private fun clearPersistedTrack(trackId: String) {
        val failedIds = prefs.getStringSet(KEY_FAILED_IDS, mutableSetOf())!!.toMutableSet()
        failedIds.remove(trackId)
        prefs.edit()
            .putStringSet(KEY_FAILED_IDS, failedIds)
            .remove("${KEY_TRACK_PREFIX}${trackId}")
            .apply()
    }

    /** Carica i track falliti dalle prefs e li mette in activeDownloads */
    fun restoreFailedDownloads() {
        val failedIds = prefs.getStringSet(KEY_FAILED_IDS, emptySet()) ?: emptySet()
        if (failedIds.isEmpty()) return
        val type = object : TypeToken<Track>() {}.type
        val restored = failedIds.mapNotNull { id ->
            val json = prefs.getString("${KEY_TRACK_PREFIX}${id}", null) ?: return@mapNotNull null
            try {
                val track = gson.fromJson<Track>(json, type)
                // Non mostrare se già completato sul disco
                if (getLocalFile(id).exists()) {
                    clearPersistedTrack(id)
                    return@mapNotNull null
                }
                id to ActiveDownload(track, DownloadProgress.Error("Download interrotto"))
            } catch (e: Exception) {
                Log.w(TAG, "Impossibile deserializzare track $id: ${e.message}")
                null
            }
        }.toMap()
        if (restored.isNotEmpty()) {
            _activeDownloads.value = restored
        }
    }

    // ── Download logic ───────────────────────────────────────────────────────

    /** Avvia (o riprova) il download di un brano */
    fun startDownload(track: Track) {
        val existing = _activeDownloads.value[track.id]
        // Se già in corso (non in errore), ignora
        if (existing != null && existing.progress !is DownloadProgress.Error) return

        // Se era in errore, rimuovilo prima di ripartire
        if (existing?.progress is DownloadProgress.Error) {
            _activeDownloads.update { it - track.id }
            clearPersistedTrack(track.id)
        }

        val job = managerScope.launch {
            _activeDownloads.update { it + (track.id to ActiveDownload(track, DownloadProgress.Preparing)) }
            val outputFile = getLocalFile(track.id)

            if (outputFile.exists()) {
                Log.d(TAG, "File già presente per ${track.id}, skip download")
                database.trackDao().insertOrUpdate(
                    database.trackDao().getTrackById(track.id)?.copy(
                        isDownloaded = true,
                        localFilePath = outputFile.absolutePath
                    ) ?: com.muse.app.data.local.TrackEntity(
                        id = track.id, title = track.title, artist = track.artist, artistId = track.artistId,
                        thumbnailUrl = track.thumbnailUrl, durationMs = track.durationMs, album = track.album,
                        albumBrowseId = track.albumBrowseId, isDownloaded = true, localFilePath = outputFile.absolutePath
                    )
                )
                _activeDownloads.update { it - track.id }
                return@launch
            }

            try {
                // 1. Recupera l'URL dello stream audio
                val streamUrl = withContext(Dispatchers.IO) {
                    NewPipeStreamExtractor.getAudioStreamUrl(track.id)
                }
                if (streamUrl == null) {
                    handleError(track, "Impossibile ottenere lo stream audio")
                    return@launch
                }

                // 2. Scarica il file con OkHttp
                val request = Request.Builder().url(streamUrl).build()
                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }

                if (!response.isSuccessful) {
                    handleError(track, "Errore HTTP: ${response.code}")
                    return@launch
                }

                val body = response.body ?: run {
                    handleError(track, "Risposta vuota dal server")
                    return@launch
                }

                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                var lastReportedPercent = -1
                withContext(Dispatchers.IO) {
                    body.byteStream().use { input ->
                        outputFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloadedBytes += read
                                if (totalBytes > 0) {
                                    val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                                    if (percent / 5 != lastReportedPercent / 5) {
                                        lastReportedPercent = percent
                                    }
                                }
                            }
                        }
                    }
                }
                // Aggiorna UI solo dopo IO (sul coroutine scope corretto)
                if (lastReportedPercent >= 0) {
                    _activeDownloads.update { it + (track.id to ActiveDownload(track, DownloadProgress.Downloading(lastReportedPercent))) }
                }


                Log.d(TAG, "Download completato: ${outputFile.absolutePath}")

                // 3. Salva nel DB e rimuovi dalle prefs
                val existingEntity = database.trackDao().getTrackById(track.id)
                val entity = existingEntity?.copy(
                    isDownloaded = true,
                    localFilePath = outputFile.absolutePath
                ) ?: com.muse.app.data.local.TrackEntity(
                    id = track.id, title = track.title, artist = track.artist, artistId = track.artistId,
                    thumbnailUrl = track.thumbnailUrl, durationMs = track.durationMs, album = track.album,
                    albumBrowseId = track.albumBrowseId, isDownloaded = true, localFilePath = outputFile.absolutePath
                )
                database.trackDao().insertOrUpdate(entity)
                clearPersistedTrack(track.id)
                _activeDownloads.update { it - track.id }

            } catch (e: IOException) {
                Log.e(TAG, "Errore I/O durante il download di ${track.id}", e)
                getLocalFile(track.id).delete()
                handleError(track, "Errore di rete: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Errore generico durante il download di ${track.id}", e)
                getLocalFile(track.id).delete()
                handleError(track, "Errore: ${e.message}")
            }
        }
        _activeJobs[track.id] = job
    }

    /** Annulla un download in corso */
    fun cancelDownload(trackId: String) {
        _activeJobs[trackId]?.cancel()
        _activeJobs.remove(trackId)
        val file = getLocalFile(trackId)
        if (file.exists()) {
            file.delete()
        }
        clearPersistedTrack(trackId)
        _activeDownloads.update { it - trackId }
        Log.d(TAG, "Download annullato per $trackId")
    }

    private fun handleError(track: Track, message: String) {
        persistFailedTrack(track) // Salva su disco per sopravvivere ai riavvii
        _activeDownloads.update { it + (track.id to ActiveDownload(track, DownloadProgress.Error(message))) }
    }

    /** Rimuove il file locale e aggiorna Room */
    suspend fun removeDownload(trackId: String) {
        val file = getLocalFile(trackId)
        if (file.exists()) {
            file.delete()
            Log.d(TAG, "File rimosso: ${file.absolutePath}")
        }
        clearPersistedTrack(trackId)
        _activeDownloads.update { it - trackId }
        val existingEntity = database.trackDao().getTrackById(trackId)
        if (existingEntity != null) {
            database.trackDao().insertOrUpdate(existingEntity.copy(isDownloaded = false, localFilePath = null))
        }
    }

    /** Controlla se il file locale esiste fisicamente */
    fun isDownloaded(trackId: String): Boolean = getLocalFile(trackId).exists()

    companion object {
        private const val KEY_FAILED_IDS = "failed_track_ids"
        private const val KEY_TRACK_PREFIX = "track_data_"
    }
}

