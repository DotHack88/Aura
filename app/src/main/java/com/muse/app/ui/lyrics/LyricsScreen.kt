package com.muse.app.ui.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.muse.app.domain.model.LyricsLine
import com.muse.app.player.PlayerManager
import com.muse.app.ui.components.rememberDominantColor
import kotlinx.coroutines.delay

@Composable
fun LyricsScreen(
    playerManager: PlayerManager,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by playerManager.playerState.collectAsState()
    val lyrics by playerManager.currentLyrics.collectAsState()
    val track = state.currentTrack ?: return

    val listState = rememberLazyListState()

    // Colore dominante dalla copertina per sfondo dinamico
    val dominantColor = rememberDominantColor(track.thumbnailUrl)
    val bgColor by animateColorAsState(
        targetValue = if (dominantColor == Color.Transparent)
            Color(0xFF0D0D0D)
        else
            dominantColor,
        animationSpec = tween(durationMillis = 900),
        label = "lyricsBg"
    )

    // Offset di sincronizzazione manuale (utile per video YouTube con intro lunghe rispetto alla traccia Spotify)
    var syncOffset by remember { mutableLongStateOf(0L) }

    // Calcola l'indice della riga attiva in base a state.positionMs e al syncOffset
    val currentLineIndex = remember(state.positionMs, syncOffset, lyrics) {
        val lines = lyrics?.lines.orEmpty()
        if (lines.isEmpty()) -1
        else {
            val adjustedPosition = state.positionMs + syncOffset
            val idx = lines.indexOfLast { it.timestampMs <= adjustedPosition }
            if (idx == -1) 0 else idx
        }
    }

    // Auto-scroll alla riga corrente stile Karaoke / Spotify
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0) {
            listState.animateScrollToItem(
                index = (currentLineIndex - 2).coerceAtLeast(0)
            )
        }
    }

    // Swipe verso il basso per tornare al FullPlayer
    var dragAccumulated by remember { mutableFloatStateOf(0f) }
    val draggableState = rememberDraggableState { delta ->
        dragAccumulated += delta
        if (dragAccumulated > 140f) {
            dragAccumulated = 0f
            onDismiss()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        bgColor,
                        Color(0xFF050505)
                    ),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            )
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStopped = { dragAccumulated = 0f }
            )
    ) {
        // Sfondo sfumato con copertina sfocata (stile Apple Music)
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .graphicsLayer { alpha = 0.12f }
        )

        // Overlay gradiente per leggibilita
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF050505).copy(alpha = 0.6f),
                            Color(0xFF050505)
                        ),
                        startY = 200f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // Header con pill handle + info brano + mini copertina
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Indietro al Player",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "TESTO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 3.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.55f)
                        ),
                        maxLines = 1
                    )
                }

                // Mini copertina arrotondata
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = Color.White.copy(alpha = 0.08f)
            )

            val lines = lyrics?.lines.orEmpty()
            
            // Controlli di sincronizzazione (visibili solo se ci sono testi)
            if (lines.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { syncOffset -= 500L }) {
                        Icon(imageVector = Icons.Default.Remove, contentDescription = "Ritarda", tint = Color.White.copy(alpha = 0.6f))
                    }
                    Text(
                        text = "Sync: ${if (syncOffset > 0) "+" else ""}${syncOffset / 1000.0}s",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.6f))
                    )
                    IconButton(onClick = { syncOffset += 500L }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Anticipa", tint = Color.White.copy(alpha = 0.6f))
                    }
                }
            }

            // Lista Lyrics Karaoke
            val isLoadingLyrics = lyrics == null && state.currentTrack != null

            if (isLoadingLyrics) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Caricamento testo...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            } else if (lines.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Text(text = "\uD83C\uDFB5", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Testo non disponibile",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.8f)
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Il testo di questo brano non è disponibile nella nostra libreria.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.35f)
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    contentPadding = PaddingValues(vertical = 80.dp)
                ) {
                    itemsIndexed(lines) { index, line ->
                        val isCurrent = (index == currentLineIndex)
                        val isPast = index < currentLineIndex

                        val textColor by animateColorAsState(
                            targetValue = when {
                                isCurrent -> Color.White
                                isPast    -> Color.White.copy(alpha = 0.22f)
                                else      -> Color.White.copy(alpha = 0.38f)
                            },
                            animationSpec = tween(400),
                            label = "lineColor_$index"
                        )
                        val textScale by animateFloatAsState(
                            targetValue = if (isCurrent) 1.03f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "lineScale_$index"
                        )

                        Text(
                            text = line.text,
                            style = if (isCurrent) {
                                MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    lineHeight = 38.sp
                                )
                            } else {
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 32.sp
                                )
                            },
                            color = textColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(textScale)
                                .clickable { playerManager.seekTo((line.timestampMs - syncOffset).coerceAtLeast(0)) }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}
