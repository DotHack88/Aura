package com.muse.app.data.repository

import com.muse.app.BuildConfig
import com.muse.app.data.local.*
import com.muse.app.data.local.download.OfflineManager
import com.muse.app.data.local.download.DownloadProgress
import com.muse.app.data.remote.FirestoreSyncService
import com.muse.app.data.remote.YoutubeApiService
import com.muse.app.data.remote.YoutubeMusicService
import com.muse.app.domain.model.Album
import com.muse.app.domain.model.Artist
import com.muse.app.domain.model.Playlist
import com.muse.app.domain.model.SearchResult
import com.muse.app.domain.model.Track
import com.muse.app.player.NewPipeStreamExtractor
import com.muse.app.utils.toHighResThumbnail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.util.UUID

class MusicRepository(
    private val youtubeApi: YoutubeApiService,
    private val youtubeMusicService: YoutubeMusicService = YoutubeMusicService(),
    private val trackDao: TrackDao,
    private val historyDao: HistoryDao,
    private val playlistDao: PlaylistDao,
    private val followedArtistDao: FollowedArtistDao,
    private val syncService: FirestoreSyncService,
    val offlineManager: OfflineManager? = null
) {

    // ── Artisti seguiti ──────────────────────────────────────────────────────────

    suspend fun followArtist(artist: Artist) {
        followedArtistDao.followArtist(
            FollowedArtistEntity(
                id = artist.id,
                name = artist.name,
                handle = artist.handle,
                avatarUrl = artist.avatarUrl,
                browseId = artist.browseId
            )
        )
    }

    suspend fun unfollowArtist(artistId: String) {
        followedArtistDao.unfollowArtist(artistId)
    }

    suspend fun isFollowingArtist(artistId: String): Boolean {
        return followedArtistDao.isFollowing(artistId) > 0
    }

    fun getFollowedArtists(): Flow<List<Artist>> {
        return followedArtistDao.getFollowedArtists().map { list ->
            list.map { e ->
                Artist(
                    id = e.id,
                    name = e.name,
                    handle = e.handle,
                    avatarUrl = e.avatarUrl,
                    browseId = e.browseId
                )
            }
        }
    }

    // ── Dettagli artista ─────────────────────────────────────────────────────────

    suspend fun getArtistDetails(browseId: String): com.muse.app.domain.model.ArtistDetails? {
        return youtubeMusicService.getArtistDetails(browseId)
    }

    suspend fun getAlbumTracks(browseId: String, albumTitle: String, artistName: String, coverUrl: String): List<Track> {
        return youtubeMusicService.getAlbumTracks(browseId, albumTitle, artistName, coverUrl)
    }

    suspend fun searchAlbums(query: String): List<Album> {
        return youtubeMusicService.searchAlbums(query)
    }

    suspend fun searchAll(query: String): SearchResult {
        // ── 1. Fonte primaria assoluta: YouTube Music (https://music.youtube.com / Innertube API) ──
        val musicResult = youtubeMusicService.searchMusic(query)
        if (musicResult.allTracks.isNotEmpty()) {
            return musicResult
        }

        // ── 2. Fallback: NewPipe SearchExtractor (nessuna API key) ──
        val newPipeTracks = NewPipeStreamExtractor.searchTracks(query)
        if (newPipeTracks.isNotEmpty()) {
        val tracks = newPipeTracks.map { item ->
                val trackId = item["id"] as String
                val rawThumb = item["thumbnailUrl"] as String
                val thumb = if (rawThumb.isNotBlank()) rawThumb
                            else "https://i.ytimg.com/vi/$trackId/hqdefault.jpg"
                Track(
                    id           = trackId,
                    title        = item["title"] as String,
                    artist       = item["artist"] as String,
                    thumbnailUrl = thumb,
                    durationMs   = item["durationMs"] as Long,
                    viewsText    = item["viewsText"] as? String
                )
            }
            val popular = if (tracks.size > 5) tracks.take(5) else tracks
            val topArtist = tracks.firstOrNull()?.let { t ->
                Artist(
                    id               = "np_artist_${t.artist.hashCode()}",
                    name             = t.artist,
                    handle           = "@${t.artist.replace(" ", "").lowercase()}",
                    avatarUrl        = t.thumbnailUrl,
                    subscribersText  = null,
                    videoCountText   = "${tracks.size} risultati",
                    isVerified       = false
                )
            }
            return SearchResult(artist = topArtist, popularTracks = popular, allTracks = tracks)
        }


        return try {
            // ── 3. Ultimo fallback: YouTube Data API ──
            val channelResponse = try {
                youtubeApi.searchChannels(
                    query = query,
                    apiKey = BuildConfig.YOUTUBE_API_KEY
                )
            } catch (e: Exception) {
                null
            }

            val channelItem = channelResponse?.items?.firstOrNull { it.id?.channelId != null }
            val channelId = channelItem?.id?.channelId

            val artist: Artist? = if (channelId != null) {
                try {
                    val channelDetails = youtubeApi.getChannelDetails(
                        channelIds = channelId,
                        apiKey = BuildConfig.YOUTUBE_API_KEY
                    )
                    val detail = channelDetails.items?.firstOrNull()
                    val snippet = detail?.snippet ?: channelItem.snippet
                    val stats = detail?.statistics
                    val branding = detail?.brandingSettings

                    val subCount = stats?.subscriberCount?.toLongOrNull()
                    val subText = when {
                        subCount != null && subCount >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f Mln iscritti", subCount / 1_000_000.0)
                        subCount != null && subCount >= 1_000 -> "${subCount / 1_000}K iscritti"
                        subCount != null -> "$subCount iscritti"
                        else -> null
                    }

                    val videoCount = stats?.videoCount?.toLongOrNull()
                    val videoText = when {
                        videoCount != null && videoCount >= 1_000 -> String.format(java.util.Locale.US, "%.1fK video", videoCount / 1000.0)
                        videoCount != null -> "$videoCount video"
                        else -> null
                    }

                    val avatar = snippet?.thumbnails?.high?.url
                        ?: snippet?.thumbnails?.medium?.url
                        ?: snippet?.thumbnails?.default?.url.orEmpty()

                    val banner = branding?.image?.bannerExternalUrl

                    Artist(
                        id = channelId,
                        name = snippet?.title.orEmpty(),
                        handle = snippet?.customUrl,
                        avatarUrl = avatar,
                        bannerUrl = banner,
                        subscribersText = subText,
                        videoCountText = videoText
                    )
                } catch (e: Exception) {
                    null
                }
            } else null

            val trackList = searchTracks(query)
            val popular = if (trackList.size > 5) trackList.take(5) else trackList

            SearchResult(
                artist = artist,
                popularTracks = popular,
                allTracks = trackList
            )
        } catch (e: Exception) {
            SearchResult(allTracks = emptyList())
        }
    }

    suspend fun getRelatedTracks(videoId: String): List<Track> {
        val newPipeTracks = NewPipeStreamExtractor.getRelatedTracks(videoId)
        return newPipeTracks.map { item ->
            val trackId = item["id"] as String
            val rawThumb = item["thumbnailUrl"] as String
            Track(
                id           = trackId,
                title        = item["title"] as String,
                artist       = item["artist"] as String,
                thumbnailUrl = if (rawThumb.isNotBlank()) rawThumb
                               else "https://i.ytimg.com/vi/$trackId/hqdefault.jpg",
                durationMs   = item["durationMs"] as Long,
                album        = null
            )
        }
    }

    /**
     * Aggiusta il punteggio locale di un brano modificando il suo playCount nella history.
     * Usato dal Muse Radio Engine per registrare segnali di ascolto (skip, completamento, ecc.).
     * Il playCount viene usato come proxy di gradimento per il ranking dei candidati radio.
     *
     * @param trackId ID del brano da aggiornare
     * @param delta   Variazione positiva o negativa del punteggio
     */
    suspend fun adjustTrackScore(trackId: String, delta: Int) {
        val existing = historyDao.getHistoryEntry(trackId) ?: return
        val newCount = (existing.playCount + delta).coerceAtLeast(0)
        historyDao.upsertHistory(existing.copy(playCount = newCount))
    }

    /**
     * Recupera il playCount locale di un brano (usato per il ranking dei candidati radio).
     * Ritorna 0 se il brano non è ancora nella history.
     */
    suspend fun getTrackScore(trackId: String): Int {
        return historyDao.getHistoryEntry(trackId)?.playCount ?: 0
    }


    suspend fun searchTracks(query: String): List<Track> {
        val ytmResult = youtubeMusicService.searchMusic(query)
        if (ytmResult.allTracks.isNotEmpty()) {
            return ytmResult.allTracks
        }
        return try {
            val response = youtubeApi.searchVideos(
                query = query,
                apiKey = BuildConfig.YOUTUBE_API_KEY
            )
            val items = response.items.orEmpty().filter { it.id?.videoId != null }
            val videoIds = items.mapNotNull { it.id?.videoId }.joinToString(",")

            // Dettagli durata e statistiche visualizzazioni
            val detailsMap = try {
                if (videoIds.isNotEmpty()) {
                    val details = youtubeApi.getVideoDetails(
                        videoIds = videoIds,
                        apiKey = BuildConfig.YOUTUBE_API_KEY
                    )
                    details.items.orEmpty().associate { item ->
                        val duration = parseIsoDuration(item.contentDetails?.duration)
                        val views = item.statistics?.viewCount?.toLongOrNull()
                        val viewsFormatted = when {
                            views != null && views >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.1f Mld visualizzazioni", views / 1_000_000_000.0)
                            views != null && views >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f Mln visualizzazioni", views / 1_000_000.0)
                            views != null && views >= 1_000 -> "${views / 1_000}K visualizzazioni"
                            views != null -> "$views visualizzazioni"
                            else -> null
                        }
                        (item.id ?: "") to Pair(duration, viewsFormatted)
                    }
                } else emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }

            items.map { item ->
                val vId = item.id?.videoId ?: ""
                val snippet = item.snippet
                val title = snippet?.title
                    ?.replace("&quot;", "\"")
                    ?.replace("&amp;", "&")
                    ?.replace("&#39;", "'")
                    ?.replace("&lt;", "<")
                    ?.replace("&gt;", ">").orEmpty()
                val artist = snippet?.channelTitle.orEmpty()
                val thumb = snippet?.thumbnails?.high?.url 
                    ?: snippet?.thumbnails?.medium?.url 
                    ?: snippet?.thumbnails?.default?.url
                    ?: "https://i.ytimg.com/vi/$vId/hqdefault.jpg"

                val info = detailsMap[vId]

                Track(
                    id = vId,
                    title = title,
                    artist = artist,
                    thumbnailUrl = thumb,
                    durationMs = info?.first ?: (180 * 1000L),
                    viewsText = info?.second
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getTopPlayedTracks(limit: Int): Flow<List<Track>> {
        return historyDao.getTopPlayedTracks(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getTotalListeningTimeMs(): Flow<Long?> {
        return historyDao.getTotalListeningTimeMs()
    }

    suspend fun getQuickPicks(): List<Track> {
        return try {
            // 1. Sorgente primaria: home feed di YouTube Music (come l'app ufficiale)
            val homePicks = youtubeMusicService.getHomeQuickPicks()
            if (homePicks.isNotEmpty()) {
                // Arricchisci con i brani recenti e preferiti dell'utente (personalizzazione)
                val recents = getRecentTracks().first().take(5)
                val favorites = getFavoriteTracks().first().take(5)
                return (homePicks + recents + favorites)
                    .distinctBy { it.id }
                    .take(20)
            }
            
            // 2. Fallback: playlist Top 50 Italia
            val playlistTracks = youtubeMusicService.getAlbumTracks(
                browseId = "VLPL4fGSI1pucjM1vN8P9TqD8NstB91vK-Lw",
                albumTitle = "Top 50 Italia",
                artistName = "YouTube Music",
                coverUrl = ""
            )
            val baseTracks = if (playlistTracks.isNotEmpty()) playlistTracks else searchTracks("Top 50 Italia")
            
            val recents = getRecentTracks().first().take(10)
            val favorites = getFavoriteTracks().first().take(10)
            
            (baseTracks.take(30) + recents + favorites)
                .distinctBy { it.id }
                .shuffled()
                .take(20)
        } catch (e: Exception) {
            searchTracks("Top 50 Italia").shuffled().take(20)
        }
    }

    /** Recupera le nuove uscite in tempo reale da YouTube Music.
     *  Usa i dati statici come fallback se la rete non è disponibile. */
    suspend fun fetchNewReleases(): List<Album> {
        val live = youtubeMusicService.fetchNewReleases()
        if (live.isNotEmpty()) return live
        // Fallback: lista statica predefinita
        return getNewReleases()
    }

    fun getNewReleases(): List<Album> {
        // Fallback statico — uscite settembre 2026
        return listOf(
            Album(
                id = "vita_morte_miracoli",
                title = "Vita Morte Miracoli",
                artist = "J-Ax",
                coverUrl = "https://yt3.googleusercontent.com/6X3x4G2z-Dy6bMTNGaOWgfhkNUGEumlEArxxF7QaZ_lZjlv4rkxFUiWZqDEnSY2iDeB4v0nq41Ntr98=w544-h544-l90-rj",
                year = "2026",
                type = "Album",
                trackCount = 14,
                browseId = "MPREb_DxSV04uEv1U"
            ),
            Album(
                id = "giannagold50",
                title = "GiannaGold 50",
                artist = "Gianna Nannini",
                coverUrl = "https://yt3.googleusercontent.com/dwyHtOrAOneeat10rYaxIPpc5Dq0JSIYpBq4shRfKIRMTgkHk8V3OwePDBHgGhdb0VNlKaSTMif4kac=w544-h544-l90-rj",
                year = "2026",
                type = "Album",
                trackCount = 50,
                browseId = "MPREb_VG60q78Yf08"
            ),
            Album(
                id = "3xjo8h14gCI",
                title = "Vangelo",
                artist = "Shiva",
                coverUrl = "https://yt3.googleusercontent.com/QadVc9_pLaa-RtOcJyMWcnNJCHtc9cSoHwlwZvecqeXKN9Yw5filYDj0R9SfLsjVsbiQhzET7B1j9TOy=w544-h544-l90-rj",
                year = "2026",
                type = "Album",
                isExplicit = true,
                trackCount = 14,
                browseId = "MPREb_BlB8gIZE23T"
            ),
            Album(
                id = "G35c59nL61U",
                title = "Serenamente",
                artist = "Juli e Bresh",
                coverUrl = "https://yt3.googleusercontent.com/lHa3qu8YIXkjvQVSQ1vro2cHQP-GZbvm4srECUQmUuN6ZI861tHBdU4fPnXApwoc5o1FcJTMJ-8cXOFP=w544-h544-l90-rj",
                year = "2026",
                type = "Singolo",
                trackCount = 1,
                browseId = "MPREb_5Zy5VmcLXZF"
            ),
            Album(
                id = "Ow8JYT_Oarc",
                title = "Genova e Napoli",
                artist = "Alfa e Gigi D'Alessio",
                coverUrl = "https://yt3.googleusercontent.com/0yUoYwu-nRRV5alxplvaEgZ8x4OLSIJV3r0pE-yxx-ypKlNO4Sb-vXlEnhfp1oab12HjCoGfX2GAQss=w544-h544-l90-rj",
                year = "2026",
                type = "Singolo",
                trackCount = 1,
                browseId = "MPREb_wqQnkbUk48L"
            )
        )
    }


    fun getFavoriteTracks(): Flow<List<Track>> {
        return trackDao.getFavoriteTracks().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun toggleFavorite(track: Track): Boolean {
        val newFavState = !track.isFavorite
        trackDao.insertOrUpdate(
            TrackEntity(
                id = track.id,
                title = track.title,
                artist = track.artist,
                artistId = track.artistId,
                thumbnailUrl = track.thumbnailUrl,
                durationMs = track.durationMs,
                album = track.album,
                albumBrowseId = track.albumBrowseId,
                isFavorite = newFavState
            )
        )
        try {
            syncService.saveFavorite(track.copy(isFavorite = newFavState), newFavState)
        } catch (e: Exception) {
            // Sync differito se offline
        }
        return newFavState
    }

    suspend fun recordHistory(track: Track, positionMs: Long, isNewPlay: Boolean = false) {
        trackDao.insertOrUpdate(
            TrackEntity(
                id = track.id,
                title = track.title,
                artist = track.artist,
                artistId = track.artistId,
                thumbnailUrl = track.thumbnailUrl,
                durationMs = track.durationMs,
                album = track.album,
                albumBrowseId = track.albumBrowseId,
                isFavorite = track.isFavorite
            )
        )
        val existingHistory = historyDao.getHistoryEntry(track.id)
        if (existingHistory != null) {
            historyDao.upsertHistory(
                existingHistory.copy(
                    lastPositionMs = positionMs,
                    playedAt = System.currentTimeMillis(),
                    playCount = if (isNewPlay) existingHistory.playCount + 1 else existingHistory.playCount
                )
            )
        } else {
            historyDao.upsertHistory(
                HistoryEntity(
                    trackId = track.id,
                    lastPositionMs = positionMs,
                    durationMs = track.durationMs,
                    playCount = 1
                )
            )
        }
        try {
            syncService.syncLastPlayback(track, positionMs, track.durationMs)
        } catch (e: Exception) {
            // Ignora se non loggato o offline
        }
    }

    fun getRecentTracks(): Flow<List<Track>> {
        return historyDao.getRecentHistory().map { list ->
            list.map { it.toDomain() }
        }
    }

    fun getPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists().map { list ->
            list.map { Playlist(id = it.id, name = it.name, coverUrl = it.coverUrl, createdAt = it.createdAt) }
        }
    }

    suspend fun getPlaylist(id: String): Playlist? {
        val entity = playlistDao.getPlaylistById(id) ?: return null
        return Playlist(id = entity.id, name = entity.name, coverUrl = entity.coverUrl, createdAt = entity.createdAt)
    }

    fun getPlaylistFlow(id: String): Flow<Playlist?> {
        return playlistDao.getPlaylistFlow(id).map { entity ->
            entity?.let { Playlist(id = it.id, name = it.name, coverUrl = it.coverUrl, createdAt = it.createdAt) }
        }
    }

    fun getPlaylistTracks(playlistId: String): Flow<List<Track>> {
        return playlistDao.getTracksForPlaylist(playlistId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun renamePlaylist(playlistId: String, newName: String) {
        playlistDao.updatePlaylistName(playlistId, newName)
        try {
            // Aggiorna anche nel cloud se disponibile
            val playlist = playlistDao.getPlaylistById(playlistId)
            if (playlist != null) {
                syncService.syncPlaylist(Playlist(id = playlist.id, name = newName, coverUrl = playlist.coverUrl, createdAt = playlist.createdAt, orderIndex = playlist.orderIndex))
            }
        } catch (e: Exception) { /* Handled */ }
    }

    suspend fun updatePlaylistCover(playlistId: String, coverUrl: String?) {
        playlistDao.updatePlaylistCover(playlistId, coverUrl)
        try {
            val playlist = playlistDao.getPlaylistById(playlistId)
            if (playlist != null) {
                syncService.syncPlaylist(Playlist(id = playlist.id, name = playlist.name, coverUrl = playlist.coverUrl, createdAt = playlist.createdAt, orderIndex = playlist.orderIndex))
            }
        } catch (e: Exception) { /* Handled */ }
    }

    suspend fun reorderPlaylists(playlists: List<Playlist>) {
        playlists.forEachIndexed { index, playlist ->
            playlistDao.updatePlaylistOrder(playlist.id, index)
        }
    }

    suspend fun deletePlaylist(playlistId: String) {
        playlistDao.deletePlaylist(playlistId) // CASCADE elimina anche playlist_tracks
        try {
            syncService.deletePlaylist(playlistId)
        } catch (e: Exception) { /* Handled */ }
    }

    suspend fun createPlaylist(name: String): String {
        val id = UUID.randomUUID().toString()
        val playlist = PlaylistEntity(id = id, name = name)
        playlistDao.insertPlaylist(playlist)
        try {
            syncService.syncPlaylist(Playlist(id = id, name = name))
        } catch (e: Exception) {
            // Sync locale
        }
        return id
    }

    suspend fun addTrackToPlaylist(playlistId: String, track: Track) {
        trackDao.insertOrUpdate(
            TrackEntity(
                id = track.id,
                title = track.title,
                artist = track.artist,
                artistId = track.artistId,
                thumbnailUrl = track.thumbnailUrl,
                durationMs = track.durationMs,
                album = track.album,
                albumBrowseId = track.albumBrowseId
            )
        )
        playlistDao.insertTrackToPlaylist(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = track.id,
                orderIndex = (System.currentTimeMillis() / 1000).toInt()
            )
        )
        
        // Imposta la copertina usando UPDATE sicuro se la playlist non ha ancora una copertina personalizzata
        val playlist = playlistDao.getPlaylistById(playlistId)
        if (playlist != null) {
            val newCover = track.thumbnailUrl.toHighResThumbnail()
            if (newCover.isNotEmpty() && playlist.coverUrl.isNullOrEmpty()) {
                playlistDao.updatePlaylistCover(playlistId, newCover)
            }
            try {
                val updatedPl = playlistDao.getPlaylistById(playlistId)
                if (updatedPl != null) {
                    syncService.syncPlaylist(Playlist(id = updatedPl.id, name = updatedPl.name, coverUrl = updatedPl.coverUrl, createdAt = updatedPl.createdAt, orderIndex = updatedPl.orderIndex))
                }
            } catch (e: Exception) { /* Handled */ }
        }
        try {
            syncService.addTrackToPlaylist(playlistId, track)
        } catch (e: Exception) {
            // Handled
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        playlistDao.removeTrackFromPlaylist(playlistId, trackId)
        
        // Se la copertina era basata su questa traccia o vuota, aggiorna con il primo brano rimanente
        val remainingTracks = playlistDao.getTracksForPlaylistList(playlistId)
        val playlist = playlistDao.getPlaylistById(playlistId)
        if (playlist != null) {
            val newCover = remainingTracks.firstOrNull()?.thumbnailUrl?.toHighResThumbnail()
            if (playlist.coverUrl.isNullOrEmpty() || playlist.coverUrl.contains(trackId)) {
                playlistDao.updatePlaylistCover(playlistId, newCover)
            }
            try {
                val updatedPl = playlistDao.getPlaylistById(playlistId)
                if (updatedPl != null) {
                    syncService.syncPlaylist(Playlist(id = updatedPl.id, name = updatedPl.name, coverUrl = updatedPl.coverUrl, createdAt = updatedPl.createdAt, orderIndex = updatedPl.orderIndex))
                }
            } catch (e: Exception) { /* Handled */ }
        }
        
        try {
            syncService.removeTrackFromPlaylist(playlistId, trackId)
        } catch (e: Exception) { /* Handled */ }
    }

    private fun parseIsoDuration(iso: String?): Long {
        if (iso.isNullOrEmpty()) return 180 * 1000L
        return try {
            Duration.parse(iso).toMillis()
        } catch (e: Exception) {
            180 * 1000L
        }
    }

    private fun TrackEntity.toDomain() = Track(
        id = id,
        title = title,
        artist = artist,
        artistId = artistId,
        // Applica toHighResThumbnail() per aggiornare al volo le URL a bassa risoluzione
        // salvate in precedenza nel DB (vecchi hqdefault → upgrade se possibile)
        thumbnailUrl = thumbnailUrl.toHighResThumbnail(),
        durationMs = durationMs,
        album = album,
        albumBrowseId = albumBrowseId,
        isFavorite = isFavorite,
        isDownloaded = isDownloaded,
        localFilePath = localFilePath
    )

    // ── Download offline ──────────────────────────────────────────────────────

    fun getDownloadedTracks(): Flow<List<Track>> =
        trackDao.getDownloadedTracks().map { list -> list.map { it.toDomain() } }

    fun getActiveDownloadProgress(trackId: String): Flow<DownloadProgress?> =
        offlineManager?.activeDownloads?.map { it[trackId]?.progress } ?: kotlinx.coroutines.flow.flowOf(null)

    fun getActiveDownloads(): Flow<List<OfflineManager.ActiveDownload>> =
        offlineManager?.activeDownloads?.map { it.values.toList() } ?: kotlinx.coroutines.flow.flowOf(emptyList())

    fun startDownload(track: Track) {
        offlineManager?.startDownload(track)
    }

    suspend fun removeDownload(trackId: String) {
        offlineManager?.removeDownload(trackId)
    }

    fun isDownloaded(trackId: String): Boolean =
        offlineManager?.isDownloaded(trackId) ?: false

    // ── Sync Cloud Bidirezionale ────────────────────────────────────────────────
    suspend fun syncBidirectional() {
        try {
            // Fase 1: Pull dal Cloud
            val cloudFavorites = syncService.pullFavorites()
            cloudFavorites.forEach { track ->
                trackDao.insertOrUpdate(
                    TrackEntity(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        thumbnailUrl = track.thumbnailUrl,
                        durationMs = track.durationMs,
                        isFavorite = true
                    )
                )
            }

            val cloudPlaylists = syncService.pullPlaylists()
            cloudPlaylists.forEach { pl ->
                val localPl = playlistDao.getPlaylistById(pl.id)
                val effectiveCover = pl.coverUrl ?: localPl?.coverUrl
                playlistDao.insertPlaylist(
                    PlaylistEntity(id = pl.id, name = pl.name, coverUrl = effectiveCover, createdAt = pl.createdAt)
                )
                val tracks = syncService.pullPlaylistTracks(pl.id)
                tracks.forEachIndexed { index, track ->
                    trackDao.insertOrUpdate(
                        TrackEntity(
                            id = track.id,
                            title = track.title,
                            artist = track.artist,
                            thumbnailUrl = track.thumbnailUrl,
                            durationMs = track.durationMs
                        )
                    )
                    playlistDao.insertTrackToPlaylist(
                        PlaylistTrackEntity(playlistId = pl.id, trackId = track.id, orderIndex = index)
                    )
                }
                if (effectiveCover.isNullOrEmpty() && tracks.isNotEmpty()) {
                    val firstCover = tracks.first().thumbnailUrl.toHighResThumbnail()
                    playlistDao.updatePlaylistCover(pl.id, firstCover)
                }
            }

            val cloudHistory = syncService.pullHistory()
            cloudHistory.forEach { data ->
                val trackId = data["trackId"] as? String
                if (trackId != null) {
                    val positionMs = (data["positionMs"] as? Long) ?: 0L
                    val durationMs = (data["durationMs"] as? Long) ?: 0L
                    val playedAt = (data["lastPlayedAt"] as? Long) ?: System.currentTimeMillis()
                    
                    trackDao.insertOrUpdate(
                        TrackEntity(
                            id = trackId,
                            title = (data["title"] as? String) ?: "",
                            artist = (data["artist"] as? String) ?: "",
                            thumbnailUrl = (data["thumbnailUrl"] as? String) ?: "",
                            durationMs = durationMs
                        )
                    )
                    
                    val existing = historyDao.getHistoryEntry(trackId)
                    if (existing != null) {
                        historyDao.upsertHistory(existing.copy(lastPositionMs = positionMs, playedAt = playedAt))
                    } else {
                        historyDao.upsertHistory(HistoryEntity(trackId = trackId, lastPositionMs = positionMs, durationMs = durationMs, playedAt = playedAt))
                    }
                }
            }
            
            // Fase 2: Push verso Cloud (Semplificato per i dati locali non presenti, 
            // ma in una logica "merge" completa andrebbero inviati tutti)
            // L'implementazione completa pusherebbe tutte le playlist e i preferiti.
            // Per ora deleghiamo il push alle azioni singole (toggleFavorite, createPlaylist) per limitare i write.
        } catch (e: Exception) {
            e.printStackTrace()
            // Ignora eventuali errori di rete
        }
    }

}
