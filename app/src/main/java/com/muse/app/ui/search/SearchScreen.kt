package com.muse.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.muse.app.data.repository.MusicRepository
import com.muse.app.domain.model.Artist
import com.muse.app.domain.model.SearchResult
import com.muse.app.domain.model.Track
import com.muse.app.player.PlayerManager
import com.muse.app.ui.components.TrackOptionsBottomSheet
import com.muse.app.ui.components.FavoriteBurstButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    musicRepository: MusicRepository,
    playerManager: PlayerManager,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: ((browseId: String, title: String, artist: String, cover: String) -> Unit)? = null,
    onNavigateToPlaylist: (String) -> Unit = {},
    onNavigateToMoodsAndGenres: (() -> Unit)? = null,
    onNavigateToNewReleases: (() -> Unit)? = null,
    initialQuery: String? = null,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<SearchResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var albumSearchResults by remember { mutableStateOf<List<com.muse.app.domain.model.Album>?>(null) }
    var singlesSearchResults by remember { mutableStateOf<List<com.muse.app.domain.model.Album>?>(null) }
    var isAlbumsLoading by remember { mutableStateOf(false) }

    var selectedFilter by remember { mutableStateOf("Tutto") }
    val filters = listOf("Tutto", "Brani", "Album", "Artisti", "Playlist")

    val coroutineScope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    

    var dynamicSuggestions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(Unit) {
        if (dynamicSuggestions.isEmpty()) {
            // Map di immagini reali per i fallback
            val fallbackImages = mapOf(
                "Linkin Park" to "https://cdn-images.dzcdn.net/images/artist/4886905210739af3438990897bad3a98/250x250-000000-80-0-0.jpg",
                "Queen" to "https://cdn-images.dzcdn.net/images/artist/fd240ebf68c6c2477d7f09c5847aa31e/250x250-000000-80-0-0.jpg",
                "Coldplay" to "https://cdn-images.dzcdn.net/images/artist/3087954bca22f306324912e5ac8375c3/250x250-000000-80-0-0.jpg",
                "Arctic Monkeys" to "https://cdn-images.dzcdn.net/images/artist/6c03e4c7c36800897fd468633286db24/250x250-000000-80-0-0.jpg",
                "The Weeknd" to "https://cdn-images.dzcdn.net/images/artist/581693b4724a7fcfa754455101e13a44/250x250-000000-80-0-0.jpg",
                "Daft Punk" to "https://cdn-images.dzcdn.net/images/artist/638e69b9caaf9f9f3f8826febea7b543/250x250-000000-80-0-0.jpg",
                "Nirvana" to "https://cdn-images.dzcdn.net/images/artist/ad67af8b0dca71d5e69f1afd8e045c4e/250x250-000000-80-0-0.jpg",
                "Imagine Dragons" to "https://cdn-images.dzcdn.net/images/artist/1ba025c23cae3dee14b51152990285fc/250x250-000000-80-0-0.jpg",
                "Måneskin" to "https://cdn-images.dzcdn.net/images/artist/b5ee25137476918b7660f80529981436/250x250-000000-80-0-0.jpg",
                "Pink Floyd" to "https://cdn-images.dzcdn.net/images/artist/d62a818a5de6455f17b6a992cf22b32f/250x250-000000-80-0-0.jpg",
                "The Beatles" to "https://cdn-images.dzcdn.net/images/artist/16925ed8f96eb621ecd56c2c8ba3da6e/250x250-000000-80-0-0.jpg",
                "Red Hot Chili Peppers" to "https://cdn-images.dzcdn.net/images/artist/238f5524a401dfdd5cac685f0f7989bd/250x250-000000-80-0-0.jpg",
                "Guns N Roses" to "https://cdn-images.dzcdn.net/images/artist/c62989722e38d7a2ab18afde3c19bc5c/250x250-000000-80-0-0.jpg",
                "Foo Fighters" to "https://cdn-images.dzcdn.net/images/artist/c2a443972cacf77896f91a66b30bfd9e/250x250-000000-80-0-0.jpg"
            )

            // Prima prova artisti dalla cronologia dell'utente (prima emissione del Flow)
            val historyTracks: List<Track> = musicRepository.getTopPlayedTracks(20).first()

            val results = mutableListOf<Pair<String, String>>()
            if (historyTracks.isNotEmpty()) {
                val artistNames = historyTracks
                    .map { t -> t.artist }
                    .distinct()
                    .filter { name -> name.isNotBlank() }
                    .take(8)
                for (name in artistNames) {
                    val thumb = historyTracks.firstOrNull { t -> t.artist == name }?.thumbnailUrl ?: ""
                    val finalUrl = if (thumb.isNotBlank()) thumb
                                   else fallbackImages[name] ?: "https://api.dicebear.com/7.x/initials/png?seed=${name.replace(" ", "+")}&backgroundColor=1a1a2e&textColor=ffffff"
                    results.add(Pair(name, finalUrl))
                }
            }

            // Fallback per utenti nuovi o con poca cronologia
            if (results.size < 8) {
                val fallbackNames = fallbackImages.keys.toList()
                    .filter { it !in results.map { r -> r.first } }
                    .shuffled()
                    .take(8 - results.size)
                for (name in fallbackNames) {
                    results.add(Pair(name, fallbackImages[name]!!))
                }
            }
            dynamicSuggestions = results
        }
    }



    // Carica album + singoli quando il filtro Album è selezionato
    // Strategia prioritaria: getArtistDetails(browseId) → molto più affidabile della ricerca generica
    LaunchedEffect(selectedFilter, query, searchResult) {
        if ((selectedFilter == "Album" || selectedFilter == "Tutto") && query.isNotBlank()) {
            isAlbumsLoading = true
            val artistBrowseId = searchResult?.artist?.browseId
            if (artistBrowseId != null) {
                // Percorso ottimale: usa l'artista già trovato e prendi i suoi album/singoli
                val details = musicRepository.getArtistDetails(artistBrowseId)
                albumSearchResults = details?.albums ?: emptyList()
                singlesSearchResults = details?.singles ?: emptyList()
            } else {
                // Fallback: cerca tramite API search con params filtro album
                val all = musicRepository.searchAlbums(query)
                albumSearchResults = all.filter { it.type == "Album" }
                singlesSearchResults = all.filter { it.type != "Album" }
            }
            isAlbumsLoading = false
        }
    }

    // Ricerca debounced
    fun extractYoutubeVideoId(input: String): String? {
        // Riconosce: https://music.youtube.com/watch?v=XXXXX  o  https://youtu.be/XXXXX  o  https://www.youtube.com/watch?v=XXXXX
        val patterns = listOf(
            Regex("""[?&]v=([a-zA-Z0-9_-]{11})"""),
            Regex("""youtu\.be/([a-zA-Z0-9_-]{11})"""),
            Regex("""youtube\.com/embed/([a-zA-Z0-9_-]{11})""")
        )
        for (p in patterns) {
            val match = p.find(input)
            if (match != null) return match.groupValues[1]
        }
        // Se è già un videoId puro (11 caratteri alfanumerici)
        if (input.matches(Regex("""[a-zA-Z0-9_-]{11}"""))) return input
        return null
    }

    fun onQueryChange(newQuery: String) {
        query = newQuery
        albumSearchResults = null
        singlesSearchResults = null
        searchJob?.cancel()
        if (newQuery.isBlank()) {
            searchResult = null
            isLoading = false
            return
        }

        searchJob = coroutineScope.launch {
            delay(350)
            isLoading = true

            // Controlla se è un URL di YouTube/YouTube Music o un videoId diretto
            val videoId = extractYoutubeVideoId(newQuery.trim())
            if (videoId != null) {
                val track = musicRepository.getTrackByVideoId(videoId)
                searchResult = if (track != null) {
                    SearchResult(allTracks = listOf(track), popularTracks = listOf(track))
                } else {
                    SearchResult()
                }
            } else {
                searchResult = musicRepository.searchAll(newQuery)
            }
            isLoading = false
        }
    }

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            onQueryChange(initialQuery)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
    ) {
        // Search Bar YouTube Style
        OutlinedTextField(
            value = query,
            onValueChange = { onQueryChange(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Cerca artisti, band, brani, album...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cerca") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Pulisci")
                    }
                }
            },
            shape = RoundedCornerShape(24.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (query.isNotBlank()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                items(filters) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        if (isLoading || (selectedFilter == "Album" && isAlbumsLoading)) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (selectedFilter == "Album" && query.isNotBlank()) {
            // Gestione dedicata filtro Album
            val albums = albumSearchResults
            if (albums == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            } else if (albums.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nessun album trovato per \"$query\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            } else {
                val albums = albumSearchResults ?: emptyList()
                val singles = singlesSearchResults ?: emptyList()
                val hasAlbums = albums.isNotEmpty()
                val hasSingles = singles.isNotEmpty()

                if (!hasAlbums && !hasSingles) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nessun album trovato per \"$query\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        // Sezione ALBUM
                        if (hasAlbums) {
                            item {
                                Text(
                                    text = "Album",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                                )
                            }
                            items(albums.chunked(2)) { rowAlbums ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowAlbums.forEach { album ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            AlbumSearchCard(
                                                album = album,
                                                onClick = {
                                                    album.browseId?.let { browseId ->
                                                        onNavigateToAlbum?.invoke(browseId, album.title, album.artist, album.coverUrl)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                    if (rowAlbums.size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        // Sezione SINGOLI ED EP
                        if (hasSingles) {
                            item {
                                Text(
                                    text = "Singoli ed EP",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                )
                            }
                            items(singles.chunked(2)) { rowSingles ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowSingles.forEach { single ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            AlbumSearchCard(
                                                album = single,
                                                onClick = {
                                                    single.browseId?.let { browseId ->
                                                        onNavigateToAlbum?.invoke(browseId, single.title, single.artist, single.coverUrl)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                    if (rowSingles.size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        } else if (searchResult == null || (searchResult?.allTracks.isNullOrEmpty() && searchResult?.artist == null)) {
            if (query.isNotBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nessun risultato trovato per \"$query\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            } else {
                // Suggerimenti iniziali con sezioni Moods & Nuove Uscite
                SearchSuggestions(
                    suggestions = dynamicSuggestions,
                    onSelect = { onQueryChange(it) },
                    onNavigateToMoodsAndGenres = onNavigateToMoodsAndGenres,
                    onNavigateToNewReleases = onNavigateToNewReleases
                )
            }
        } else {
            val result = searchResult!!
            val tracks = result.allTracks

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // 0. Fallback per risultati vuoti nel filtro
                val isEmptyFilter = when (selectedFilter) {
                    "Brani" -> tracks.isEmpty()
                    "Artisti" -> result.artist == null
                    "Playlist" -> result.playlists.isEmpty()
                    else -> false
                }
                
                if (isEmptyFilter) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nessun risultato trovato in $selectedFilter",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // 1. YouTube Official Artist / Band Card (se trovato)
                if (selectedFilter == "Tutto" || selectedFilter == "Artisti") {
                    result.artist?.let { artist ->
                        item {
                            YoutubeOfficialArtistCard(
                                artist = artist,
                                popularTracks = result.popularTracks,
                                musicRepository = musicRepository,
                                onPlayTrack = { track ->
                                    playerManager.playTrack(track, tracks)
                                },
                                onPlayAll = {
                                    if (tracks.isNotEmpty()) {
                                        playerManager.playTrack(tracks.first(), tracks)
                                    }
                                },
                                onArtistClick = {
                                    artist.browseId?.let { onNavigateToArtist(it) }
                                }
                            )
                        }
                    }
                }

                // 1.5 Album e Singoli Section (solo filtro Tutto)
                if (selectedFilter == "Tutto") {
                    val fallbackAlbums = result.albums.filter { it.type == "Album" }
                    val fallbackSingles = result.albums.filter { it.type != "Album" }
                    
                    val albumsToShow = albumSearchResults ?: fallbackAlbums
                    val singlesToShow = singlesSearchResults ?: fallbackSingles
                    
                    if (albumsToShow.isNotEmpty()) {
                        item {
                            Text(
                                text = "Album",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(albumsToShow) { album ->
                                    AlbumSearchCard(
                                        album = album,
                                        onClick = {
                                            album.browseId?.let { browseId ->
                                                onNavigateToAlbum?.invoke(browseId, album.title, album.artist, album.coverUrl)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (singlesToShow.isNotEmpty()) {
                        item {
                            Text(
                                text = "Singoli ed EP",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(singlesToShow) { album ->
                                    AlbumSearchCard(
                                        album = album,
                                        onClick = {
                                            album.browseId?.let { browseId ->
                                                onNavigateToAlbum?.invoke(browseId, album.title, album.artist, album.coverUrl)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 1.6 Playlist Section
                if ((selectedFilter == "Tutto" || selectedFilter == "Playlist") && result.playlists.isNotEmpty()) {
                    item {
                        Text(
                            text = "Playlist",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(result.playlists) { playlist ->
                                PlaylistSearchCard(
                                    playlist = playlist,
                                    onClick = { onNavigateToPlaylist(playlist.id) }
                                )
                            }
                        }
                    }
                }

                // 2. Sezione Brani / Video musicali YouTube
                if ((selectedFilter == "Tutto" || selectedFilter == "Brani") && tracks.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (result.artist != null) "Tutti i brani & Video" else "Brani",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${tracks.size} risultati",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }

                    items(tracks) { track ->
                        TrackSearchItem(
                            track = track,
                            musicRepository = musicRepository,
                            playerManager = playerManager,
                            onNavigateToAlbum = onNavigateToAlbum,
                            onClick = { playerManager.playTrack(track, tracks) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card cinematica ufficiale stile YouTube Official Artist Card
 */
@Composable
fun YoutubeOfficialArtistCard(
    artist: Artist,
    popularTracks: List<Track>,
    musicRepository: MusicRepository,
    onPlayTrack: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onArtistClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2638)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onArtistClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E4F66),
                            Color(0xFF0F1722),
                            Color(0xFF0D1117)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            // Header Artista (Avatar + Nome + Badge verificato + Iscritti)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = artist.avatarUrl,
                    contentDescription = artist.name,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = com.muse.app.R.drawable.ic_youtube_artist_badge),
                            contentDescription = "Canale Artista Ufficiale",
                            tint = Color(0xFFF7F7F7),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    artist.handle?.let { handle ->
                        Text(
                            text = handle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val infoParts = listOfNotNull(artist.subscribersText, artist.videoCountText)
                    if (infoParts.isNotEmpty()) {
                        Text(
                            text = infoParts.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons (Mix / Riproduci tutto)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPlayAll,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Riproduci Mix", fontWeight = FontWeight.SemiBold)
                }

                // Bottone Segui / Seguito
                FollowArtistButton(artist = artist, musicRepository = musicRepository)
            }

            // Shelf Orizzontale dei brani più popolari
            if (popularTracks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Brani più ascoltati",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.9f)
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(popularTracks) { track ->
                        PopularTrackCard(
                            track = track,
                            onClick = { onPlayTrack(track) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card singola per brani nello shelf orizzontale
 */
@Composable
fun PopularTrackCard(
    track: Track,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Durata overlay
                val minutes = (track.durationMs / 1000) / 60
                val seconds = (track.durationMs / 1000) % 60
                val durationText = String.format("%d:%02d", minutes, seconds)

                Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = durationText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = track.title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            track.viewsText?.let { views ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = views,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Riga singola del brano
 */
@Composable
fun TrackSearchItem(
    track: Track,
    musicRepository: MusicRepository? = null,
    playerManager: PlayerManager? = null,
    onNavigateToAlbum: ((browseId: String, title: String, artist: String, cover: String) -> Unit)? = null,
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val favoriteTracks by musicRepository?.getFavoriteTracks()?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val isFavorite = favoriteTracks.any { it.id == track.id }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val metaList = listOfNotNull(track.artist, track.viewsText)
            Text(
                text = metaList.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Riproduci",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        if (musicRepository != null && playerManager != null) {
            FavoriteBurstButton(
                isFavorite = isFavorite,
                onClick = {
                    scope.launch { musicRepository.toggleFavorite(track) }
                }
            )
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Opzioni",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }

    if (showMenu && musicRepository != null && playerManager != null) {
        TrackOptionsBottomSheet(
            track = track,
            musicRepository = musicRepository,
            playerManager = playerManager,
            onDismiss = { showMenu = false },
            onNavigateToAlbum = onNavigateToAlbum
        )
    }
}

@Composable
fun SearchSuggestions(
    suggestions: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    onNavigateToMoodsAndGenres: (() -> Unit)? = null,
    onNavigateToNewReleases: (() -> Unit)? = null
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // --- BANNER MOOD & GENERI ---
        if (onNavigateToMoodsAndGenres != null) {
            item {
                Text(
                    text = "Esplora",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Banner Mood e Generi
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(110.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF6B21A8), Color(0xFF9333EA))
                                )
                            )
                            .clickable { onNavigateToMoodsAndGenres() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "🎭",
                                fontSize = 32.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Mood & Generi",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                    }

                    // Banner Nuove Uscite
                    if (onNavigateToNewReleases != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(110.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF0F4C81), Color(0xFF1565C0))
                                    )
                                )
                                .clickable { onNavigateToNewReleases() },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "🎵",
                                    fontSize = 32.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Nuove Uscite",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- RICERCHE CONSIGLIATE ---
        item {
            Text(
                text = "Ricerche consigliate",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            if (suggestions.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestions.forEach { (name, url) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelect(name) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar circolare sulla sinistra
                            AsyncImage(
                                model = url.ifEmpty { "https://api.dicebear.com/7.x/initials/png?seed=${name.replace(" ", "+")}&backgroundColor=1a1a2e&textColor=ffffff" },
                                contentDescription = name,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            // Nome della band
                            Text(
                                text = name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// FollowArtistButton — persiste il follow nell'app, indipendente da YouTube
// ---------------------------------------------------------------------------
@Composable
fun FollowArtistButton(
    artist: Artist,
    musicRepository: MusicRepository
) {
    val scope = rememberCoroutineScope()
    var isFollowing by remember { mutableStateOf(false) }

    // Leggi lo stato corrente dal DB
    LaunchedEffect(artist.id) {
        isFollowing = musicRepository.isFollowingArtist(artist.id)
    }

    OutlinedButton(
        onClick = {
            scope.launch {
                if (isFollowing) {
                    musicRepository.unfollowArtist(artist.id)
                    isFollowing = false
                } else {
                    musicRepository.followArtist(artist)
                    isFollowing = true
                }
            }
        },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isFollowing) Color.White.copy(alpha = 0.15f) else Color.Transparent,
            contentColor = Color.White
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.horizontalGradient(
                listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.4f))
            )
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        if (isFollowing) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Seguito", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Segui", fontSize = 13.sp)
        }
    }
}

@Composable
fun AlbumSearchCard(
    album: com.muse.app.domain.model.Album,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = album.coverUrl,
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = album.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = album.type + " • " + album.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PlaylistSearchCard(
    playlist: com.muse.app.domain.model.Playlist,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = playlist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Playlist",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
