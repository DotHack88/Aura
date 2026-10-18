package com.muse.app.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Download
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
import com.muse.app.ui.components.FavoriteBurstButton
import kotlinx.coroutines.launch
import com.muse.app.domain.model.Track
import com.muse.app.player.PlayerManager
import com.muse.app.ui.components.DownloadAllFab

import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Pause
import com.muse.app.ui.components.EqualizerBars
import com.muse.app.ui.components.rememberAccentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    browseId: String,
    albumTitle: String,
    albumArtist: String,
    albumCoverUrl: String,
    musicRepository: MusicRepository,
    playerManager: PlayerManager,
    onBack: () -> Unit,
    onNavigateToSearch: (String) -> Unit = {}
) {
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    val scope = rememberCoroutineScope()
    
    val playerState by playerManager.playerState.collectAsState()
    val currentTrack = playerState.currentTrack
    val isPlayerPlaying = playerState.isPlaying

    val currentCover = currentTrack?.thumbnailUrl?.ifBlank { albumCoverUrl } ?: albumCoverUrl
    val rawAccent = rememberAccentColor(currentCover)
    val highlightColor = if (rawAccent == Color.Transparent) MaterialTheme.colorScheme.primary else rawAccent

    val favoriteTracks by musicRepository.getFavoriteTracks().collectAsState(initial = emptyList())
    val favoriteIds = favoriteTracks.map { it.id }.toSet()

    LaunchedEffect(browseId) {
        isLoading = true
        tracks = musicRepository.getAlbumTracks(browseId, albumTitle, albumArtist, albumCoverUrl)
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030303))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // ---- Hero Cover ----
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    AsyncImage(
                        model = albumCoverUrl,
                        contentDescription = albumTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Gradient overlay bottom
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xFF030303)),
                                    startY = 150f
                                )
                            )
                    )
                    // Back button
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopStart)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Indietro",
                            tint = Color.White
                        )
                    }
                    // Album title + artist at bottom of hero
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = albumTitle,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = albumArtist,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        )
                    }
                }
            }

            // ---- Play All & Actions ----
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isLoading) "Caricamento..." else "${tracks.size} brani",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    
                    if (!isLoading && tracks.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { tracks.forEach { t -> musicRepository.offlineManager?.startDownload(t) } },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Scarica tutto",
                                    tint = Color(0xFF9B59B6)
                                )
                            }
                            
                            IconButton(
                                onClick = { playerManager.playShuffled(tracks) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Mescola",
                                    tint = Color.White
                                )
                            }
                            
                            val isThisAlbumPlaying = isPlayerPlaying && tracks.any { it.id == currentTrack?.id }
                            FloatingActionButton(
                                onClick = {
                                    if (isThisAlbumPlaying) {
                                        playerManager.togglePlayPause()
                                    } else {
                                        playerManager.playTrack(tracks.first(), tracks)
                                    }
                                },
                                containerColor = Color(0xFF6200EE),
                                contentColor = Color.White,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isThisAlbumPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isThisAlbumPlaying) "Pausa" else "Riproduci tutto",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ---- Loading indicator ----
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF6200EE))
                    }
                }
            }

            // ---- Track list ----
            val SpotifyGreen = Color(0xFF1DB954)
            itemsIndexed(tracks) { index, track ->
                val isCurrent = currentTrack != null && (
                    currentTrack.id == track.id ||
                    (currentTrack.title.trim().equals(track.title.trim(), ignoreCase = true) &&
                     (currentTrack.artist.trim().equals(track.artist.trim(), ignoreCase = true) ||
                      track.artist.isBlank() ||
                      albumArtist.contains(currentTrack.artist, ignoreCase = true) ||
                      currentTrack.artist.contains(albumArtist, ignoreCase = true)))
                )
                val isTrackPlaying = isCurrent && isPlayerPlaying

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isCurrent) {
                                playerManager.togglePlayPause()
                            } else {
                                playerManager.playTrack(track, tracks)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrent) SpotifyGreen else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.width(28.dp)
                    )
                    AsyncImage(
                        model = track.thumbnailUrl.ifBlank { albumCoverUrl },
                        contentDescription = track.title,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isCurrent) {
                                EqualizerBars(
                                    isPlaying = isTrackPlaying,
                                    color = SpotifyGreen,
                                    barWidth = 3.dp,
                                    maxHeight = 14.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isCurrent) SpotifyGreen else Color.White
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = track.artist.ifBlank { albumArtist },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.55f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val isFav = favoriteIds.contains(track.id)
                    FavoriteBurstButton(
                        isFavorite = isFav,
                        onClick = {
                            scope.launch { musicRepository.toggleFavorite(track) }
                        }
                    )
                    IconButton(
                        onClick = { selectedTrackForMenu = track },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opzioni",
                            tint = Color(0xFF888888),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 60.dp, end = 16.dp),
                    color = Color.White.copy(alpha = 0.07f),
                    thickness = 0.5.dp
                )
            }
        }
    }

    selectedTrackForMenu?.let { trk ->
        com.muse.app.ui.components.TrackOptionsBottomSheet(
            track = trk,
            musicRepository = musicRepository,
            playerManager = playerManager,
            onDismiss = { selectedTrackForMenu = null },
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToAlbum = null // Siamo già nell'album, nascondiamo il pulsante
        )
    }
}
