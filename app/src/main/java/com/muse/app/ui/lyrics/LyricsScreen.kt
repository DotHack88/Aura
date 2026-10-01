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
import androidx.compose.ui.draw.blur
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
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.muse.app.domain.model.LyricsLine
import com.muse.app.player.PlayerManager
import androidx.compose.material.icons.filled.Equalizer
import com.muse.app.ui.components.EqualizerBottomSheet
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
    var showEqualizer by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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

    // Offset di sincronizzazione manuale: default -1.0s (la maggior parte dei testi risulta
    // leggermente in anticipo rispetto al segnale audio; l'utente può regolarlo con +/-)
    var syncOffset by remember { mutableLongStateOf(-1000L) }

    // Calcola l'indice della riga attiva in base a state.positionMs e al syncOffset
    val currentLineIndex = remember(state.positionMs, syncOffset, lyrics) {
        val lines = lyrics?.lines.orEmpty()
        if (lines.isEmpty()) -1
        else {
            // syncOffset è già il valore esatto da sommare (senza offset fisso nascosto)
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
        // Sfondo sfocato: copertina a pieno schermo con heavy blur (stile Apple Music)
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = 55.dp)
                .graphicsLayer { scaleX = 1.3f; scaleY = 1.3f }
        )
        // Overlay scuro + gradiente per leggibilità
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.60f),
                            Color.Black.copy(alpha = 0.85f)
                        )
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

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
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
                    modifier = Modifier.weight(1f).padding(end = 32.dp) // padding per bilanciare il bottone indietro
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

                // Pulsante Equalizzatore (Bug #1 fix)
                IconButton(onClick = { showEqualizer = true }) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = "Equalizzatore",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = Color.White.copy(alpha = 0.08f)
            )

            // Copertina in evidenza
            if (!isLandscape) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = "Copertina",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            }

            val lines = lyrics?.lines.orEmpty()
            
            // Controlli di sincronizzazione (visibili solo se ci sono testi)
            if (lines.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
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
                        .padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp)
                ) {
                    itemsIndexed(lines) { index, line ->
                        val isCurrent = (index == currentLineIndex)
                        val isPast = index < currentLineIndex

                        val textColor by animateColorAsState(
                            targetValue = when {
                                isCurrent -> Color.White
                                isPast    -> Color.White.copy(alpha = 0.20f)
                                else      -> Color.White.copy(alpha = 0.40f)
                            },
                            animationSpec = tween(350),
                            label = "lineColor_$index"
                        )
                        
                        val textScale by animateFloatAsState(
                            targetValue = when {
                                isCurrent -> 1.0f
                                isPast    -> 0.90f
                                else      -> 0.95f
                            },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "lineScale_$index"
                        )
                        
                        val blurRadius by animateFloatAsState(
                            targetValue = when {
                                isCurrent -> 0f
                                isPast    -> 2.5f
                                else      -> 0.5f
                            },
                            animationSpec = tween(350),
                            label = "lineBlur_$index"
                        )

                        Text(
                            text = line.text,
                            style = if (isCurrent) {
                                MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    lineHeight = 44.sp,
                                    letterSpacing = (-0.5).sp
                                )
                            } else {
                                MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 38.sp
                                )
                            },
                            color = textColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(textScale)
                                .blur(radius = blurRadius.dp)
                                .clickable { playerManager.seekTo((line.timestampMs - syncOffset).coerceAtLeast(0)) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }

    // Equalizzatore bottom sheet (Bug #1)
    if (showEqualizer) {
        EqualizerBottomSheet(
            equalizerManager = playerManager.equalizerManager,
            onDismiss = { showEqualizer = false }
        )
    }
}
