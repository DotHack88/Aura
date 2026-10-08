package com.muse.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.muse.app.data.repository.MusicRepository
import com.muse.app.domain.model.Track
import com.muse.app.player.PlayerManager
import com.muse.app.ui.components.shimmerEffect
import kotlinx.coroutines.launch

/**
 * Mappa ogni mood/genere alla query specifica da usare su YouTube Music.
 * Non si fa una semplice ricerca testuale della parola (es. "Relax"),
 * ma una query contestualizzata che restituisce mix e playlist adatti,
 * replicando il comportamento dei chip di YouTube Music.
 */
private val moodQueryMap = mapOf(
    "Energica"          to "musica energica pump up mix 2024",
    "Relax"             to "musica relax chill lofi mix 2024",
    "Allenamento"       to "musica allenamento workout gym mix 2024",
    "Concentrazione"    to "musica concentrazione studio focus lofi 2024",
    "Viaggio"           to "musica viaggio road trip playlist 2024",
    "Festa"             to "musica festa party dance mix 2024",
    "Triste"            to "musica triste malinconica sad playlist 2024",
    "Romantico"         to "musica romantica amore love songs 2024",
    "Dormire"           to "musica dormire sleep calming music 2024",
    "Sentirsi bene"     to "musica buonumore feel good pop hits 2024",
    "Pop"               to "pop hits 2024 playlist",
    "Hip Hop"           to "hip hop rap mix 2024",
    "Rock"              to "rock hits playlist 2024",
    "R&B"               to "r&b soul mix 2024",
    "Elettronica/Dance" to "electronic dance music edm mix 2024",
    "Indie"             to "indie alternative music playlist 2024",
    "Latina"            to "musica latina reggaeton mix 2024",
    "K-Pop"             to "k-pop hits playlist 2024",
    "Classica"          to "classical music playlist best",
    "Jazz"              to "jazz music playlist relax 2024",
    "Metal"             to "metal rock heavy playlist 2024",
    "Folk"              to "folk acoustic music playlist 2024",
    "Reggae"            to "reggae music playlist 2024",
    "Blues"             to "blues music playlist 2024"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodScreen(
    moodName: String,
    musicRepository: MusicRepository,
    playerManager: PlayerManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(moodName) {
        isLoading = true
        val query = moodQueryMap[moodName] ?: "$moodName music mix playlist"
        tracks = musicRepository.searchTracks(query)
        isLoading = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = Color(0xFF030303),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = moodName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Indietro",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212)
                )
            )
        }
    ) { paddingValues ->
        when {
            isLoading -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(12) {
                        MoodTrackShimmer()
                    }
                }
            }
            tracks.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nessun brano trovato per \"$moodName\"",
                        color = Color(0xFF9E9E9E),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${tracks.size} brani",
                                color = Color(0xFF9E9E9E),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = { playerManager.playTrack(tracks.first(), tracks) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Riproduci", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    items(tracks) { track ->
                        MoodTrackItem(
                            track = track,
                            onClick = { playerManager.playTrack(track, tracks) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodTrackItem(
    track: Track,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center
        ) {
            if (track.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFF9E9E9E),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                color = Color(0xFF9E9E9E),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MoodTrackShimmer() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(6.dp))
                .shimmerEffect()
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
        }
    }
}

