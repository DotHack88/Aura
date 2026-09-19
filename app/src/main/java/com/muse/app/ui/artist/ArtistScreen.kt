package com.muse.app.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MoreVert
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
import com.muse.app.domain.model.Album
import com.muse.app.domain.model.Artist
import com.muse.app.domain.model.ArtistDetails
import com.muse.app.domain.model.Track
import com.muse.app.player.PlayerManager
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    browseId: String,
    musicRepository: MusicRepository,
    playerManager: PlayerManager,
    onBack: () -> Unit,
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (browseId: String, title: String, artist: String, cover: String) -> Unit = { _, _, _, _ -> }
) {
    var details by remember { mutableStateOf<ArtistDetails?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(browseId) {
        isLoading = true
        details = musicRepository.getArtistDetails(browseId)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(details?.artist?.name ?: "Artista") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (details == null) {
                Text(
                    text = "Impossibile caricare i dettagli dell'artista.",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                val d = details!!
                val allTracks = d.topTracks

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // ---- Hero Banner ----
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        ) {
                            AsyncImage(
                                model = d.artist.avatarUrl,
                                contentDescription = d.artist.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // Gradiente sul banner
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                                            startY = 100f
                                        )
                                    )
                            )
                            // Nome artista in basso
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = d.artist.name,
                                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                d.artist.subscribersText?.let { sub ->
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    // ---- Pulsante Riproduci Mix ----
                    if (allTracks.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { playerManager.playTrack(allTracks.first(), allTracks) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Riproduci")
                                }
                            }
                        }
                    }

                    // ---- Brani più ascoltati ----
                    if (d.topTracks.isNotEmpty()) {
                        item {
                            Text(
                                text = "Brani più ascoltati",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                            )
                        }
                        itemsIndexed(d.topTracks.take(5)) { index, track ->
                            ArtistTrackRow(
                                track = track,
                                index = index + 1,
                                musicRepository = musicRepository,
                                playerManager = playerManager,
                                onNavigateToAlbum = onNavigateToAlbum,
                                onClick = { playerManager.playTrack(track, allTracks) }
                            )
                        }
                    }

                    // ---- Artisti simili ----
                    if (d.similarArtists.isNotEmpty()) {
                        item {
                            Text(
                                text = "Artisti simili",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                items(d.similarArtists) { similarArtist ->
                                    SimilarArtistCard(
                                        artist = similarArtist,
                                        onClick = {
                                            val bid = similarArtist.browseId ?: similarArtist.id
                                            if (bid.isNotBlank()) onNavigateToArtist(bid)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // ---- Album ----
                    if (d.albums.isNotEmpty()) {
                        item {
                            Text(
                                text = "Album",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                items(d.albums) { album ->
                                    AlbumCard(
                                        album = album,
                                        onOpenAlbum = {
                                            val bId = album.browseId ?: album.id
                                            if (bId.isNotBlank()) {
                                                onNavigateToAlbum(bId, album.title, album.artist, album.coverUrl)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // ---- Singoli ed EP ----
                    if (d.singles.isNotEmpty()) {
                        item {
                            Text(
                                text = "Singoli ed EP",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                items(d.singles) { single ->
                                    AlbumCard(
                                        album = single,
                                        onOpenAlbum = {
                                            val bId = single.browseId ?: single.id
                                            if (bId.isNotBlank()) {
                                                onNavigateToAlbum(bId, single.title, single.artist, single.coverUrl)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistTrackRow(
    track: Track,
    index: Int,
    musicRepository: MusicRepository,
    playerManager: PlayerManager,
    onNavigateToAlbum: (browseId: String, title: String, artist: String, cover: String) -> Unit,
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$index",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.width(28.dp)
        )
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            track.viewsText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Riproduci",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Opzioni",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }

    if (showMenu) {
        com.muse.app.ui.components.TrackOptionsBottomSheet(
            track = track,
            musicRepository = musicRepository,
            playerManager = playerManager,
            onDismiss = { showMenu = false },
            onNavigateToAlbum = onNavigateToAlbum
        )
    }
}

@Composable
fun AlbumCard(album: Album, onOpenAlbum: () -> Unit) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable { onOpenAlbum() }
    ) {
        AsyncImage(
            model = album.coverUrl,
            contentDescription = album.title,
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.title,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp
        )
        Text(
            text = listOfNotNull(album.type, album.year).joinToString(" • "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1
        )
    }
}

@Composable
fun SimilarArtistCard(artist: Artist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A2A2A))
        ) {
            AsyncImage(
                model = artist.avatarUrl,
                contentDescription = artist.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        artist.subscribersText?.let { subs ->
            Text(
                text = subs,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.55f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
