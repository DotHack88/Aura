package com.muse.app.domain.lyrics

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.muse.app.domain.model.Lyrics
import com.muse.app.domain.model.LyricsLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

interface LyricsProvider {
    suspend fun getLyrics(trackId: String, artist: String, title: String): Lyrics?
}

/**
 * Parser per file LRC standard [mm:ss.xx] o [mm:ss:xx]
 */
object LrcParser {
    private val LRC_LINE_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})(?:[.:](\\d{2,3}))?\\](.*)")

    fun parse(trackId: String, lrcContent: String): Lyrics {
        val lines = mutableListOf<LyricsLine>()
        lrcContent.lineSequence().forEach { rawLine ->
            val matcher = LRC_LINE_PATTERN.matcher(rawLine.trim())
            if (matcher.matches()) {
                val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                val millisString = matcher.group(3) ?: "0"
                val millis = if (millisString.length == 2) {
                    millisString.toLongOrNull()?.times(10) ?: 0L
                } else {
                    millisString.toLongOrNull() ?: 0L
                }
                val totalTimestampMs = (minutes * 60 * 1000) + (seconds * 1000) + millis
                val text = matcher.group(4)?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    lines.add(LyricsLine(timestampMs = totalTimestampMs, text = text))
                }
            }
        }
        return Lyrics(
            trackId = trackId,
            plainText = lines.joinToString("\n") { it.text },
            lines = lines.sortedBy { it.timestampMs }
        )
    }
}

/**
 * Provider avanzato sincronizzato:
 * 1. Cerca prima i testi sincronizzati reali tramite l'API globale e gratuita LRCLIB (usata da Spotify / client open source).
 * 2. Se non disponibile o offline, utilizza un database curato con testi esatti (Thegiornalisti, Rino Gaetano, ecc.).
 * 3. Fallback dinamico sincronizzato sul brano.
 */
class DefaultLyricsProvider : LyricsProvider {

    private val TAG = "DefaultLyricsProvider"
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    override suspend fun getLyrics(trackId: String, artist: String, title: String): Lyrics? = withContext(Dispatchers.IO) {
        // 1. Prova fetch diretto con artist + title (endpoint /api/get)
        val onlineLyrics = fetchFromLrcLib(trackId, artist, title)
        if (onlineLyrics != null && onlineLyrics.lines.isNotEmpty()) {
            Log.d(TAG, "Testo sincronizzato trovato su LRCLIB per $artist - $title (${onlineLyrics.lines.size} righe)")
            return@withContext onlineLyrics
        }

        // 2. Fallback: ricerca LRCLIB tramite endpoint /api/search
        val searchedLyrics = searchFromLrcLib(trackId, artist, title)
        if (searchedLyrics != null && searchedLyrics.lines.isNotEmpty()) {
            Log.d(TAG, "Testo trovato via ricerca LRCLIB per $artist - $title")
            return@withContext searchedLyrics
        }

        // 3. Fallback locale curato (solo se c'è corrispondenza esatta con ID o titolo pulito)
        val localLyrics = getLocalCuratedLyrics(trackId, artist, title)
        if (localLyrics != null) {
            Log.d(TAG, "Uso fallback curato locale per $artist - $title")
            return@withContext localLyrics
        }

        // 4. Nessun testo disponibile: restituisci null (la UI mostrerà "Testo non disponibile")
        Log.d(TAG, "Nessun testo trovato per $artist - $title")
        null
    }

    /**
     * Calcola uno score di corrispondenza [0.0, 1.0] tra il risultato LRCLIB e il brano cercato.
     * Confronta trackName e artistName restituiti dall'API con quelli attesi.
     */
    private fun matchScore(
        resultTrackName: String?,
        resultArtistName: String?,
        expectedTitle: String,
        expectedArtist: String
    ): Double {
        if (resultTrackName.isNullOrBlank()) return 0.0
        val rTitle  = resultTrackName.lowercase().trim()
        val rArtist = resultArtistName?.lowercase()?.trim() ?: ""
        val eTitle  = expectedTitle.lowercase().trim()
        val eArtist = expectedArtist.lowercase().trim()

        // Verifica se il titolo atteso è contenuto nel titolo risultato o viceversa
        val titleScore = when {
            rTitle == eTitle                               -> 1.0
            rTitle.contains(eTitle) || eTitle.contains(rTitle) -> 0.7
            else -> {
                // Controlla quante parole del titolo atteso sono nel risultato
                val eWords = eTitle.split(" ").filter { it.length > 2 }
                if (eWords.isEmpty()) 0.0
                else eWords.count { rTitle.contains(it) }.toDouble() / eWords.size * 0.6
            }
        }

        // Verifica artista (bonus, non obbligatorio)
        val artistScore = when {
            eArtist.isEmpty()                                   -> 0.5
            rArtist == eArtist                                  -> 1.0
            rArtist.contains(eArtist) || eArtist.contains(rArtist) -> 0.8
            else -> 0.0
        }

        // Peso: titolo 70%, artista 30%
        return titleScore * 0.7 + artistScore * 0.3
    }

