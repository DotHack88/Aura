package com.muse.app.player

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.concurrent.TimeUnit

object NewPipeStreamExtractor {
    private const val TAG = "NewPipeExtractor"

    // User-Agent realistico per evitare blocchi di YouTube
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/116.0.0.0 Mobile Safari/537.36"

    fun init(context: Context) {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        NewPipe.init(
            object : Downloader() {
                override fun execute(request: Request): Response {
                    val method  = request.httpMethod()
                    val url     = request.url()
                    val headers = request.headers()
                    val data    = request.dataToSend()

                    val builder = okhttp3.Request.Builder()
                        .url(url)
                        // User-Agent obbligatorio per non essere bloccati da YouTube
                        .header("User-Agent", USER_AGENT)
                        // GDPR consent cookie per evitare popup di consenso
                        .header("Cookie", "SOCS=CAISNQgDEitib3FfaWRlbnRpdHlmcm9udGVuZHVpc2VydmVyXzIwMjMwODI5LjA3X3AxGgJpdCAD")


                    // Aggiunge tutti gli header della richiesta NewPipe
                    headers.forEach { (key, list) ->
                        list.forEach { value -> builder.addHeader(key, value) }
                    }

                    when (method) {
                        "POST" -> builder.post(
                            data?.toRequestBody() ?: ByteArray(0).toRequestBody()
                        )
                        else   -> builder.get()
                    }

                    val response     = okHttpClient.newCall(builder.build()).execute()
                    
                    if (response.isRedirect) {
                        return Response(
                            response.code,
                            response.message,
                            mapOf("Location" to listOf(response.header("Location")!!)),
                            "",
                            url
                        )
                    }

                    val responseBody = response.body?.string() ?: ""

                    val responseHeaders = mutableMapOf<String, List<String>>()
                    response.headers.names().forEach { name ->
                        responseHeaders[name] = response.headers.values(name)
                    }

                    return Response(
                        response.code,
                        response.message,
                        responseHeaders,
                        responseBody,
                        url
                    )
                }
            },
            // Imposta la localizzazione italiana per i metadati
            Localization("it", "IT")
        )
        Log.d(TAG, "NewPipeExtractor inizializzato con User-Agent e Localization IT")
    }

    /**
     * Recupera l'URL diretto dello stream audio per un dato videoId YouTube.
     * Prova prima gli stream DASH, poi fallback sugli stream progressivi HLS.
     * Restituisce null solo se non trova nulla o si verifica un errore non recuperabile.
     */
    suspend fun getAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val pageUrl  = "https://www.youtube.com/watch?v=$videoId"
            val extractor = ServiceList.YouTube.getStreamExtractor(pageUrl)
            extractor.fetchPage()

            val audioStreams = extractor.audioStreams
            Log.d(TAG, "NewPipe: trovati ${audioStreams.size} audio stream per $videoId")

            if (audioStreams.isEmpty()) {
                Log.w(TAG, "Nessun audio stream trovato per $videoId")
                return@withContext null
            }

            // Preferisce stream PROGRESSIVE (URL diretto, nessun manifest necessario)
            val progressive = audioStreams.filter {
                it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
            }

            val best: AudioStream? = if (progressive.isNotEmpty()) {
                progressive.maxByOrNull { it.averageBitrate }
            } else {
                // Fallback: qualsiasi stream disponibile con URL diretto
                audioStreams.filter { !it.content.isNullOrEmpty() }
                            .maxByOrNull { it.averageBitrate }
            }

            val url = best?.content
            if (url.isNullOrEmpty()) {
                Log.w(TAG, "URL stream vuoto per $videoId (bitrate=${best?.averageBitrate})")
                return@withContext null
            }

            Log.d(TAG, "NewPipe OK per $videoId: ${best.averageBitrate}kbps | url=${url.take(80)}…")
            return@withContext url

        } catch (e: Exception) {
            Log.e(TAG, "Errore estrazione NewPipe per $videoId: ${e.javaClass.simpleName} - ${e.message}")
            return@withContext null
        }
    }

    /**
     * Recupera l'URL diretto dello stream video per un dato videoId YouTube.
     * Preferisce DASH manifest (che include video HD e audio) se disponibile.
     */
    suspend fun getVideoStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val pageUrl = "https://www.youtube.com/watch?v=$videoId"
            val extractor = ServiceList.YouTube.getStreamExtractor(pageUrl)
            extractor.fetchPage()

            // Prima scelta: DASH manifest, supportato in modo nativo da ExoPlayer
            val dashUrl = extractor.dashMpdUrl
            if (!dashUrl.isNullOrEmpty()) {
                Log.d(TAG, "NewPipe Video: Usa DASH manifest per $videoId")
                return@withContext dashUrl
            }

            // Seconda scelta: stream progressivi MP4 (spesso limitati a 360p)
            val videoStreams: List<VideoStream> = extractor.videoStreams
            val progressive = videoStreams.filter {
                it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                !it.content.isNullOrEmpty() && !it.isVideoOnly
            }.sortedByDescending { it.height }

            if (progressive.isNotEmpty()) {
                val best = progressive.firstOrNull { it.height <= 720 } ?: progressive.first()
                Log.d(TAG, "NewPipe Video: Usa stream progressivo per $videoId: ${best.height}p")
                return@withContext best.content
            }

            // Ultima risorsa HLS
            val hlsUrl = extractor.hlsUrl
            if (!hlsUrl.isNullOrEmpty()) {
                Log.d(TAG, "NewPipe Video: Fallback su HLS per $videoId")
                return@withContext hlsUrl
            }

            Log.w(TAG, "Nessun video stream trovato per $videoId")
            return@withContext null

        } catch (e: Exception) {
            Log.e(TAG, "Errore estrazione video NewPipe per $videoId: ${e.javaClass.simpleName} - ${e.message}")
            return@withContext null
        }
    }

    /**
     * Cerca brani su YouTube usando il SearchExtractor di NewPipe.
     * Non richiede API key, restituisce risultati accurati e pertinenti alla query.
     * Ritorna una lista di mappe con: id, title, artist, thumbnailUrl, durationMs, viewsText
     */
    suspend fun searchTracks(query: String, maxResults: Int = 25): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val searchInfo = SearchInfo.getInfo(
                ServiceList.YouTube,
                ServiceList.YouTube.searchQHFactory.fromQuery(
                    query,
                    listOf("videos"),   // filtro: solo video (non playlist/canali)
                    ""
                )
            )

            val results = searchInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .take(maxResults)

            Log.d(TAG, "NewPipe Search: trovati ${results.size} risultati per \"$query\"")

            results.map { item ->
                mapOf<String, Any>(
                    "id"           to (item.url.substringAfter("v=").substringBefore("&")),
                    "title"        to item.name,
                    "artist"       to (item.uploaderName ?: ""),
                    "thumbnailUrl" to (item.thumbnails.maxByOrNull { it.height }?.url ?: ""),
                    "durationMs"   to (if (item.duration > 0) item.duration * 1000L else 210_000L),
                    "viewsText"    to formatViews(item.viewCount)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore ricerca NewPipe per \"$query\": ${e.javaClass.simpleName} - ${e.message}")
            emptyList()
        }
    }

    private fun formatViews(count: Long): String {
        return when {
            count <= 0           -> ""
            count >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.1f Mld visualizzazioni", count / 1_000_000_000.0)
            count >= 1_000_000   -> String.format(java.util.Locale.US, "%.1f Mln visualizzazioni", count / 1_000_000.0)
            count >= 1_000       -> "${count / 1_000}K visualizzazioni"
            else                 -> "$count visualizzazioni"
        }
    }
}
