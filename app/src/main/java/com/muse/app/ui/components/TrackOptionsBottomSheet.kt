package com.muse.app.ui.components

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.muse.app.data.local.download.DownloadProgress
import com.muse.app.data.repository.MusicRepository
import com.muse.app.domain.model.Playlist
import com.muse.app.domain.model.Track
import com.muse.app.player.PlayerManager
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// TrackOptionsMenu - Bottom Sheet contestuale per un brano
// Usato in: SearchScreen, ArtistScreen, HomeScreen, FullPlayerScreen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsBottomSheet(
    track: Track,
    musicRepository: MusicRepository,
    playerManager: PlayerManager,
    onDismiss: () -> Unit,
    showAddToQueue: Boolean = true,
    onNavigateToSearch: ((String) -> Unit)? = null,
    onNavigateToAlbum: ((browseId: String, title: String, artist: String, cover: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Stato per la sotto-schermata "aggiungi a playlist"
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showAdvancedShare by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var isSearchingAlbum by remember { mutableStateOf(false) }
    // Stato per navigazione differita: viene impostato dal coroutine e consumato dal LaunchedEffect
    var pendingAlbumNav by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    // Stato download
    var isDownloaded by remember { mutableStateOf(musicRepository.isDownloaded(track.id)) }
    var downloadProgress by remember { mutableStateOf<Int?>(null) } // null = non in download, 0-100 = in corso

    LaunchedEffect(track.id) {
        musicRepository.getActiveDownloadProgress(track.id).collect { progress ->
            when (progress) {
                is DownloadProgress.Preparing -> downloadProgress = 0
                is DownloadProgress.Downloading -> downloadProgress = progress.percent
                is DownloadProgress.Done -> {
                    downloadProgress = null
                    isDownloaded = true
                    snackbarMessage = "\"${track.title}\" scaricato!"
                }
                is DownloadProgress.Error -> {
                    downloadProgress = null
                    snackbarMessage = "Errore: ${progress.message}"
                }
                null -> downloadProgress = null
            }
        }
    }

    LaunchedEffect(Unit) {
        musicRepository.getPlaylists().collect { playlists = it }
    }

    // (Rimosso LaunchedEffect per navigazione, la eseguiamo direttamente sul Main thread)

    // Mostra AdvancedShareBottomSheet se richiesto
    if (showAdvancedShare) {
        AdvancedShareBottomSheet(
            track = track,
            playerManager = playerManager,
            onDismiss = { showAdvancedShare = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        // Snackbar di conferma
        snackbarMessage?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(2000)
                snackbarMessage = null
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(msg, color = Color.White, fontSize = 13.sp)
            }
        }

        if (!showPlaylistPicker) {
            // --- Schermata principale menu ---
            Column(modifier = Modifier.padding(bottom = 32.dp)) {

                // Header brano
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = track.title,
                        modifier = Modifier
                            .size(52.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            ),
                            maxLines = 1
                        )
                    }
                }

                Divider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(8.dp))

                // Opzione: Riproduci subito
                MenuOption(
                    icon = Icons.Default.PlayArrow,
                    label = "Riproduci subito"
                ) {
                    playerManager.playTrack(track)
                    onDismiss()
                }

                // Opzione: Aggiungi a coda
                if (showAddToQueue) {
                    MenuOption(
                        icon = Icons.Default.QueueMusic,
                        label = "Aggiungi alla coda"
                    ) {
                        playerManager.addToQueue(track)
                        snackbarMessage = "\"${track.title}\" aggiunto alla coda"
                    }
                }

                // Opzione: Aggiungi ai preferiti
                MenuOption(
                    icon = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    label = if (track.isFavorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                    tint = if (track.isFavorite) Color(0xFFE53935) else null
                ) {
                    scope.launch {
                        musicRepository.toggleFavorite(track)
                        snackbarMessage = if (!track.isFavorite) "Aggiunto ai preferiti ♡" else "Rimosso dai preferiti"
                    }
                }

                // Opzione: Aggiungi a playlist
                MenuOption(
                    icon = Icons.Default.PlaylistAdd,
                    label = "Aggiungi a playlist"
                ) {
                    showPlaylistPicker = true
                }

                // Opzione: Vai all'artista
                if (onNavigateToSearch != null) {
                    MenuOption(
                        icon = Icons.Default.Person,
                        label = "Vai all'artista"
                    ) {
                        onNavigateToSearch(track.artist)
                        onDismiss()
                    }
                }

                // Opzione: Vai all'album — sempre visibile se il menu ha supporto navigazione
                if (onNavigateToAlbum != null || onNavigateToSearch != null) {
                    MenuOption(
                        icon = if (isSearchingAlbum) Icons.Default.HourglassEmpty else Icons.Default.Album,
                        label = if (isSearchingAlbum) "Ricerca album..." else "Vai all'album"
                    ) {
                        if (isSearchingAlbum) return@MenuOption
                        when {
                            // Caso 1: abbiamo il browseId diretto → AlbumScreen
                            onNavigateToAlbum != null && !track.albumBrowseId.isNullOrBlank() -> {
                                scope.launch(Dispatchers.Main) {
                                    onNavigateToAlbum(
                                        track.albumBrowseId!!,
                                        track.album ?: track.title,
                                        track.artist,
                                        track.thumbnailUrl
                                    )
                                    onDismiss()
                                }
                            }
                            // Caso 2: albumBrowseId mancante → cerca album su YTM e naviga
                            onNavigateToAlbum != null -> {
                                isSearchingAlbum = true
                                scope.launch {
                                    try {
                                        var bestAlbum: com.muse.app.domain.model.Album? = null

                                        Log.d("AlbumNav", "Starting album search. track.album=${track.album} track.artist=${track.artist} track.title=${track.title}")

                                        // Tentativo 1: cerca per "Nome Album Artista" se il nome album è noto
                                        if (!track.album.isNullOrBlank()) {
                                            val results = musicRepository.searchAlbums("${track.album} ${track.artist}")
                                            Log.d("AlbumNav", "Attempt 1 (album+artist): ${results.size} results")
                                            bestAlbum = results.firstOrNull()
                                        }

                                        // Tentativo 2: searchAll con "Titolo Brano Artista" → guarda nella lista albums
                                        if (bestAlbum == null) {
                                            val allResults = musicRepository.searchAll("${track.title} ${track.artist}")
                                            Log.d("AlbumNav", "Attempt 2 (searchAll): ${allResults.albums.size} albums")
                                            bestAlbum = allResults.albums.firstOrNull()
                                        }

                                        // Tentativo 3: searchAlbums con "Titolo Artista"
                                        if (bestAlbum == null) {
                                            val results = musicRepository.searchAlbums("${track.title} ${track.artist}")
                                            Log.d("AlbumNav", "Attempt 3 (searchAlbums title+artist): ${results.size} results")
                                            bestAlbum = results.firstOrNull()
                                        }

                                        // Tentativo 4: searchAlbums solo con il nome artista
                                        if (bestAlbum == null && track.artist.isNotBlank()) {
                                            val results = musicRepository.searchAlbums(track.artist)
                                            Log.d("AlbumNav", "Attempt 4 (artist only): ${results.size} results")
                                            bestAlbum = results.firstOrNull()
                                        }

                                        Log.d("AlbumNav", "Best album found: ${bestAlbum?.title} id=${bestAlbum?.id}")

                                        if (bestAlbum != null) {
                                            withContext(Dispatchers.Main) {
                                                Log.d("AlbumNav", "Invoking onNavigateToAlbum directly from coroutine")
                                                onNavigateToAlbum(bestAlbum.id, bestAlbum.title, bestAlbum.artist, track.thumbnailUrl)
                                                onDismiss()
                                            }
                                        } else {
                                            snackbarMessage = "Album non trovato"
                                        }
                                    } catch (e: Exception) {
                                        Log.e("AlbumNav", "Exception during album search", e)
                                        snackbarMessage = "Errore nella ricerca album"
                                    } finally {
                                        isSearchingAlbum = false
                                    }
                                }
                            }
                            // Caso 3: fallback alla ricerca testuale
                            onNavigateToSearch != null -> {
                                val query = if (!track.album.isNullOrBlank())
                                    "${track.album} ${track.artist}"
                                else
                                    "${track.title} ${track.artist} album"
                                onNavigateToSearch(query)
                                onDismiss()
                            }
                            else -> { /* noop */ }
                        }
                    }
                }

                // Opzione: Condividi
                MenuOption(
                    icon = Icons.Default.Share,
                    label = "Condividi"
                ) {
                    showAdvancedShare = true
                }

                // Opzione: Download / Rimuovi download
                if (downloadProgress != null) {
                    // Download in corso: mostra barra progresso
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Downloading,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Download in corso... ${downloadProgress}%",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (downloadProgress ?: 0) / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else if (isDownloaded) {
                    MenuOption(
                        icon = Icons.Default.DownloadDone,
                        label = "Rimuovi download",
                        tint = MaterialTheme.colorScheme.primary
                    ) {
                        scope.launch {
                            musicRepository.removeDownload(track.id)
                            isDownloaded = false
                            snackbarMessage = "Download rimosso"
                        }
                    }
                } else {
                    MenuOption(
                        icon = Icons.Default.Download,
                        label = "Scarica per ascolto offline"
                    ) {
                        musicRepository.startDownload(track)
                    }
                }
            }

        } else {
            // --- Sotto-schermata: selezione playlist ---
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showPlaylistPicker = false }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Indietro")
                    }
                    Text(
                        text = "Aggiungi a playlist",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Divider(modifier = Modifier.padding(horizontal = 16.dp))

                // Crea nuova playlist
                MenuOption(
                    icon = Icons.Default.Add,
                    label = "Crea nuova playlist"
                ) {
                    showNewPlaylistDialog = true
                }

                // Lista playlist esistenti
                playlists.forEach { playlist ->
                    MenuOption(
                        icon = Icons.Default.QueueMusic,
                        label = playlist.name
                    ) {
                        scope.launch {
                            musicRepository.addTrackToPlaylist(playlist.id, track)
                            snackbarMessage = "Aggiunto a \"${playlist.name}\""
                            showPlaylistPicker = false
                        }
                    }
                }
            }
        }
    }

    // Dialog crea nuova playlist
    if (showNewPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showNewPlaylistDialog = false },
            title = { Text("Nuova playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Nome playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            scope.launch {
                                val playlistId = musicRepository.createPlaylist(newPlaylistName)
                                musicRepository.addTrackToPlaylist(playlistId, track)
                                snackbarMessage = "Aggiunto a \"$newPlaylistName\""
                                newPlaylistName = ""
                                showNewPlaylistDialog = false
                                showPlaylistPicker = false
                            }
                        }
                    }
                ) { Text("Crea") }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlaylistDialog = false }) { Text("Annulla") }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Riga del menu (icona + label)
// ---------------------------------------------------------------------------
@Composable
private fun MenuOption(
    icon: ImageVector,
    label: String,
    tint: Color? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

// ---------------------------------------------------------------------------
// Helper condivisione link YouTube
// ---------------------------------------------------------------------------
fun shareTrack(context: Context, track: Track) {
    val url = "https://music.youtube.com/watch?v=${track.id}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "${track.title} - ${track.artist}")
        putExtra(Intent.EXTRA_TEXT, "🎵 ${track.title} di ${track.artist}\n$url")
    }
    context.startActivity(Intent.createChooser(intent, "Condividi brano"))
}