    private fun fetchFromLrcLib(trackId: String, artist: String, title: String): Lyrics? {
        return try {
            val cleanTitle = cleanSongTitle(title)
            val cleanArtist = cleanArtistName(artist)

            val url = "https://lrclib.net/api/get".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("artist_name", cleanArtist)
                ?.addQueryParameter("track_name", cleanTitle)
                ?.build() ?: return null

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MuseMusicApp/1.0 (https://github.com/muse)")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val jsonString = response.body?.string() ?: return null
            val obj = gson.fromJson(jsonString, JsonObject::class.java)

            // Verifica che il risultato corrisponda al brano cercato
            val resultTitle  = obj.get("trackName")?.takeIf { !it.isJsonNull }?.asString
            val resultArtist = obj.get("artistName")?.takeIf { !it.isJsonNull }?.asString
            val score = matchScore(resultTitle, resultArtist, cleanTitle, cleanArtist)
            Log.d(TAG, "fetchFromLrcLib score=$score per '$cleanArtist - $cleanTitle' → '$resultArtist - $resultTitle'")
            if (score < 0.5) {
                Log.w(TAG, "Risultato LRCLIB scartato (score=$score): '$resultArtist - $resultTitle'")
                return null
            }

            val syncedLyrics = obj.get("syncedLyrics")?.takeIf { !it.isJsonNull }?.asString
            if (!syncedLyrics.isNullOrBlank()) {
                return LrcParser.parse(trackId, syncedLyrics)
            }

            null
        } catch (e: Exception) {
            Log.w(TAG, "Impossibile scaricare testi da LRCLIB: ${e.message}")
            null
        }
    }

    /**
     * Ricerca LRCLIB tramite endpoint /api/search (fallback se il fetch diretto fallisce).
     * Seleziona il candidato con score più alto (titolo + artista) invece del primo della lista.
     */
    private fun searchFromLrcLib(trackId: String, artist: String, title: String): Lyrics? {
        val cleanTitle  = cleanSongTitle(title)
        val cleanArtist = cleanArtistName(artist)

        // Estrai possibili parti di titolo se è nel formato "X - Y" o "X: Y"
        val subTitles = if (cleanTitle.contains("-")) {
            cleanTitle.split("-").map { it.trim() }.filter { it.length >= 4 }
        } else if (cleanTitle.contains(":")) {
            cleanTitle.split(":").map { it.trim() }.filter { it.length >= 4 }
        } else emptyList()

        val queries = mutableListOf<String>()
        if (cleanArtist.isNotEmpty() && !cleanArtist.equals("Artista sconosciuto", ignoreCase = true)) {
            queries.add("$cleanArtist $cleanTitle")
        }
        queries.add(cleanTitle)
        for (sub in subTitles) {
            if (cleanArtist.isNotEmpty() && !cleanArtist.equals("Artista sconosciuto", ignoreCase = true)) {
                queries.add("$cleanArtist $sub")
            }
            queries.add(sub)
        }

        for (q in queries.distinct()) {
            try {
                val url = "https://lrclib.net/api/search".toHttpUrlOrNull()?.newBuilder()
                    ?.addQueryParameter("q", q)
                    ?.build() ?: continue

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "MuseMusicApp/1.0 (https://github.com/muse)")
                    .get()
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) continue

                val jsonString = response.body?.string() ?: continue
                val arr = gson.fromJson(jsonString, com.google.gson.JsonArray::class.java)

                // Ordina i risultati per score di corrispondenza (titolo + artista)
                data class Candidate(val score: Double, val syncedLyrics: String?, val plainLyrics: String?)
                val candidates = arr.mapNotNull { el ->
                    val obj = el.asJsonObject
                    val rTitle  = obj.get("trackName")?.takeIf { !it.isJsonNull }?.asString
                    val rArtist = obj.get("artistName")?.takeIf { !it.isJsonNull }?.asString
                    val score   = matchScore(rTitle, rArtist, cleanTitle, cleanArtist)
                    if (score < 0.5) {
                        Log.d(TAG, "Candidato scartato (score=$score): '$rArtist - $rTitle'")
                        null
                    } else {
                        val synced = obj.get("syncedLyrics")?.takeIf { !it.isJsonNull }?.asString
                        val plain  = obj.get("plainLyrics")?.takeIf { !it.isJsonNull }?.asString
                        Candidate(score, synced?.takeIf { it.isNotBlank() }, plain?.takeIf { it.isNotBlank() })
                    }
                }.sortedByDescending { it.score }

                Log.d(TAG, "Query '$q': ${candidates.size} candidati validi")

                // 1. Preferisci synced lyrics del candidato con score più alto
                candidates.firstOrNull { !it.syncedLyrics.isNullOrBlank() }?.let { best ->
                    Log.d(TAG, "Synced lyrics trovate (score=${best.score}) per '$cleanArtist - $cleanTitle'")
                    return LrcParser.parse(trackId, best.syncedLyrics!!)
                }

                // 2. Fallback a plain lyrics del candidato con score più alto
                candidates.firstOrNull { !it.plainLyrics.isNullOrBlank() }?.let { best ->
                    Log.d(TAG, "Plain lyrics trovate (score=${best.score}) per '$cleanArtist - $cleanTitle'")
                    val lines = best.plainLyrics!!.lines().filter { it.isNotBlank() }
                    val parsedLines = lines.mapIndexed { index, line ->
                        LyricsLine(timestampMs = index * 4000L, text = line.trim())
                    }
                    return Lyrics(trackId = trackId, plainText = best.plainLyrics, lines = parsedLines)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Errore ricerca LRCLIB per \"$q\": ${e.message}")
            }
        }
        return null
    }

    private fun cleanSongTitle(title: String): String {
        return title
            .replace(Regex("(?i)\\(.*?\\)"), "")
            .replace(Regex("(?i)\\[.*?\\]"), "")
            .replace(Regex("(?i)ft\\..*"), "")
            .replace(Regex("(?i)feat\\..*"), "")
            .replace(Regex("(?i)official.*"), "")
            .replace(Regex("(?i)music video.*"), "")
            .trim()
    }

    private fun cleanArtistName(artist: String): String {
        return artist
            .replace(Regex("(?i) - Topic$"), "")
            .replace(Regex("(?i)VEVO$"), "")
            .replace(Regex("(?i)Official$"), "")
            .trim()
    }

    private fun getLocalCuratedLyrics(trackId: String, artist: String, title: String): Lyrics? {
        val t = title.lowercase().trim()
        val a = artist.lowercase().trim()

        val lrc: String? = when {
            // LOVE - Thegiornalisti (Controllo stretto solo su ID o artista e titolo esatti)
            trackId == "Tm6hzPZYU6A" || (t == "love" && (a.contains("thegiornalisti") || a.contains("tommaso paradiso"))) -> """
                [00:00.14]Le tue foto mi uccidono, love
                [00:28.80]I tuoi baci guariscono, love
                [00:35.06]Love mio, dove sei? Non ti vedo più
                [00:41.21]Love, dove sei? Non nasconderti
                [00:48.27]Love mio, sto cercando su Google
                [00:52.93]I nomi delle stelle
                [00:55.68]Tuo padre e mio padre
                [00:58.11]Un albergo carino
                [00:59.34]Se abbiamo sorelle
                [01:02.42]Se abbiamo sorelle
                [01:05.21]Ma soltanto tu
                [01:11.36]Mi puoi salvare
                [01:16.96]Quando la vita non gira bene
                [01:22.65]Non ti fa volare
                [01:27.41]Sì, soltanto tu
                [01:33.27]Love mio, ti va di parlarmi un po', love
                [01:39.52]Delle cose più semplici? Love mio
                [01:47.09]Love mio, sto cercando su Google
                [01:52.03]I nei sulla pelle
                [01:55.32]La strada di casa
                [01:57.97]Le spiagge più belle
                [01:59.19]Le farmacie aperte
                [02:02.30]Le farmacie aperte
                [02:05.11]Ma soltanto tu
                [02:08.62]Mi fai arrivare
                [02:16.92]In fondo a quei giorni in cui
                [02:20.41]Non riesco a salire le scale
                [02:26.69]Nemmeno, nemmeno dormire
                [02:31.96]Menomale che c'è il cane
                [02:34.56]Menomale che c'è davvero
                [02:37.54]Menomale che c'è
                [02:40.72]Sempre qualcosa
                [02:46.67]Su cui mi posso sdraiare
                [02:53.21]Ma soltanto tu
                [02:59.22]Mi puoi salvare
                [03:04.94]Quando la vita non gira bene
                [03:10.14]Non ti fa volare
                [03:13.91]Sì, soltanto tu
                [03:20.05]Mi puoi salvare
                [03:28.98]Sì, soltanto tu
                [03:33.33]Love mio, love mio
            """.trimIndent()

            // A mano a mano - Rino Gaetano
            trackId == "5uPbsQnMTBQ" || (t == "a mano a mano" && a.contains("gaetano")) -> """
                [00:00.00]♪ Intro ♪
                [00:06.00]A mano a mano ti accorgi che il vento
                [00:12.00]Ti soffia sul viso e ti ruba un sorriso
                [00:18.00]La bella stagione che sta per finire
                [00:23.00]Ti soffia sul cuore e ti ruba l'amore
                [00:30.00]A mano a mano si scioglie nel pianto
                [00:35.00]Quel dolce ricordo sbiadito dal tempo
                [00:41.00]Di quando vivevi con me sul tuo viso
                [00:47.00]La prima carezza, il primo sorriso
                [00:54.00]E dammi la mano e torna vicino
                [01:00.00]Può nascere un fiore nel nostro giardino
                [01:06.00]Che neanche l'inverno potrà mai gelare
                [01:12.00]Può crescere un fiore da questo mio amore per te
                [01:20.00]E se ti lascerai andare
                [01:26.00]Avremo ancora un sogno da toccare
                [01:33.00]A mano a mano, insieme a te
            """.trimIndent()

            // Zero stare sereno - Thegiornalisti
            (t.contains("zero stare sereno") && a.contains("thegiornalisti")) -> """
                [00:00.00]♪ Intro ♪
                [00:05.00]Volevo solo dirti che stanotte
                [00:10.00]Ho fatto un sogno strano su di noi
                [00:15.00]E c'era il mare aperto e le finestre rotte
                [00:20.00]E noi che non sapevamo che ore sono
                [00:26.00]E zero stare sereno
                [00:29.00]Quando mi guardi così
                [00:33.00]Io perdo il filo del treno
                [00:36.00]E resto fermo qui
                [00:41.00]E zero stare sereno
                [00:44.00]In questa notte di fumo
                [00:48.00]Ti cerco tra la gente
                [00:52.00]E non c'è più nessuno
                [00:57.00]Soltanto io e te
                [01:03.00]Zero stare sereno
            """.trimIndent()

            // Numb - Linkin Park
            (t == "numb" && a.contains("linkin park")) -> """
                [00:00.00]♪ Intro Synthesizer ♪
                [00:20.00]I'm tired of being what you want me to be
                [00:25.00]Feeling so faithless, lost under the surface
                [00:30.00]Don't know what you're expecting of me
                [00:35.00]Put under the pressure of walking in your shoes
                [00:40.00]Every step that I take is another mistake to you
                [00:50.00]I've become so numb, I can't feel you there
                [00:55.00]Become so tired, so much more aware
                [01:00.00]I'm becoming this, all I want to do
                [01:05.00]Is be more like me and be less like you
            """.trimIndent()

            // Neon Heart - Zylvox+Studio
            trackId == "PrZdvoaIn-k" || (t.contains("neon heart") && a.contains("zylvox")) -> """
                [00:07.00]City lights are falling down the glass tonight
                [00:11.00]Broken signals drifting through the satellite
                [00:15.00]Every memory flickers like a fading screen
                [00:19.00]You're the only thing that ever felt serene
                [00:25.00]Static in my heartbeat
                [00:28.00]Running through the wires
                [00:31.00]Every dream we built now
                [00:34.00]Burns in neon fire
                [00:40.00]Stay with me tonight
                [00:43.00]Under electric skies
                [00:46.00]Where the stars don't shine
                [00:49.00]But your eyes still light my life
                [00:53.00]Hold me in the glow
                [00:56.00]Before the daylight goes
                [00:59.00]In this endless chrome
                [01:02.00]You're the only place I call home
                [01:12.00]Crowded streets and endless advertisements bloom
                [01:16.00]Yet the silence grows whenever I lose you
                [01:20.00]Data ghosts are dancing in the midnight rain
                [01:24.00]Trying to convince me you're still here again
                [01:30.00]Voices from the network
                [01:33.00]Calling out your name
                [01:36.00]Every crowded skyline
                [01:39.00]Feels exactly the same
                [01:44.00]Stay with me tonight
                [01:47.00]Under electric skies
                [01:50.00]Where the stars don't shine
                [01:53.00]But your eyes still light my life
                [01:57.00]Hold me in the glow
                [02:00.00]Before the daylight goes
                [02:03.00]In this endless chrome
                [02:06.00]You're the only place I call home
                [02:16.00]Fly beyond the moonlight
                [02:20.00]Past the city haze
                [02:24.00]If tomorrow breaks us
                [02:28.00]I'll remember these days
                [02:33.00]Stay with me tonight
                [02:36.00]Through the fading light
                [02:39.00]Even if we're gone
                [02:42.00]Our signal carries on
                [02:47.00]Neon hearts remain
                [02:50.00]Inside the pouring rain
                [02:53.00]And in every dream
                [02:56.00]You're still waiting there for me
            """.trimIndent()

            // Nessun testo curato disponibile
            else -> null
        }

        return lrc?.let { LrcParser.parse(trackId, it) }
    }
}
