package com.muse.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbDownOffAlt
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbUpOffAlt
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.muse.app.R
import com.muse.app.data.repository.MusicRepository
import com.muse.app.domain.model.Album
import com.muse.app.ui.components.shimmerEffect
import com.muse.app.domain.model.Track
import com.muse.app.player.PlayerManager
import androidx.compose.ui.graphics.Brush
import com.muse.app.ui.components.TrackOptionsBottomSheet
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    musicRepository: MusicRepository,
    playerManager: PlayerManager,
    onOpenSearch: () -> Unit,
    onNavigateToNewReleases: () -> Unit,
    onNavigateToMoodsAndGenres: () -> Unit,
    onGenreClick: (String) -> Unit,
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (browseId: String, title: String, artist: String, cover: String) -> Unit = { _, _, _, _ -> },
    onNavigateToStats: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val favorites by musicRepository.getFavoriteTracks().collectAsState(initial = emptyList())
    val recentTracks by musicRepository.getRecentTracks().collectAsState(initial = emptyList())
    var quickPicks by remember { mutableStateOf<List<Track>>(emptyList()) }
    val newReleases = remember { musicRepository.getNewReleases() }
    var suggestedTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var trendingTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    val totalTimeMs by musicRepository.getTotalListeningTimeMs().collectAsState(initial = 0L)
    val topTracks by musicRepository.getTopPlayedTracks(1).collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        val year = java.time.LocalDate.now().year
        val month = java.time.LocalDate.now().month.getDisplayName(
            java.time.format.TextStyle.FULL, 
            java.util.Locale.ITALIAN
        ).lowercase()
        
        quickPicks = musicRepository.getQuickPicks()
        suggestedTracks = musicRepository.searchTracks("Canzoni italiane $year")
        trendingTracks = musicRepository.searchTracks("Nuove canzoni italiane $month $year")
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF030303)),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)
    ) {


        // Chip Filter Row (Moods & Genres come su YouTube Music)
        item {
            val chips = listOf("Energica", "Relax", "Allenamento", "Concentrazione", "Viaggio", "Festa")
            var selectedChip by remember { mutableStateOf<String?>(null) }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chips) { chip ->
                    val isSelected = selectedChip == chip
                    FilterChip(
                        selected = isSelected,
                        onClick = { 
                            selectedChip = if (isSelected) null else chip 
                            if (!isSelected) {
                                onGenreClick(chip)
                            }
                        },
                        label = {
                            Text(
                                text = chip,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFF212121),
                            labelColor = Color(0xFFE0E0E0),
                            selectedContainerColor = Color.White,
                            selectedLabelColor = Color.Black
                        ),
                        border = null
                    )
                }
                
                item {
                    FilterChip(
                        selected = false,
                        onClick = onNavigateToMoodsAndGenres,
                        label = {
                            Text(
                                text = "Tutti i generi...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = null
                    )
                }
            }
        }

        // Card Statistiche Settimanali
        item {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(totalTimeMs ?: 0L)
            val topTrack = topTracks.firstOrNull()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF6A11CB), Color(0xFF2575FC))
                        )
                    )
                    .clickable { onNavigateToStats() }
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎵", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Le tue statistiche",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = Color.White.copy(alpha = 0.75f),
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Vai alle statistiche",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (minutes > 0) "${minutes} min ascoltati" else "Inizia ad ascoltare!",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    if (topTrack != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Top: ${topTrack.title} · ${topTrack.artist}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.8f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 1. SEZIONE SCELTE RAPIDE (Carousel Shelf a 4 righe x colonna)
        item {
            QuickPicksSection(
                tracks = quickPicks,
                onPlayTrack = { track ->
                    playerManager.playTrack(track, quickPicks)
                },
                onPlayAll = {
                    if (quickPicks.isNotEmpty()) {
                        playerManager.playTrack(quickPicks.first(), quickPicks)
                    }
                },
                onMoreClick = { track ->
                    selectedTrackForMenu = track
                }
            )
        }



        // 3. SEZIONE ASCOLTATI DI RECENTE (Se presenti)
        if (recentTracks.isNotEmpty()) {
            item {
                SectionHeader(
                    strapline = "RIASCOLTA",
                    title = "Ascoltati di recente"
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(recentTracks) { track ->
                        StandardMusicCard(
                            track = track,
                            onClick = { playerManager.playTrack(track, recentTracks) },
                            onMoreClick = { selectedTrackForMenu = track }
                        )
                    }
                }
            }
        }

        // 4. SEZIONE CONSIGLIATI PER TE / HIT DEL MOMENTO
        if (suggestedTracks.isNotEmpty()) {
            item {
                SectionHeader(
                    strapline = "IN BASE AI TUOI ASCOLTI",
                    title = "Consigliati per te"
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(suggestedTracks) { track ->
                        StandardMusicCard(
                            track = track,
                            onClick = { playerManager.playTrack(track, suggestedTracks) },
                            onMoreClick = { selectedTrackForMenu = track }
                        )
                    }
                }
            }
        }

        // 5. SEZIONE I TUOI BRANI PREFERITI
        if (favorites.isNotEmpty()) {
            item {
                SectionHeader(
                    strapline = "LA TUA MUSICA",
                    title = "I tuoi preferiti"
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(favorites) { track ->
                        StandardMusicCard(
                            track = track,
                            onClick = { playerManager.playTrack(track, favorites) },
                            onMoreClick = { selectedTrackForMenu = track }
                        )
                    }
                }
            }
        }
    }

    selectedTrackForMenu?.let { trk ->
        TrackOptionsBottomSheet(
            track = trk,
            musicRepository = musicRepository,
            playerManager = playerManager,
            onDismiss = { selectedTrackForMenu = null },
            onNavigateToSearch = { query ->
                onOpenSearch()
            },
            onNavigateToAlbum = { browseId, title, artist, cover ->
                onNavigateToAlbum(browseId, title, artist, cover)
            }
        )
    }
}

