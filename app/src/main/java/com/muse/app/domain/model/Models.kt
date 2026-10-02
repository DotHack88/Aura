package com.muse.app.domain.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val thumbnailUrl: String,
    val durationMs: Long,
    val viewsText: String? = null,
    val album: String? = null,
    val albumBrowseId: String? = null,
    val isExplicit: Boolean = false,
    val youtubeUrl: String = "https://www.youtube.com/watch?v=$id",
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null
)

data class Artist(
    val id: String,
    val name: String,
    val handle: String? = null,
    val avatarUrl: String,
    val bannerUrl: String? = null,
    val subscribersText: String? = null,
    val videoCountText: String? = null,
    val isVerified: Boolean = true,
    val browseId: String? = null
)

data class ArtistDetails(
    val artist: Artist,
    val topTracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val singles: List<Album> = emptyList(),
    val similarArtists: List<Artist> = emptyList()
)

data class SearchResult(
    val artist: Artist? = null,
    val popularTracks: List<Track> = emptyList(),
    val allTracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList()
)

data class Chip(
    val title: String,
    val endpointParams: String? = null,
    val isSelected: Boolean = false
)

data class UpNextResult(
    val tracks: List<Track> = emptyList(),
    val chips: List<Chip> = emptyList()
)

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String,
    val year: String? = "2026",
    val type: String = "Album", // Album, Singolo, EP
    val isExplicit: Boolean = false,
    val trackCount: Int = 10,
    val browseId: String? = null
)

data class Playlist(
    val id: String,
    val name: String,
    val coverUrl: String? = null,
    val trackCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val orderIndex: Int = 0
)

data class Lyrics(
    val trackId: String,
    val plainText: String? = null,
    val lines: List<LyricsLine> = emptyList()
)

data class LyricsLine(
    val timestampMs: Long,
    val text: String
)

enum class PlayerMode {
    MINI,
    FULL,
    VIDEO,
    LYRICS
}

enum class RepeatMode {
    OFF,
    ONE,
    ALL
}

data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val playerMode: PlayerMode = PlayerMode.MINI,
    val isVideoMode: Boolean = false,
    val currentYouTubeVideoId: String? = null, // ID del video YouTube in riproduzione in video mode
    val playlist: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val errorMessage: String? = null,
    val sleepTimerRemainingMs: Long = 0L, // 0 = timer disattivo
    val crossfadeDurationMs: Long = 0L, // 0 = crossfade disattivo
    val upNextChips: List<Chip> = emptyList(), // I chip "A SEGUIRE" da YT Music
    val selectedChip: String? = null // Titolo del chip selezionato
)

