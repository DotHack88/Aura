package com.muse.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
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
import com.muse.app.ui.components.MuseThumbnail
import com.muse.app.data.repository.MusicRepository
import com.muse.app.domain.model.Playlist
import com.muse.app.domain.model.Track
import com.muse.app.player.PlayerManager
import com.muse.app.ui.components.DownloadAllFab
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistId: String,
    musicRepository: MusicRepository,
    playerManager: PlayerManager,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var playlist by remember { mutableStateOf<Playlist?>(null) }
    val tracks by musicRepository.getPlaylistTracks(playlistId).collectAsState(initial = emptyList())
    var isLoading by remember { mutableStateOf(true) }

    // UI States for Menu & Dialogs
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    LaunchedEffect(playlistId) {
        isLoading = true
        playlist = musicRepository.getPlaylist(playlistId)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "Playlist") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opzioni playlist")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rinomina") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                newPlaylistName = playlist?.name ?: ""
                                showRenameDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Elimina") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (playlist == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("Playlist non trovata", color = MaterialTheme.colorScheme.error)
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // Header (Copertina e Titolo)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MuseThumbnail(
                            url = playlist!!.coverUrl,
                            contentDescription = playlist!!.name,
                            size = 200.dp,
                            shape = RoundedCornerShape(16.dp),
                            fallbackIcon = androidx.compose.material.icons.Icons.Default.QueueMusic
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = playlist!!.name,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${tracks.size} brani",
                            style = MaterialTheme.typography.bodyLarge.copy(color = Color.Gray)
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                        
                        if (tracks.isNotEmpty()) {
                            Button(
                                onClick = { playerManager.playTrack(tracks.first(), tracks) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(32.dp),
                                modifier = Modifier
                                    .height(56.dp)
                                    .fillMaxWidth(0.6f)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Riproduci")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Riproduci", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            DownloadAllFab(
                                onClick = {
                                    tracks.forEach { t -> musicRepository.offlineManager?.startDownload(t) }
                                }
                            )
                        }
                    }
                }

                // Lista Brani
                if (tracks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Nessun brano in questa playlist",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    itemsIndexed(tracks) { index, track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { playerManager.playTrack(track, tracks) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                                modifier = Modifier.width(32.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            MuseThumbnail(
                                url = track.thumbnailUrl,
                                contentDescription = track.title,
                                size = 48.dp
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
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

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rinomina playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Nome") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            coroutineScope.launch {
                                musicRepository.renamePlaylist(playlistId, newPlaylistName)
                                playlist = playlist?.copy(name = newPlaylistName)
                                showRenameDialog = false
                            }
                        }
                    }
                ) {
                    Text("Salva")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Elimina playlist") },
            text = { Text("Sei sicuro di voler eliminare questa playlist? L'azione non può essere annullata.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            musicRepository.deletePlaylist(playlistId)
                            showDeleteDialog = false
                            onNavigateBack() // Torna alla libreria
                        }
                    }
                ) {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }
}