/**
 * Sezione "Scelte rapide" con carousel 4 righe per pagina, pulsante "Riproduci tutti" e frecce di navigazione
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickPicksSection(
    tracks: List<Track>,
    onPlayTrack: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onMoreClick: (Track) -> Unit = {}
) {
    // Raggruppa i brani in pagine da 4 elementi ciascuna, o crea 12 skeleton (3 pagine) se in loading
    val isSkeleton = tracks.isEmpty()
    val chunkedPages = remember(tracks) { 
        if (isSkeleton) {
            (1..12).map { Track(id = it.toString(), title = "", artist = "", thumbnailUrl = "", durationMs = 0L) }.chunked(4)
        } else {
            tracks.chunked(4) 
        }
    }
    val pagerState = rememberPagerState(pageCount = { chunkedPages.size })
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header con Titolo "Scelte rapide", "Riproduci tutti" e pulsanti Freccia
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "INIZIA A SENTIRE LA RADIO DA UN BRANO",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFFAAAAAA),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Scelte rapide",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Pulsante Riproduci tutti
                OutlinedButton(
                    onClick = onPlayAll,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF404040)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = "Riproduci tutti",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Frecce carousel
                IconButton(
                    onClick = {
                        if (pagerState.currentPage > 0) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                    },
                    enabled = pagerState.currentPage > 0,
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White,
                        disabledContentColor = Color(0xFF444444)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Indietro"
                    )
                }

                IconButton(
                    onClick = {
                        if (pagerState.currentPage < chunkedPages.size - 1) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    enabled = pagerState.currentPage < chunkedPages.size - 1,
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White,
                        disabledContentColor = Color(0xFF444444)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Avanti"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Pager orizzontale a 4 righe verticali
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) { pageIndex ->
            val pageTracks = chunkedPages.getOrNull(pageIndex).orEmpty()
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pageTracks.forEach { track ->
                    if (isSkeleton) {
                        ShimmerQuickPickItem()
                    } else {
                        QuickPickItem(
                            track = track,
                            onPlay = { onPlayTrack(track) },
                            onMoreClick = { onMoreClick(track) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Singola riga del Quick Pick Item conforme allo stile ytmusic-responsive-list-item-renderer
 */
