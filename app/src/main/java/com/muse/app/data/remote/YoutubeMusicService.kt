package com.muse.app.data.remote

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.muse.app.domain.model.Album
import com.muse.app.domain.model.Artist
import com.muse.app.domain.model.ArtistDetails
import com.muse.app.domain.model.Chip
import com.muse.app.domain.model.SearchResult
import com.muse.app.domain.model.Track
import com.muse.app.domain.model.UpNextResult
import com.muse.app.utils.toHighResThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Service dedicato all'interrogazione diretta dell'endpoint YouTube Music (https://music.youtube.com / Innertube API)
 * Ottimizzato specificamente per query musicali, brani, album ed artisti ufficiali.
 */
class YoutubeMusicService(
    private val client: OkHttpClient = OkHttpClient()
) {

    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private fun buildInnertubeRequest(endpoint: String, payloadBody: String): Request {
        val url = "https://music.youtube.com/youtubei/v1/$endpoint?prettyPrint=false"
        val contextJson = """
            "context": {
                "client": {
                    "clientName": "WEB_REMIX",
                    "clientVersion": "1.20260901.01.00",
                    "hl": "it",
                    "gl": "IT"
                }
            }
        """.trimIndent()
        
        val fullBody = "{\n$contextJson,\n$payloadBody\n}"
        
        return Request.Builder()
            .url(url)
            .post(fullBody.toRequestBody(JSON))
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
            .addHeader("Referer", "https://music.youtube.com/")
            .addHeader("Origin", "https://music.youtube.com")
            .build()
    }

    suspend fun searchMusic(query: String): SearchResult = withContext(Dispatchers.IO) {
        try {
            val payload = "\"query\": \"${query.replace("\"", "\\\"")}\""
            val request = buildInnertubeRequest("search", payload)

            val response = client.newCall(request).execute()
            val rawJson = response.body?.string().orEmpty()

            parseYoutubeMusicResponse(rawJson, query)
        } catch (e: Exception) {
            e.printStackTrace()
            SearchResult()
        }
    }

    /**
     * Ricerca dedicata per gli Album su YouTube Music.
     * Usa il params "EgWKAQIYAWoKEAoQAxAEEAkQBQ%3D%3D" che corrisponde al filtro "Albums" dell'Innertube.
     */
    suspend fun searchAlbums(query: String): List<Album> = withContext(Dispatchers.IO) {
        try {
            val q = query.replace("\"", "\\\"")
            // Rimuoviamo il params perché l'Innertube API lo ha deprecato/bloccato, causava messageRenderer vuoto.
            // Aggiungiamo " album" alla query per forzare i risultati ad essere album.
            val payload = "\"query\": \"$q album\""
            val request = buildInnertubeRequest("search", payload)
            val response = client.newCall(request).execute()
            val rawJson = response.body?.string().orEmpty()

            val albums = mutableListOf<Album>()
            val root = gson.fromJson(rawJson, JsonObject::class.java) ?: return@withContext albums

            // Naviga fino a sectionListRenderer.contents
            val sections = root
                .getAsJsonObject("contents")
                ?.getAsJsonObject("tabbedSearchResultsRenderer")
                ?.getAsJsonArray("tabs")?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("tabRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents")

            sections?.forEach { secEl ->
                // Check if it's a Top Result Card (usually for Albums/Artists)
                val cardShelf = secEl.asJsonObject.getAsJsonObject("musicCardShelfRenderer")
                if (cardShelf != null) {
                    val titleRuns = cardShelf.getAsJsonObject("title")?.getAsJsonArray("runs")
                        ?: cardShelf.getAsJsonObject("header")?.getAsJsonObject("musicCardShelfHeaderBasicRenderer")
                            ?.getAsJsonObject("title")?.getAsJsonArray("runs")
                    val title = titleRuns?.firstOrNull()?.asJsonObject?.get("text")?.asString.orEmpty()
                    
                    val endpoint = titleRuns?.firstOrNull()?.asJsonObject?.getAsJsonObject("navigationEndpoint")
                    val browseId = endpoint?.getAsJsonObject("browseEndpoint")?.get("browseId")?.asString
                    
                    val thumb = cardShelf.getAsJsonObject("thumbnail")
                        ?.getAsJsonObject("musicThumbnailRenderer")
                        ?.getAsJsonObject("thumbnail")
                        ?.getAsJsonArray("thumbnails")?.lastOrNull()?.asJsonObject
                        ?.get("url")?.asString.orEmpty().toHighResThumbnail()
                        
                    val subtitleRuns = cardShelf.getAsJsonObject("subtitle")?.getAsJsonArray("runs")
                    val artistNames = mutableListOf<String>()
                    var albumType = "Album"
                    subtitleRuns?.forEach { run ->
                        val text = run.asJsonObject["text"]?.asString.orEmpty().trim()
                        when {
                            text.equals("Singolo", ignoreCase = true) -> albumType = "Singolo"
                            text.equals("EP", ignoreCase = true) -> albumType = "EP"
                            text.isNotEmpty() && text != "•" && text != "·" &&
                                    !text.equals("Album", ignoreCase = true) &&
                                    !text.any { it.isDigit() } -> artistNames.add(text)
                        }
                    }
                    
                    var actualBrowseId = browseId
                    if (actualBrowseId == null || !actualBrowseId.startsWith("MPREb_")) {
                        val mprebMatch = Regex("\"browseId\":\"(MPREb_[^\"]+)\"").find(cardShelf.toString())
                        if (mprebMatch != null) {
                            actualBrowseId = mprebMatch.groupValues[1]
                        }
                    }
                    
                    if (actualBrowseId != null && actualBrowseId.startsWith("MPREb_") && title.isNotEmpty()) {
                        albums.add(
                            Album(
                                id = actualBrowseId,
                                title = title,
                                artist = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else query,
                                coverUrl = thumb,
                                type = albumType,
                                browseId = actualBrowseId
                            )
                        )
                    }
                }

                val shelf = secEl.asJsonObject.getAsJsonObject("musicShelfRenderer")
                    ?: secEl.asJsonObject.getAsJsonObject("itemSectionRenderer")
                shelf?.getAsJsonArray("contents")?.forEach { itemEl ->
                    val renderer = itemEl.asJsonObject.getAsJsonObject("musicResponsiveListItemRenderer")
                        ?: return@forEach
                    val flexCols = renderer.getAsJsonArray("flexColumns") ?: return@forEach
                    if (flexCols.size() == 0) return@forEach

                    val col0 = flexCols[0].asJsonObject
                        .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.getAsJsonObject("text")?.getAsJsonArray("runs")

                    val title = col0?.joinToString("") { it.asJsonObject["text"]?.asString.orEmpty() }?.trim().orEmpty()
                    if (title.isEmpty()) return@forEach

                    val mainNavEndpoint = col0?.get(0)?.asJsonObject?.getAsJsonObject("navigationEndpoint")
                    val mainBrowseId = mainNavEndpoint?.getAsJsonObject("browseEndpoint")?.get("browseId")?.asString

                    val thumb = renderer.getAsJsonObject("thumbnail")
                        ?.getAsJsonObject("musicThumbnailRenderer")
                        ?.getAsJsonObject("thumbnail")
                        ?.getAsJsonArray("thumbnails")?.lastOrNull()?.asJsonObject
                        ?.get("url")?.asString.orEmpty().toHighResThumbnail()

                    // Artista dalla colonna 1
                    val subtitleRuns = if (flexCols.size() > 1) {
                        flexCols[1].asJsonObject
                            .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.getAsJsonObject("text")?.getAsJsonArray("runs")
                    } else null

                    val artistNames = mutableListOf<String>()
                    var albumType = "Album"
                    subtitleRuns?.forEach { run ->
                        val text = run.asJsonObject["text"]?.asString.orEmpty().trim()
                        when {
                            text.equals("Singolo", ignoreCase = true) -> albumType = "Singolo"
                            text.equals("EP", ignoreCase = true) -> albumType = "EP"
                            text.isNotEmpty() && text != "•" && text != "·" &&
                                    !text.equals("Album", ignoreCase = true) &&
                                    !text.any { it.isDigit() } -> artistNames.add(text)
                        }
                    }

                    if (mainBrowseId != null && mainBrowseId.startsWith("MPREb_") && title.isNotEmpty()) {
                        albums.add(
                            Album(
                                id = mainBrowseId,
                                title = title,
                                artist = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else query,
                                coverUrl = thumb,
                                type = albumType,
                                browseId = mainBrowseId
                            )
                        )
                    }
                }
            }
            albums
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }



    suspend fun getAlbumTracks(browseId: String, albumTitle: String, artistName: String, coverUrl: String): List<Track> = withContext(Dispatchers.IO) {
        try {
            val payload = "\"browseId\": \"$browseId\""
            val request = buildInnertubeRequest("browse", payload)

            val response = client.newCall(request).execute()
            val rawJson = response.body?.string().orEmpty()
            if (rawJson.isBlank()) return@withContext emptyList()

            val root = gson.fromJson(rawJson, JsonObject::class.java)
            val secondaryContents = root.getAsJsonObject("contents")
                ?.getAsJsonObject("twoColumnBrowseResultsRenderer")
                ?.getAsJsonObject("secondaryContents")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents")

            val tracks = mutableListOf<Track>()

            secondaryContents?.forEach { secEl ->
                val shelf = secEl.asJsonObject.getAsJsonObject("musicShelfRenderer")
                shelf?.getAsJsonArray("contents")?.forEach { itemEl ->
                    val rend = itemEl.asJsonObject.getAsJsonObject("musicResponsiveListItemRenderer")
                    if (rend != null) {
                        val videoId = rend.getAsJsonObject("playlistItemData")?.get("videoId")?.asString
                            ?: rend.getAsJsonArray("flexColumns")?.get(0)?.asJsonObject
                                ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.getAsJsonObject("text")?.getAsJsonArray("runs")?.get(0)?.asJsonObject
                                ?.getAsJsonObject("navigationEndpoint")?.getAsJsonObject("watchEndpoint")
                                ?.get("videoId")?.asString

                        val titleRuns = rend.getAsJsonArray("flexColumns")?.get(0)?.asJsonObject
                            ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.getAsJsonObject("text")?.getAsJsonArray("runs")

                        val title = titleRuns?.joinToString("") { it.asJsonObject.get("text")?.asString.orEmpty() }?.trim().orEmpty()

                        // Estrazione durata da fixedColumns (es. "3:45") o flexColumns secondari
                        var durationText = "3:30"
                        val fixedColumns = rend.getAsJsonArray("fixedColumns")
                        if (fixedColumns != null && fixedColumns.size() > 0) {
                            val durationRuns = fixedColumns.get(0)?.asJsonObject
                                ?.getAsJsonObject("musicResponsiveListItemFixedColumnRenderer")
                                ?.getAsJsonObject("text")?.getAsJsonArray("runs")
                            val text = durationRuns?.joinToString("") { it.asJsonObject.get("text")?.asString.orEmpty() }?.trim().orEmpty()
                            if (text.contains(":") && text.length <= 8) {
                                durationText = text
                            }
                        } else {
                            val flexCols = rend.getAsJsonArray("flexColumns")
                            if (flexCols != null && flexCols.size() > 1) {
                                val secondColRuns = flexCols.get(1)?.asJsonObject
                                    ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                                    ?.getAsJsonObject("text")?.getAsJsonArray("runs")
                                secondColRuns?.forEach { r ->
                                    val t = r.asJsonObject.get("text")?.asString.orEmpty().trim()
                                    if (t.contains(":") && t.length <= 8) {
                                        durationText = t
                                    }
                                }
                            }
                        }

                        val durationParts = durationText.split(":")
                        val calculatedDurationMs = if (durationParts.size == 2) {
                            (durationParts[0].toLongOrNull() ?: 3) * 60_000L + (durationParts[1].toLongOrNull() ?: 30) * 1_000L
                        } else if (durationParts.size == 3) {
                            (durationParts[0].toLongOrNull() ?: 0) * 3600_000L + (durationParts[1].toLongOrNull() ?: 3) * 60_000L + (durationParts[2].toLongOrNull() ?: 30) * 1_000L
                        } else {
                            210_000L
                        }

                        val extractedThumb = rend.getAsJsonObject("thumbnail")
                            ?.getAsJsonObject("musicThumbnailRenderer")
                            ?.getAsJsonObject("thumbnail")
                            ?.getAsJsonArray("thumbnails")?.lastOrNull()?.asJsonObject
                            ?.get("url")?.asString.orEmpty().toHighResThumbnail()

                        val finalThumb = if (coverUrl.isNotEmpty()) coverUrl else extractedThumb

                        if (!videoId.isNullOrEmpty() && title.isNotEmpty()) {
                            tracks.add(
                                Track(
                                    id = videoId,
                                    title = title,
                                    artist = artistName,
                                    thumbnailUrl = finalThumb,
                                    durationMs = calculatedDurationMs,
                                    album = albumTitle,
                                    albumBrowseId = browseId,
                                    viewsText = "Album • $albumTitle"
                                )
                            )
                        }
                    }
                }
            }

            tracks
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getArtistDetails(browseId: String): ArtistDetails? = withContext(Dispatchers.IO) {
        try {
            val payload = "\"browseId\": \"$browseId\""
            val request = buildInnertubeRequest("browse", payload)

            val response = client.newCall(request).execute()
            val rawJson = response.body?.string().orEmpty()
            if (rawJson.isBlank()) return@withContext null

            val root = gson.fromJson(rawJson, JsonObject::class.java)
            val header = root.getAsJsonObject("header")?.getAsJsonObject("musicImmersiveHeaderRenderer")
                ?: root.getAsJsonObject("header")?.getAsJsonObject("musicVisualHeaderRenderer")
            
            val artistName = header?.getAsJsonObject("title")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString ?: "Artist"
            val avatarUrl = header?.getAsJsonObject("thumbnail")?.getAsJsonObject("musicThumbnailRenderer")
                ?.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")?.lastOrNull()?.asJsonObject?.get("url")?.asString.orEmpty()
            
            val artist = Artist(id = browseId, name = artistName, avatarUrl = avatarUrl, browseId = browseId)
            val tracks = mutableListOf<Track>()
            val albums = mutableListOf<Album>()
            val singles = mutableListOf<Album>()
            val similarArtists = mutableListOf<Artist>()
            
            // Artist page uses tabs[0] inside twoColumnBrowseResultsRenderer
            val contents: JsonArray? = root.getAsJsonObject("contents")
                ?.getAsJsonObject("twoColumnBrowseResultsRenderer")
                ?.getAsJsonArray("tabs")?.get(0)?.asJsonObject
                ?.getAsJsonObject("tabRenderer")?.getAsJsonObject("content")
                ?.getAsJsonObject("sectionListRenderer")?.getAsJsonArray("contents")
                ?: root.getAsJsonObject("contents")
                    ?.getAsJsonObject("singleColumnBrowseResultsRenderer")
                    ?.getAsJsonArray("tabs")?.get(0)?.asJsonObject
                    ?.getAsJsonObject("tabRenderer")?.getAsJsonObject("content")
                    ?.getAsJsonObject("sectionListRenderer")?.getAsJsonArray("contents")
                
            contents?.forEach { sectionElement ->
                val shelf = sectionElement.asJsonObject.getAsJsonObject("musicShelfRenderer")
                val carouselShelf = sectionElement.asJsonObject.getAsJsonObject("musicCarouselShelfRenderer")
                val title = (shelf?.getAsJsonObject("title") ?: carouselShelf?.getAsJsonObject("header")?.getAsJsonObject("musicCarouselShelfBasicHeaderRenderer")?.getAsJsonObject("title"))
                    ?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString?.lowercase() ?: ""
                
                val shelfContents = shelf?.getAsJsonArray("contents") ?: carouselShelf?.getAsJsonArray("contents")
                shelfContents?.forEach { itemEl ->
                    val item = itemEl.asJsonObject
                    val mrlir = item.getAsJsonObject("musicResponsiveListItemRenderer")
                    val mtr = item.getAsJsonObject("musicTwoRowItemRenderer")
                    
                    if (mrlir != null && (title.contains("brani") || title.contains("canzoni") || title.contains("top"))) {
                        val flex = mrlir.getAsJsonArray("flexColumns")
                        val trackTitle = flex?.get(0)?.asJsonObject?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")?.getAsJsonObject("text")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString.orEmpty()
                        val videoId = mrlir.getAsJsonObject("playlistItemData")?.get("videoId")?.asString ?: flex?.get(0)?.asJsonObject?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")?.getAsJsonObject("text")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.getAsJsonObject("navigationEndpoint")?.getAsJsonObject("watchEndpoint")?.get("videoId")?.asString
                        val thumb = mrlir.getAsJsonObject("thumbnail")?.getAsJsonObject("musicThumbnailRenderer")?.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")?.lastOrNull()?.asJsonObject?.get("url")?.asString.orEmpty().toHighResThumbnail()
                        
                        if (!videoId.isNullOrEmpty() && trackTitle.isNotEmpty()) {
                            tracks.add(Track(id = videoId, title = trackTitle, artist = artistName, artistId = browseId, thumbnailUrl = thumb, durationMs = 180000L))
                        }
                    } else if (mtr != null) {
                        // Estrai dati comuni per tutti i musicTwoRowItemRenderer
                        val mtrTitle = mtr.getAsJsonObject("title")?.getAsJsonArray("runs")
                            ?.get(0)?.asJsonObject?.get("text")?.asString.orEmpty()

                        // BrowseEndpoint può stare in "title.runs[0].navigationEndpoint" oppure
                        // direttamente in "navigationEndpoint" del renderer
                        val titleNavBrowse = mtr.getAsJsonObject("title")?.getAsJsonArray("runs")
                            ?.get(0)?.asJsonObject
                            ?.getAsJsonObject("navigationEndpoint")?.getAsJsonObject("browseEndpoint")
                        val rendererNavBrowse = mtr.getAsJsonObject("navigationEndpoint")
                            ?.getAsJsonObject("browseEndpoint")
                        val mtrBrowseEndpoint = titleNavBrowse ?: rendererNavBrowse

                        val mtrBrowseId = mtrBrowseEndpoint?.get("browseId")?.asString
                        val mtrPageType = mtrBrowseEndpoint
                            ?.getAsJsonObject("browseEndpointContextSupportedConfigs")
                            ?.getAsJsonObject("browseEndpointContextMusicConfig")
                            ?.get("pageType")?.asString

                        val mtrThumb = mtr.getAsJsonObject("thumbnailRenderer")
                            ?.getAsJsonObject("musicThumbnailRenderer")
                            ?.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
                            ?.lastOrNull()?.asJsonObject?.get("url")?.asString.orEmpty()

                        // Sezioni di album/brani/top → non sono artisti simili
                        val isKnownNonArtistSection = title.contains("album") || title.contains("singol") ||
                            title.contains("ep") || title.contains("brani") || title.contains("top") ||
                            title.contains("canzoni") || title.contains("video")

                        val isSimilarArtist =
                            // 1) pageType esplicito: il modo più affidabile
                            mtrPageType == "MUSIC_PAGE_TYPE_ARTIST" ||
                            // 2) browseId "UC..." = channel YouTube = sempre un artista,
                            //    purché non siamo in una sezione che sicuramente NON è artisti
                            (mtrBrowseId?.startsWith("UC") == true && !isKnownNonArtistSection) ||
                            // 3) Fallback titolo sezione (nessun pageType, nessun UC...)
                            (mtrPageType == null && (
                                title.contains("simili") || title.contains("fan") ||
                                title.contains("correlat") || title.contains("similar") ||
                                title.contains("ascolt")
                            ))

                        val isAlbumOrSingle = !isSimilarArtist && (
                            mtrPageType == "MUSIC_PAGE_TYPE_ALBUM" ||
                            mtrPageType == "MUSIC_PAGE_TYPE_SINGLE" ||
                            (mtrPageType == null && (
                                title.contains("album") || title.contains("singol") || title.contains("ep")
                            ))
                        )

                        when {
                            isSimilarArtist && mtrBrowseId != null && mtrTitle.isNotEmpty() -> {
                                val similarSubs = mtr.getAsJsonObject("subtitle")
                                    ?.getAsJsonArray("runs")
                                    ?.joinToString("") { it.asJsonObject.get("text")?.asString.orEmpty() }
                                similarArtists.add(
                                    Artist(
                                        id = mtrBrowseId,
                                        name = mtrTitle,
                                        avatarUrl = mtrThumb,
                                        subscribersText = similarSubs,
                                        browseId = mtrBrowseId,
                                        isVerified = false
                                    )
                                )
                            }
                            isAlbumOrSingle && mtrBrowseId != null && mtrTitle.isNotEmpty() -> {
                                val album = Album(
                                    id = mtrBrowseId,
                                    title = mtrTitle,
                                    artist = artistName,
                                    coverUrl = mtrThumb.toHighResThumbnail(),
                                    type = if (title.contains("album")) "Album" else "Singolo",
                                    browseId = mtrBrowseId
                                )
                                if (title.contains("album")) albums.add(album) else singles.add(album)
                            }
                        }
                    }
                }
            }
            
            com.muse.app.domain.model.ArtistDetails(artist = artist, topTracks = tracks, albums = albums, singles = singles, similarArtists = similarArtists)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseYoutubeMusicResponse(rawJson: String, query: String): SearchResult {
        if (rawJson.isBlank()) return SearchResult()

        return try {
            val root = gson.fromJson(rawJson, JsonObject::class.java)
            val contents = root.getAsJsonObject("contents")
                ?.getAsJsonObject("tabbedSearchResultsRenderer")
                ?.getAsJsonArray("tabs")?.get(0)?.asJsonObject
                ?.getAsJsonObject("tabRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents")

            var foundArtist: Artist? = null
            val rawItems = mutableListOf<JsonObject>()

            contents?.forEach { sectionElement ->
                val secObj = sectionElement.asJsonObject

                // 1. Direct musicShelfRenderer o musicCardShelfRenderer
                val directShelf = secObj.getAsJsonObject("musicShelfRenderer")
                    ?: secObj.getAsJsonObject("musicCardShelfRenderer")

                if (directShelf != null) {
                    // Controllo eventuale card artista/risultato principale
                    if (foundArtist == null) {
                        val header = directShelf.getAsJsonObject("header")
                            ?.getAsJsonObject("musicCardShelfHeaderBasicRenderer")
                            
                        val titleObj = header?.getAsJsonObject("title") ?: directShelf.getAsJsonObject("title")
                        val artistName = titleObj?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString

                        if (artistName != null) {
                            val thumbObj = directShelf.getAsJsonObject("thumbnail")
                                ?: header?.getAsJsonObject("thumbnail")
                                
                            val thumb = thumbObj?.getAsJsonObject("musicThumbnailRenderer")
                                ?.getAsJsonObject("thumbnail")
                                ?.getAsJsonArray("thumbnails")?.lastOrNull()?.asJsonObject
                                ?.get("url")?.asString.orEmpty()

                            val subtitleObj = directShelf.getAsJsonObject("subtitle") ?: header?.getAsJsonObject("subtitle")
                            val subtitle = subtitleObj?.getAsJsonArray("runs")?.joinToString("") { it.asJsonObject.get("text")?.asString.orEmpty() }

                            val isArtist = subtitle == null || (!subtitle.contains("Video", ignoreCase = true) && !subtitle.contains("Brano", ignoreCase = true) && !subtitle.contains("Song", ignoreCase = true) && !subtitle.contains("visualizzazioni", ignoreCase = true) && !subtitle.contains(" views", ignoreCase = true))

                            if (isArtist) {
                                val onTapObj = directShelf.getAsJsonObject("onTap") ?: header?.getAsJsonObject("onTap")

                                val browseId = titleObj?.getAsJsonArray("runs")?.get(0)?.asJsonObject
                                    ?.getAsJsonObject("navigationEndpoint")
                                    ?.getAsJsonObject("browseEndpoint")
                                    ?.get("browseId")?.asString
                                    ?: titleObj?.getAsJsonObject("navigationEndpoint")
                                        ?.getAsJsonObject("browseEndpoint")
                                        ?.get("browseId")?.asString
                                    ?: onTapObj?.getAsJsonObject("browseEndpoint")
                                        ?.get("browseId")?.asString
                                    ?: directShelf.getAsJsonArray("buttons")?.firstOrNull()?.asJsonObject
                                        ?.getAsJsonObject("buttonRenderer")
                                        ?.getAsJsonObject("navigationEndpoint")
                                        ?.getAsJsonObject("browseEndpoint")
                                        ?.get("browseId")?.asString

                                foundArtist = Artist(
                                    id = "ytm_${artistName.hashCode()}",
                                    name = artistName,
                                    handle = "@${artistName.replace(" ", "").lowercase()}",
                                    avatarUrl = thumb,
                                    subscribersText = subtitle ?: "Artista Ufficiale",
                                    videoCountText = "Brani & Album",
                                    isVerified = true,
                                    browseId = browseId
                                )
                            }
                        }
                    }

                    directShelf.getAsJsonArray("contents")?.forEach { itemEl ->
                        itemEl.asJsonObject.getAsJsonObject("musicResponsiveListItemRenderer")?.let {
                            rawItems.add(it)
                        }
                    }
                }

                // 2. Nested itemSectionRenderer
                val itemSec = secObj.getAsJsonObject("itemSectionRenderer")
                itemSec?.getAsJsonArray("contents")?.forEach { itemEl ->
                    itemEl.asJsonObject.getAsJsonObject("musicResponsiveListItemRenderer")?.let {
                        rawItems.add(it)
                    }
                }
            }

            val tracks = mutableListOf<Track>()
            val parsedAlbums = mutableListOf<Album>()
            val parsedPlaylists = mutableListOf<com.muse.app.domain.model.Playlist>()

            for (flexItem in rawItems) {
                val flexColumns = flexItem.getAsJsonArray("flexColumns") ?: continue
                if (flexColumns.size() == 0) continue

                // Titolo e navigazione
                val col0Runs = flexColumns.get(0).asJsonObject
                    .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.getAsJsonObject("text")?.getAsJsonArray("runs")

                val title = col0Runs?.joinToString("") { it.asJsonObject.get("text")?.asString.orEmpty() }?.trim().orEmpty()

                // Ricerca Video ID da molteplici possibili percorsi Innertube
                var videoId: String? = flexItem.getAsJsonObject("playlistItemData")?.get("videoId")?.asString

                if (videoId.isNullOrEmpty() && col0Runs != null && col0Runs.size() > 0) {
                    videoId = col0Runs.get(0).asJsonObject
                        .getAsJsonObject("navigationEndpoint")
                        ?.getAsJsonObject("watchEndpoint")
                        ?.get("videoId")?.asString
                }

                if (videoId.isNullOrEmpty()) {
                    videoId = flexItem.getAsJsonObject("overlay")
                        ?.getAsJsonObject("musicItemThumbnailOverlayRenderer")
                        ?.getAsJsonObject("content")
                        ?.getAsJsonObject("musicPlayButtonRenderer")
                        ?.getAsJsonObject("playNavigationEndpoint")
                        ?.getAsJsonObject("watchEndpoint")
                        ?.get("videoId")?.asString
                }

                // Colonna 1: Autore, Durata, Visualizzazioni/Riproduzioni
                val subtitleRuns = if (flexColumns.size() > 1) {
                    flexColumns.get(1).asJsonObject
                        .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.getAsJsonObject("text")?.getAsJsonArray("runs")
                } else null

                val artistNames = mutableListOf<String>()
                var artistBrowseId: String? = null
                var albumName: String? = null
                var albumBrowseId: String? = null
                var durationText = "3:30"
                var viewsText = "YouTube Music"

                subtitleRuns?.forEach { run ->
                    val text = run.asJsonObject.get("text")?.asString.orEmpty().trim()
                    val navEndpoint = run.asJsonObject.getAsJsonObject("navigationEndpoint")
                    val browseEndpointId = navEndpoint?.getAsJsonObject("browseEndpoint")?.get("browseId")?.asString
                    val pageType = navEndpoint?.getAsJsonObject("browseEndpoint")
                        ?.getAsJsonObject("browseEndpointContextSupportedConfigs")
                        ?.getAsJsonObject("browseEndpointContextMusicConfig")
                        ?.get("pageType")?.asString
                    if (text.isNotEmpty() && text != "•" && text != "·" && text != "|") {
                        if (text.contains(":") && text.length <= 6) {
                            durationText = text
                        } else if (text.contains("visualizzazioni", ignoreCase = true) ||
                            text.contains("riproduzioni", ignoreCase = true) ||
                            text.contains("views", ignoreCase = true) ||
                            text.contains("plays", ignoreCase = true)
                        ) {
                            viewsText = text
                        } else if (pageType == "MUSIC_PAGE_TYPE_ALBUM" || pageType == "MUSIC_PAGE_TYPE_PLAYLIST") {
                            // This run is an album/EP reference
                            albumName = text
                            albumBrowseId = browseEndpointId
                        } else if (!text.equals("brano", ignoreCase = true) &&
                            !text.equals("video", ignoreCase = true) &&
                            !text.equals("album", ignoreCase = true) &&
                            !text.equals("singolo", ignoreCase = true) &&
                            !text.equals("ep", ignoreCase = true) &&
                            !text.equals("e", ignoreCase = true) &&
                            !text.equals("&", ignoreCase = true)
                        ) {
                            artistNames.add(text)
                            if (browseEndpointId != null && artistBrowseId == null) {
                                artistBrowseId = browseEndpointId
                            }
                        }
                    }
                }

                // Thumbnail
                val thumb = flexItem.getAsJsonObject("thumbnail")
                    ?.getAsJsonObject("musicThumbnailRenderer")
                    ?.getAsJsonObject("thumbnail")
                    ?.getAsJsonArray("thumbnails")?.lastOrNull()?.asJsonObject
                    ?.get("url")?.asString.orEmpty().toHighResThumbnail()

                val mainNavEndpoint = col0Runs?.get(0)?.asJsonObject?.getAsJsonObject("navigationEndpoint")
                val mainPageType = mainNavEndpoint?.getAsJsonObject("browseEndpoint")
                    ?.getAsJsonObject("browseEndpointContextSupportedConfigs")
                    ?.getAsJsonObject("browseEndpointContextMusicConfig")
                    ?.get("pageType")?.asString

                val mainBrowseId = mainNavEndpoint?.getAsJsonObject("browseEndpoint")?.get("browseId")?.asString

                val isAlbum = mainPageType == "MUSIC_PAGE_TYPE_ALBUM" || mainPageType == "MUSIC_PAGE_TYPE_SINGLE" || (mainBrowseId?.startsWith("MPREb_") == true)

                if (isAlbum) {
                    val finalArtist = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else query
                    parsedAlbums.add(
                        Album(
                            id = mainBrowseId ?: videoId ?: "",
                            title = title,
                            artist = finalArtist,
                            coverUrl = thumb,
                            type = if (mainPageType == "MUSIC_PAGE_TYPE_ALBUM") "Album" else "Singolo",
                            browseId = mainBrowseId
                        )
                    )
                } else if (mainPageType == "MUSIC_PAGE_TYPE_PLAYLIST") {
                    parsedPlaylists.add(
                        com.muse.app.domain.model.Playlist(
                            id = mainBrowseId ?: videoId ?: "",
                            name = title,
                            coverUrl = thumb,
                            trackCount = 0
                        )
                    )
                } else if (!videoId.isNullOrEmpty() && title.isNotEmpty()) {
                    val durationParts = durationText.split(":")
                    val durationMs = if (durationParts.size == 2) {
                        (durationParts[0].toLongOrNull() ?: 3) * 60_000 + (durationParts[1].toLongOrNull() ?: 30) * 1_000
                    } else 210_000L

                    val finalArtist = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else query

                    tracks.add(
                        Track(
                            id = videoId,
                            title = title,
                            artist = finalArtist,
                            artistId = artistBrowseId,
                            thumbnailUrl = thumb,
                            durationMs = durationMs,
                            viewsText = viewsText,
                            album = albumName,
                            albumBrowseId = albumBrowseId
                        )
                    )
                }
            }

            // Se l'artista principale è stato trovato ma senza browseId, tentiamo di recuperarlo dai brani
            if (foundArtist != null && foundArtist.browseId == null) {
                val fallbackBrowseId = tracks.firstNotNullOfOrNull { it.artistId }
                if (fallbackBrowseId != null) {
                    foundArtist = foundArtist.copy(browseId = fallbackBrowseId)
                }
            }

            // Se l'artista principale non è ancora stato valorizzato, impostalo dal primo brano
            if (foundArtist == null && tracks.isNotEmpty()) {
                val topTrack = tracks.first()
                foundArtist = Artist(
                    id = "ytm_artist_${topTrack.artist.hashCode()}",
                    name = topTrack.artist,
                    handle = "@${topTrack.artist.replace(" ", "").lowercase()}",
                    avatarUrl = topTrack.thumbnailUrl,
                    subscribersText = "Artista Ufficiale",
                    videoCountText = "${tracks.size} brani",
                    isVerified = true,
                    browseId = topTrack.artistId
                )
            }

            val popular = if (tracks.size > 5) tracks.take(5) else tracks

            SearchResult(
                artist = foundArtist,
                popularTracks = popular,
                allTracks = tracks,
                albums = parsedAlbums,
                playlists = parsedPlaylists
            )
        } catch (e: Exception) {
            e.printStackTrace()
            SearchResult()
        }
    }

    /**
     * Recupera le nuove uscite musicali dalla home di YouTube Music tramite Innertube API browse.
     * Usa FEmusic_new_releases (pagina /new_releases) come primario,
     * con fallback a FEmusic_new_releases_albums_chart.
     */
    suspend fun fetchNewReleases(): List<com.muse.app.domain.model.Album> = withContext(Dispatchers.IO) {
        try {
            // Tenta prima con la pagina principale nuove uscite
            val primaryPayload = "\"browseId\": \"FEmusic_new_releases\""
            val primaryRequest = buildInnertubeRequest("browse", primaryPayload)
            val primaryResponse = client.newCall(primaryRequest).execute()
            val primaryJson = primaryResponse.body?.string().orEmpty()

            val primaryResult = if (primaryJson.isNotBlank()) parseNewReleasesResponse(primaryJson) else emptyList()
            if (primaryResult.isNotEmpty()) return@withContext primaryResult

            // Fallback: chart album
            val payload = "\"browseId\": \"FEmusic_new_releases_albums_chart\""
            val request = buildInnertubeRequest("browse", payload)

            val response = client.newCall(request).execute()
            val rawJson = response.body?.string().orEmpty()

            val result = parseNewReleasesResponse(rawJson)
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseNewReleasesResponse(rawJson: String): List<com.muse.app.domain.model.Album> {
        val albums = mutableListOf<com.muse.app.domain.model.Album>()
        try {
            val root = gson.fromJson(rawJson, JsonObject::class.java) ?: return albums

            // Cerca ricorsivamente tutti i nodi musicTwoRowItemRenderer nell'intera risposta
            findMusicTwoRowItemRenderers(root, albums)

        } catch (e: Exception) {
            // Ignorato
        }
        return albums
    }

    /**
     * Naviga ricorsivamente il JSON alla ricerca di nodi musicTwoRowItemRenderer
     * che contengono album/singoli. Questo approccio è indipendente dalla struttura esatta
     * della risposta API che può variare.
     */
    private fun findMusicTwoRowItemRenderers(
        element: com.google.gson.JsonElement?,
        albums: MutableList<com.muse.app.domain.model.Album>,
        depth: Int = 0
    ) {
        if (element == null || depth > 15) return

        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                // Trovato un renderer album!
                if (obj.has("musicTwoRowItemRenderer")) {
                    extractAlbumFromRenderer(obj.getAsJsonObject("musicTwoRowItemRenderer"))
                        ?.let { albums.add(it) }
                    return // non scendere ulteriormente da qui
                }
                // Continua a cercare nei figli
                for ((_, value) in obj.entrySet()) {
                    findMusicTwoRowItemRenderers(value, albums, depth + 1)
                }
            }
            element.isJsonArray -> {
                for (item in element.asJsonArray) {
                    findMusicTwoRowItemRenderers(item, albums, depth + 1)
                }
            }
        }
    }

    private fun extractAlbumFromRenderer(renderer: JsonObject): com.muse.app.domain.model.Album? {
        return try {
            // Titolo
            val title = renderer
                .getAsJsonObject("title")
                ?.getAsJsonArray("runs")
                ?.get(0)?.asJsonObject
                ?.get("text")?.asString ?: return null

            if (title.isBlank()) return null

            // Sottotitolo: artista e anno
            val subtitleRuns = renderer
                .getAsJsonObject("subtitle")
                ?.getAsJsonArray("runs")

            // I runs alternano: testo | separatore | testo
            // tipicamente: "Artista" · "2026" · "Album"
            val artist = subtitleRuns?.firstOrNull()?.asJsonObject?.get("text")?.asString ?: ""
            val year = subtitleRuns?.lastOrNull()?.asJsonObject?.get("text")?.asString ?: ""

            // Copertina — priorità all'ultima thumbnail (più alta risoluzione)
            val thumbnails = renderer
                .getAsJsonObject("thumbnailRenderer")
                ?.getAsJsonObject("musicThumbnailRenderer")
                ?.getAsJsonObject("thumbnail")
                ?.getAsJsonArray("thumbnails")
            val coverUrl = (thumbnails?.lastOrNull()?.asJsonObject?.get("url")?.asString ?: "")
                .toHighResThumbnail()

            // browseId per aprire l'album
            val browseId = renderer
                .getAsJsonObject("navigationEndpoint")
                ?.getAsJsonObject("browseEndpoint")
                ?.get("browseId")?.asString ?: ""

            // videoId per la riproduzione diretta
            val videoId = renderer
                .getAsJsonObject("overlay")
                ?.getAsJsonObject("musicItemThumbnailOverlayRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("musicPlayButtonRenderer")
                ?.getAsJsonObject("playNavigationEndpoint")
                ?.getAsJsonObject("watchEndpoint")
                ?.get("videoId")?.asString
                ?: browseId

            // Tipo: Album / EP / Singolo
            val type = when {
                subtitleRuns?.any { it.asJsonObject.get("text")?.asString == "EP" } == true -> "EP"
                subtitleRuns?.any { it.asJsonObject.get("text")?.asString?.contains("Singolo", true) == true } == true -> "Singolo"
                else -> "Album"
            }

            com.muse.app.domain.model.Album(
                id = videoId.ifEmpty { browseId },
                title = title,
                artist = artist,
                coverUrl = coverUrl,
                year = year,
                type = type,
                browseId = browseId
            )
        } catch (e: Exception) {
            android.util.Log.w("YoutubeMusicService", "extractAlbum error: ${e.message}")
            null
        }
    }

    /**
     * Recupera la sezione "Scelte rapide" direttamente dalla home feed di YouTube Music
     * (FEmusic_home), esattamente come fa l'app ufficiale.
     * 
     * La home restituisce una serie di shelf; cerchiamo quello con titolo contenente
     * "Quick picks" / "Scelte rapide" / "mix" oppure prendiamo il primo shelf
     * di tipo musicShelfRenderer che contiene videoId riproducibili.
     */
    suspend fun getHomeQuickPicks(): List<Track> = withContext(Dispatchers.IO) {
        try {
            val payload = "\"browseId\": \"FEmusic_home\""
            val request = buildInnertubeRequest("browse", payload)
            val response = client.newCall(request).execute()
            val rawJson = response.body?.string().orEmpty()
            if (rawJson.isBlank()) return@withContext emptyList()

            val root = gson.fromJson(rawJson, JsonObject::class.java)

            // La home usa sectionListRenderer dentro twoColumnBrowseResultsRenderer o singleColumn
            val sections: com.google.gson.JsonArray? =
                root.getAsJsonObject("contents")
                    ?.getAsJsonObject("singleColumnBrowseResultsRenderer")
                    ?.getAsJsonArray("tabs")?.get(0)?.asJsonObject
                    ?.getAsJsonObject("tabRenderer")?.getAsJsonObject("content")
                    ?.getAsJsonObject("sectionListRenderer")?.getAsJsonArray("contents")
                    ?: root.getAsJsonObject("contents")
                        ?.getAsJsonObject("twoColumnBrowseResultsRenderer")
                        ?.getAsJsonArray("tabs")?.get(0)?.asJsonObject
                        ?.getAsJsonObject("tabRenderer")?.getAsJsonObject("content")
                        ?.getAsJsonObject("sectionListRenderer")?.getAsJsonArray("contents")

            val quickPickKeywords = listOf(
                "scelte rapide", "quick picks", "quick pick",
                "mixed for you", "mix", "recommended", "consigliati"
            )

            val tracks = mutableListOf<Track>()

            // Prima passata: cerca il shelf "Scelte rapide" per nome
            sections?.forEach { secEl ->
                if (tracks.size >= 20) return@forEach
                val secObj = secEl.asJsonObject
                val shelf = secObj.getAsJsonObject("musicShelfRenderer") ?: return@forEach

                val shelfTitle = shelf.getAsJsonObject("title")
                    ?.getAsJsonArray("runs")
                    ?.joinToString("") { it.asJsonObject["text"]?.asString.orEmpty() }
                    ?.lowercase().orEmpty()

                val isQuickPicks = quickPickKeywords.any { shelfTitle.contains(it) }
                if (!isQuickPicks && tracks.isNotEmpty()) return@forEach  // skip non-quickpicks shelf once we have results

                shelf.getAsJsonArray("contents")?.forEach { itemEl ->
                    val rend = itemEl.asJsonObject.getAsJsonObject("musicResponsiveListItemRenderer")
                        ?: return@forEach

                    val flexCols = rend.getAsJsonArray("flexColumns") ?: return@forEach
                    if (flexCols.size() == 0) return@forEach

                    val col0Runs = flexCols[0].asJsonObject
                        .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.getAsJsonObject("text")?.getAsJsonArray("runs")

                    val title = col0Runs?.joinToString("") { it.asJsonObject["text"]?.asString.orEmpty() }
                        ?.trim().orEmpty()
                    if (title.isBlank()) return@forEach

                    // videoId da playlistItemData o watchEndpoint
                    val videoId = rend.getAsJsonObject("playlistItemData")?.get("videoId")?.asString
                        ?: col0Runs?.get(0)?.asJsonObject
                            ?.getAsJsonObject("navigationEndpoint")
                            ?.getAsJsonObject("watchEndpoint")
                            ?.get("videoId")?.asString
                        ?: rend.getAsJsonObject("overlay")
                            ?.getAsJsonObject("musicItemThumbnailOverlayRenderer")
                            ?.getAsJsonObject("content")
                            ?.getAsJsonObject("musicPlayButtonRenderer")
                            ?.getAsJsonObject("playNavigationEndpoint")
                            ?.getAsJsonObject("watchEndpoint")
                            ?.get("videoId")?.asString
                    if (videoId.isNullOrEmpty()) return@forEach

                    // Artista dalla colonna 1
                    val subtitleRuns = if (flexCols.size() > 1) {
                        flexCols[1].asJsonObject
                            .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.getAsJsonObject("text")?.getAsJsonArray("runs")
                    } else null

                    val artistNames = mutableListOf<String>()
                    var artistBrowseId: String? = null
                    var albumName: String? = null
                    var viewsText: String? = null

                    subtitleRuns?.forEach { run ->
                        val text = run.asJsonObject["text"]?.asString.orEmpty().trim()
                        val navEp = run.asJsonObject.getAsJsonObject("navigationEndpoint")
                        val pageType = navEp?.getAsJsonObject("browseEndpoint")
                            ?.getAsJsonObject("browseEndpointContextSupportedConfigs")
                            ?.getAsJsonObject("browseEndpointContextMusicConfig")
                            ?.get("pageType")?.asString
                        val bId = navEp?.getAsJsonObject("browseEndpoint")?.get("browseId")?.asString

                        when {
                            text.isEmpty() || text == "•" || text == "·" -> {}
                            text.contains("visualizzazioni", ignoreCase = true) ||
                            text.contains("views", ignoreCase = true) ||
                            text.contains("plays", ignoreCase = true) -> viewsText = text
                            pageType == "MUSIC_PAGE_TYPE_ALBUM" ||
                            pageType == "MUSIC_PAGE_TYPE_PLAYLIST" -> { albumName = text }
                            !text.equals("Brano", ignoreCase = true) &&
                            !text.equals("Song", ignoreCase = true) &&
                            !text.equals("Video", ignoreCase = true) -> {
                                artistNames.add(text)
                                if (bId != null && artistBrowseId == null) artistBrowseId = bId
                            }
                        }
                    }

                    // Thumbnail
                    val thumb = rend.getAsJsonObject("thumbnail")
                        ?.getAsJsonObject("musicThumbnailRenderer")
                        ?.getAsJsonObject("thumbnail")
                        ?.getAsJsonArray("thumbnails")?.lastOrNull()?.asJsonObject
                        ?.get("url")?.asString.orEmpty().toHighResThumbnail()

                    tracks.add(
                        Track(
                            id = videoId,
                            title = title,
                            artist = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else "YouTube Music",
                            artistId = artistBrowseId,
                            thumbnailUrl = thumb,
                            durationMs = 210_000L,
                            viewsText = viewsText,
                            album = albumName
                        )
                    )
                }
            }

            tracks.take(20)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getUpNext(videoId: String, params: String? = null): UpNextResult = withContext(Dispatchers.IO) {
        android.util.Log.d("MuseChips", "getUpNext CHIAMATO per videoId=$videoId, params=$params")
        try {
            val payloadObj = JsonObject()
            payloadObj.addProperty("videoId", videoId)
            // playlistId RDAMVM{videoId} attiva il chip cloud di YouTube Music
            payloadObj.addProperty("playlistId", "RDAMVM$videoId")
            if (params != null) {
                payloadObj.addProperty("params", params)
            }
            val payload = payloadObj.toString()
            // Rimuoviamo le parentesi graffe più esterne perché buildInnertubeRequest le aggiunge già
            val innerPayload = payload.substring(1, payload.length - 1)
            
            val request = buildInnertubeRequest("next", innerPayload)
            val response = client.newCall(request).execute()
            val rawJson = response.body?.string().orEmpty()
            android.util.Log.d("MuseChips", "Risposta API: ${rawJson.length} chars")
            if (rawJson.isBlank()) return@withContext UpNextResult()

            val root = gson.fromJson(rawJson, JsonObject::class.java)

            val contentsObj = root.getAsJsonObject("contents")

            val musicQueueRenderer = contentsObj
                ?.getAsJsonObject("singleColumnMusicWatchNextResultsRenderer")
                ?.getAsJsonObject("tabbedRenderer")
                ?.getAsJsonObject("watchNextTabbedResultsRenderer")
                ?.getAsJsonArray("tabs")?.get(0)?.asJsonObject
                ?.getAsJsonObject("tabRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("musicQueueRenderer")

            android.util.Log.d("MuseChips", "musicQueueRenderer trovato: ${musicQueueRenderer != null}")
            if (musicQueueRenderer != null) {
                android.util.Log.d("MuseChips", "musicQueueRenderer keys: ${musicQueueRenderer.keySet()}")
                // Log header content per trovare i chip
                val headerContent = musicQueueRenderer.get("header")
                android.util.Log.d("MuseChips", "header content (primi 500 chars): ${headerContent.toString().take(500)}")
            }

            if (musicQueueRenderer == null) return@withContext UpNextResult()


            val tracks = mutableListOf<Track>()
            val chips = mutableListOf<Chip>()

            // Estrai i brani
            val panelContents = musicQueueRenderer
                .getAsJsonObject("content")
                ?.getAsJsonObject("playlistPanelRenderer")
                ?.getAsJsonArray("contents")

            panelContents?.forEach { item ->
                val renderer = item.asJsonObject.getAsJsonObject("playlistPanelVideoRenderer") ?: return@forEach
                val vId = renderer.get("videoId")?.asString ?: return@forEach

                val title = renderer.getAsJsonObject("title")
                    ?.getAsJsonArray("runs")
                    ?.get(0)?.asJsonObject
                    ?.get("text")?.asString ?: ""

                val longBylineTextRuns = renderer.getAsJsonObject("longBylineText")?.getAsJsonArray("runs")
                val artistNames = mutableListOf<String>()
                var albumName: String? = null
                var artistId: String? = null

                longBylineTextRuns?.forEach { run ->
                    val text = run.asJsonObject.get("text")?.asString.orEmpty().trim()
                    val navEp = run.asJsonObject.getAsJsonObject("navigationEndpoint")
                    val bId = navEp?.getAsJsonObject("browseEndpoint")?.get("browseId")?.asString
                    val pageType = navEp?.getAsJsonObject("browseEndpoint")
                        ?.getAsJsonObject("browseEndpointContextSupportedConfigs")
                        ?.getAsJsonObject("browseEndpointContextMusicConfig")
                        ?.get("pageType")?.asString

                    if (text.isNotEmpty() && text != "•" && text != "·") {
                        if (pageType == "MUSIC_PAGE_TYPE_ALBUM") {
                            albumName = text
                        } else if (pageType == "MUSIC_PAGE_TYPE_ARTIST" || pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" || (pageType == null && !text.contains("visualizzazioni"))) {
                            artistNames.add(text)
                            if (artistId == null && bId != null) {
                                artistId = bId
                            }
                        }
                    }
                }

                val durationText = renderer.getAsJsonObject("lengthText")
                    ?.getAsJsonArray("runs")
                    ?.get(0)?.asJsonObject
                    ?.get("text")?.asString ?: "3:00"

                val durationParts = durationText.split(":")
                val durationMs = if (durationParts.size == 2) {
                    (durationParts[0].toLongOrNull() ?: 3) * 60_000 + (durationParts[1].toLongOrNull() ?: 0) * 1_000
                } else if (durationParts.size == 3) {
                    (durationParts[0].toLongOrNull() ?: 0) * 3600_000 + (durationParts[1].toLongOrNull() ?: 3) * 60_000 + (durationParts[2].toLongOrNull() ?: 0) * 1_000
                } else {
                    180_000L
                }

                val thumb = renderer.getAsJsonObject("thumbnail")
                    ?.getAsJsonArray("thumbnails")
                    ?.lastOrNull()?.asJsonObject
                    ?.get("url")?.asString.orEmpty().toHighResThumbnail()

                if (title.isNotEmpty()) {
                    tracks.add(
                        Track(
                            id = vId,
                            title = title,
                            artist = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else "Unknown",
                            artistId = artistId,
                            thumbnailUrl = thumb,
                            durationMs = durationMs,
                            album = albumName
                        )
                    )
                }
            }

            // Estrai i chip — prova più percorsi JSON perché la struttura può variare
            var chipCloudArray = musicQueueRenderer
                .getAsJsonObject("subHeaderChipCloud")
                ?.getAsJsonObject("chipCloudRenderer")
                ?.getAsJsonArray("chips")

            // Percorso alternativo: header -> chipCloudRenderer
            if (chipCloudArray == null) {
                chipCloudArray = musicQueueRenderer
                    .getAsJsonObject("header")
                    ?.getAsJsonObject("chipCloudRenderer")
                    ?.getAsJsonArray("chips")
            }

            // Percorso alternativo: cerca ricorsivamente chipCloudRenderer
            if (chipCloudArray == null) {
                chipCloudArray = findChipCloudRecursive(musicQueueRenderer)
            }

            android.util.Log.d("MuseChips", "musicQueueRenderer keys: ${musicQueueRenderer.keySet()}")
            android.util.Log.d("MuseChips", "chipCloud trovato: ${chipCloudArray != null}, size: ${chipCloudArray?.size()}")

            chipCloudArray?.forEach { chipObj ->
                val chipRenderer = chipObj.asJsonObject.getAsJsonObject("chipCloudChipRenderer")
                if (chipRenderer != null) {
                    val text = chipRenderer.getAsJsonObject("text")
                        ?.getAsJsonArray("runs")
                        ?.get(0)?.asJsonObject
                        ?.get("text")?.asString ?: ""

                    val isSelected = chipRenderer.get("isSelected")?.asBoolean ?: false

                    val navEndpoint = chipRenderer.getAsJsonObject("navigationEndpoint")
                    val endpointParams = navEndpoint
                        ?.getAsJsonObject("watchEndpoint")
                        ?.get("params")?.asString
                        ?: navEndpoint
                            ?.getAsJsonObject("queueUpdateCommand")
                            ?.getAsJsonObject("fetchParams")
                            ?.get("queueUpdateParams")?.asString
                        ?: navEndpoint?.get("params")?.asString

                    android.util.Log.d("MuseChips", "Chip: '$text', selected: $isSelected, params: $endpointParams")

                    if (text.isNotEmpty()) {
                        chips.add(Chip(title = text, endpointParams = endpointParams, isSelected = isSelected))
                    }
                }
            }

            UpNextResult(tracks = tracks, chips = chips)
        } catch (e: Exception) {
            e.printStackTrace()
            UpNextResult()
        }
    }

    /**
     * Cerca ricorsivamente un array "chips" dentro un chipCloudRenderer nel JSON.
     */
    private fun findChipCloudRecursive(element: com.google.gson.JsonElement?, depth: Int = 0): com.google.gson.JsonArray? {
        if (element == null || depth > 10) return null
        return when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                if (obj.has("chipCloudRenderer")) {
                    val chips = obj.getAsJsonObject("chipCloudRenderer")?.getAsJsonArray("chips")
                    if (chips != null && chips.size() > 0) return chips
                }
                for ((_, value) in obj.entrySet()) {
                    val result = findChipCloudRecursive(value, depth + 1)
                    if (result != null) return result
                }
                null
            }
            element.isJsonArray -> {
                for (item in element.asJsonArray) {
                    val result = findChipCloudRecursive(item, depth + 1)
                    if (result != null) return result
                }
                null
            }
            else -> null
        }
    }
}
