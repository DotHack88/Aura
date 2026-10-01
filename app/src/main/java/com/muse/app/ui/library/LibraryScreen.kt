package com.muse.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Image
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.platform.LocalContext
import com.muse.app.utils.CoverUtils
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.rememberLazyListState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.ExperimentalFoundationApi
import coil.compose.AsyncImage
import com.muse.app.ui.components.MuseArtistThumbnail
import com.muse.app.ui.components.MuseThumbnail
import com.muse.app.ui.components.FavoriteBurstButton
import com.muse.app.data.local.download.DownloadProgress
import com.muse.app.data.local.download.OfflineManager
import com.muse.app.data.repository.MusicRepository
import com.muse.app.player.PlayerManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    musicRepository: MusicRepository,
    playerManager: PlayerManager,
    onNavigateToPlaylist: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Preferiti", "Playlist", "Artisti", "Cronologia", "Download")

    val favorites by musicRepository.getFavoriteTracks().collectAsState(initial = emptyList())
    val playlists by musicRepository.getPlaylists().collectAsState(initial = emptyList())
    val followedArtists by musicRepository.getFollowedArtists().collectAsState(initial = emptyList())
    val recents by musicRepository.getRecentTracks().collectAsState(initial = emptyList())
    val downloads by musicRepository.getDownloadedTracks().collectAsState(initial = emptyList())
    val activeDownloads by musicRepository.getActiveDownloads().collectAsState(initial = emptyList())

    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var playlistForCoverChange by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && playlistForCoverChange != null) {
            val plId = playlistForCoverChange!!
            coroutineScope.launch {
                val savedPath = CoverUtils.savePlaylistCoverLocally(context, plId, uri)
                if (savedPath != null) {
                    musicRepository.updatePlaylistCover(plId, savedPath)
                }
                playlistForCoverChange = null
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 16.dp)
    ) {
        // Titolo & Pulsante Crea Playlist
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "La tua Libreria",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )

            IconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Nuova Playlist")
            }
        }

        // Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 16.dp,
            divider = {}, // Rimuove la linea sotto i tab per un look più pulito
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        // Contenuto Tabs
        when (selectedTab) {
            0 -> {
                // Preferiti
                if (favorites.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Non hai ancora aggiunto brani preferiti", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(favorites) { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { playerManager.playTrack(track, favorites) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MuseThumbnail(
                                    url = track.thumbnailUrl,
                                    contentDescription = track.title,
                                    size = 56.dp
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(track.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(track.artist, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                FavoriteBurstButton(
                                    isFavorite = true,
                                    onClick = {
                                        coroutineScope.launch { musicRepository.toggleFavorite(track) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            1 -> {
                // Playlist
                if (playlists.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nessuna playlist creata. Premi + in alto per crearne una!", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                } else {
                    var localPlaylists by remember(playlists) { mutableStateOf(playlists) }
                    val lazyListState = rememberLazyListState()
                    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                        localPlaylists = localPlaylists.toMutableList().apply {
                            add(to.index, removeAt(from.index))
                        }
                    }
                    LaunchedEffect(reorderableState.isAnyItemDragging) {
                        if (!reorderableState.isAnyItemDragging && localPlaylists != playlists) {
                            musicRepository.reorderPlaylists(localPlaylists)
                        }
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(localPlaylists, key = { it.id }) { playlist ->
                            ReorderableItem(reorderableState, key = playlist.id) { isDragging ->
                                val elevation = if (isDragging) 8.dp else 0.dp
                                var showMenu by remember { mutableStateOf(false) }

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color.Transparent,
                                    shadowElevation = elevation
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNavigateToPlaylist(playlist.id) }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        MuseThumbnail(
                                            url = playlist.coverUrl,
                                            contentDescription = playlist.name,
                                            size = 56.dp,
                                            shape = RoundedCornerShape(8.dp),
                                            fallbackIcon = Icons.Default.QueueMusic
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                                            modifier = Modifier.weight(1f)
                                        )
                                        
                                        // Menu a tre puntini
                                        Box {
                                            IconButton(onClick = { showMenu = true }) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "Opzioni")
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
                                                        selectedPlaylistId = playlist.id
                                                        newPlaylistName = playlist.name
                                                        showRenameDialog = true
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Cambia copertina") },
                                                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                                    onClick = {
                                                        showMenu = false
                                                        playlistForCoverChange = playlist.id
                                                        photoPickerLauncher.launch(
                                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                        )
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Elimina") },
                                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                                    onClick = {
                                                        showMenu = false
                                                        selectedPlaylistId = playlist.id
                                                        showDeleteDialog = true
                                                    }
                                                )
                                            }
                                        }
                                        
                                        // Maniglia di trascinamento
                                        IconButton(
                                            modifier = Modifier.draggableHandle(
                                                onDragStarted = { },
                                                onDragStopped = { }
                                            ),
                                            onClick = {}
                                        ) {
                                            Icon(Icons.Default.DragHandle, contentDescription = "Reorder")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                // Artisti
                if (followedArtists.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Non segui ancora nessun artista", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(followedArtists) { artist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToArtist(artist.id) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MuseArtistThumbnail(
                                    url = artist.avatarUrl.ifEmpty { null },
                                    contentDescription = artist.name,
                                    size = 56.dp
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = artist.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    if (!artist.handle.isNullOrEmpty()) {
                                        Text(
                                            text = artist.handle,
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            3 -> {
                // Cronologia
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recents) { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { playerManager.playTrack(track, recents) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MuseThumbnail(
                                url = track.thumbnailUrl,
                                contentDescription = track.title,
                                size = 56.dp
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            4 -> {
                // Download offline
                if (downloads.isEmpty() && activeDownloads.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Nessun brano in download o scaricato",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                            Text(
                                "Tieni premuto un brano per scaricarlo",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Sezione in download
                        if (activeDownloads.isNotEmpty()) {
                            item {
                                Text(
                                    "In download",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                                )
                            }
                            items(activeDownloads) { activeDownload ->
                                val track = activeDownload.track
                                val progress = activeDownload.progress
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MuseThumbnail(
                                        url = track.thumbnailUrl,
                                        contentDescription = track.title,
                                        size = 56.dp
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                         Column(modifier = Modifier.weight(1f)) {
                                            Text(track.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(track.artist, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            
                                            Spacer(modifier = Modifier.height(4.dp))
                                            
                                            when (progress) {
                                                is DownloadProgress.Preparing -> {
                                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                                }
                                                is DownloadProgress.Downloading -> {
                                                    LinearProgressIndicator(
                                                        progress = progress.percent / 100f,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                                is DownloadProgress.Error -> {
                                                    Text(
                                                        "Download interrotto",
                                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                else -> {}
                                            }
                                        }
                                        // Pulsante Riprova visibile solo in caso di errore
                                        if (progress is DownloadProgress.Error) {
                                            IconButton(onClick = { musicRepository.startDownload(track) }) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "Riprova download",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        // Pulsante Annulla sempre visibile
                                        IconButton(onClick = {
                                            musicRepository.offlineManager?.cancelDownload(track.id)
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Annulla download",
                                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                            )
                                        }
                                }
                            }
                        }

                        // Sezione completati
                        if (downloads.isNotEmpty()) {
                            if (activeDownloads.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                            item {
                                Text(
                                    "Completati",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                                )
                            }
                        items(downloads) { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { playerManager.playTrack(track, downloads) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MuseThumbnail(
                                    url = track.thumbnailUrl,
                                    contentDescription = track.title,
                                    size = 56.dp
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(track.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(track.artist, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = {
                                    coroutineScope.launch { musicRepository.removeDownload(track.id) }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Rimuovi download",
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
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

    // Dialog Nuova Playlist
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Nuova Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Nome Playlist") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            coroutineScope.launch {
                                musicRepository.createPlaylist(newPlaylistName.trim())
                                newPlaylistName = ""
                                showCreateDialog = false
                            }
                        }
                    }
                ) {
                    Text("Crea")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }

    // Dialog Rinomina Playlist
    if (showRenameDialog && selectedPlaylistId != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rinomina Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Nome Playlist") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            coroutineScope.launch {
                                musicRepository.renamePlaylist(selectedPlaylistId!!, newPlaylistName.trim())
                                showRenameDialog = false
                                selectedPlaylistId = null
                            }
                        }
                    }
                ) {
                    Text("Salva")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showRenameDialog = false 
                    selectedPlaylistId = null
                }) {
                    Text("Annulla")
                }
            }
        )
    }

    // Dialog Elimina Playlist
    if (showDeleteDialog && selectedPlaylistId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Elimina Playlist") },
            text = { Text("Sei sicuro di voler eliminare questa playlist? L'azione non può essere annullata.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            musicRepository.deletePlaylist(selectedPlaylistId!!)
                            showDeleteDialog = false
                            selectedPlaylistId = null
                        }
                    }
                ) {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteDialog = false 
                    selectedPlaylistId = null
                }) {
                    Text("Annulla")
                }
            }
        )
    }
}