@Composable
fun QuickPickItem(
    track: Track,
    onPlay: () -> Unit,
    onMoreClick: () -> Unit = {}
) {
    var isLiked by remember { mutableStateOf(false) }
    var isDisliked by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onPlay() }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail da 56dp con Play Overlay
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Sfondo semi-trasparente e play icon
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Riproduci ${track.title}",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Colonna Testo (Titolo con badge esplicito, Artista • Riproduzioni / Album)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.isExplicit) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF888888), RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "E",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                fontSize = 9.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Subtitle: Artista • Riproduzioni • Album
            val subtitle = buildString {
                append(track.artist)
                if (!track.viewsText.isNullOrBlank()) {
                    append(" • ")
                    append(track.viewsText)
                } else if (!track.album.isNullOrBlank()) {
                    append(" • ")
                    append(track.album)
                }
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFFAAAAAA)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Pulsante Like/Dislike compatti (come su YouTube Music)
        IconButton(
            onClick = {
                isLiked = !isLiked
                if (isLiked) isDisliked = false
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                contentDescription = "Mi piace",
                tint = if (isLiked) Color.White else Color(0xFF888888),
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(
            onClick = {
                isDisliked = !isDisliked
                if (isDisliked) isLiked = false
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isDisliked) Icons.Default.ThumbDown else Icons.Default.ThumbDownOffAlt,
                contentDescription = "Non mi piace",
                tint = if (isDisliked) Color.White else Color(0xFF888888),
                modifier = Modifier.size(18.dp)
            )
        }

        // Menu Overflow
        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Menu Azione",
                tint = Color(0xFF888888),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun SectionHeader(
    strapline: String? = null,
    title: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        if (!strapline.isNullOrEmpty()) {
            Text(
                text = strapline,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFFAAAAAA),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
    }
}

@Composable
fun StandardMusicCard(
    track: Track,
    onClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1E1E))
        ) {
            // Placeholder icon shown until image loads
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.15f),
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
            )
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Play button overlay in basso a destra
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                color = Color.White
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFFAAAAAA)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (onMoreClick != null) {
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opzioni",
                        tint = Color(0xFF888888),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ─── Nuove Uscite ─────────────────────────────────────────────────────────────

@Composable
fun NewReleasesSection(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    onNavigateToNewReleases: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "NOVITÀ",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFFAAAAAA),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Nuove uscite",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
            TextButton(onClick = onNavigateToNewReleases) {
                Text(
                    text = "Vedi tutto",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFFAAAAAA),
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(albums) { album ->
                AlbumCard(
                    album = album,
                    onPlay = { onAlbumClick(album) }
                )
            }
        }
    }
}

@Composable
fun AlbumCard(
    album: Album,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(148.dp)
            .clickable { onPlay() }
    ) {
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1E1E))
        ) {
            AsyncImage(
                model = album.coverUrl,
                contentDescription = album.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Badge tipo (Album / Singolo / EP) in alto a sinistra
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = album.type.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp
                    )
                )
            }

            // Badge E (esplicito) in basso a sinistra
            if (album.isExplicit) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(
                            color = Color(0xFF888888),
                            shape = RoundedCornerShape(2.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "E",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            fontSize = 9.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                color = Color.White
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${album.year} • ${album.artist}",
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color(0xFFAAAAAA)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ShimmerQuickPickItem() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shimmer Thumbnail
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmerEffect()
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Testi
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
        }

        Spacer(modifier = Modifier.width(16.dp))
        
        // Icona menu
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .shimmerEffect()
        )
    }
}
