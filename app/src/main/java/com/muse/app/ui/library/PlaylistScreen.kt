package com.muse.app.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muse.app.data.repository.MusicRepository
import com.muse.app.domain.model.Playlist
import com.muse.app.domain.model.Track
import com.muse.app.player.PlayerManager
import com.muse.app.ui.components.DownloadAllFab
import com.muse.app.ui.components.MuseThumbnail
import com.muse.app.ui.components.TrackOptionsBottomSheet
import com.muse.app.utils.CoverUtils
import com.muse.app.utils.toHighResThumbnail
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistId: String,
    musicRepository: MusicRepository,
    playerManager: PlayerManager,
    onNavigateBack: () -> Unit,
    onNavigateToSearch: ((String) -> Unit)? = null,
    onNavigateToAlbum: ((browseId: String, title: String, artist: String, cover: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val playlist by musicRepository.getPlaylistFlow(playlistId).collectAsState(initial = null)
    val tracks by musicRepository.getPlaylistTracks(playlistId).collectAsState(initial = emptyList())

    // UI States for Menu & Dialogs
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCoverOptionsDialog by remember { mutableStateOf(false) }
    var showTrackCoverPickerDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val savedPath = CoverUtils.savePlaylistCoverLocally(context, playlistId, uri)
                if (savedPath != null) {
                    musicRepository.updatePlaylistCover(playlistId, savedPath)
                    snackbarHostState.showSnackbar("Copertina aggiornata dalla galleria!")
                }
            }
        }
    }

    // Copertina: usa cover personalizzata se presente, altrimenti copertina della prima traccia come fallback
    val effectiveCover = playlist?.coverUrl?.takeIf { it.isNotBlank() } ?: tracks.firstOrNull()?.thumbnailUrl

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            text = { Text("Cambia copertina") },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showCoverOptionsDialog = true
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
        if (playlist == null && tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
                        // Copertina con indicatore interattivo per modifica
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showCoverOptionsDialog = true }
                        ) {
                            MuseThumbnail(
                                url = effectiveCover,
                                contentDescription = playlist?.name,
                                size = 200.dp,
                                shape = RoundedCornerShape(16.dp),
                                fallbackIcon = Icons.Default.QueueMusic
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Modifica copertina",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Modifica",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = playlist?.name ?: "Playlist",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${tracks.size} ${if (tracks.size == 1) "brano" else "brani"}",
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
                    itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                    coroutineScope.launch {
                                        musicRepository.removeTrackFromPlaylist(playlistId, track.id)
                                        snackbarHostState.showSnackbar("\"${track.title}\" rimosso dalla playlist")
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromEndToStart = true,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                val color by animateColorAsState(
                                    targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                                        MaterialTheme.colorScheme.errorContainer
                                    else Color.Transparent,
                                    label = "dismissColor"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Rimuovi dalla playlist",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                modifier = Modifier.fillMaxWidth()
                            ) {
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
                                    IconButton(
                                        onClick = { selectedTrackForMenu = track },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Opzioni brano",
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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

    // Bottom Sheet opzioni brano (con Rimuovi dalla playlist)
    selectedTrackForMenu?.let { trk ->
        TrackOptionsBottomSheet(
            track = trk,
            musicRepository = musicRepository,
            playerManager = playerManager,
            onDismiss = { selectedTrackForMenu = null },
            playlistId = playlistId,
            onRemoveFromPlaylist = {
                coroutineScope.launch {
                    musicRepository.removeTrackFromPlaylist(playlistId, trk.id)
                    snackbarHostState.showSnackbar("\"${trk.title}\" rimosso dalla playlist")
                }
            },
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToAlbum = onNavigateToAlbum
        )
    }

    // Dialog opzioni copertina
    if (showCoverOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showCoverOptionsDialog = false },
            title = { Text("Copertina playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showCoverOptionsDialog = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Scegli dalla galleria")
                        }
                    }

                    if (tracks.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                showCoverOptionsDialog = false
                                showTrackCoverPickerDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.QueueMusic, contentDescription = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Usa copertina di un brano")
                            }
                        }
                    }

                    if (playlist?.coverUrl != null) {
                        TextButton(
                            onClick = {
                                showCoverOptionsDialog = false
                                coroutineScope.launch {
                                    musicRepository.updatePlaylistCover(playlistId, null)
                                    snackbarHostState.showSnackbar("Copertina predefinita ripristinata")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Ripristina copertina automatica")
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCoverOptionsDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }

    // Dialog selezione copertina da un brano della playlist
    if (showTrackCoverPickerDialog) {
        AlertDialog(
            onDismissRequest = { showTrackCoverPickerDialog = false },
            title = { Text("Seleziona copertina brano") },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    items(tracks) { trk ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showTrackCoverPickerDialog = false
                                    coroutineScope.launch {
                                        val highRes = trk.thumbnailUrl.toHighResThumbnail()
                                        musicRepository.updatePlaylistCover(playlistId, highRes)
                                        snackbarHostState.showSnackbar("Copertina aggiornata!")
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MuseThumbnail(url = trk.thumbnailUrl, contentDescription = trk.title, size = 44.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(trk.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(trk.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTrackCoverPickerDialog = false }) {
                    Text("Annulla")
                }
            }
        )
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
