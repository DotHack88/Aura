package com.muse.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muse.app.cast.CastManager
import com.muse.app.domain.model.PlayerState

@Composable
fun MiniPlayer(
    playerState: PlayerState,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playerState.currentTrack ?: return
    val isCasting by CastManager.isCasting.collectAsState()

    // Colore dominante dalla copertina per il glassmorphism
    val rawDominant = rememberDominantColor(track.thumbnailUrl)
    val rawAccent = rememberAccentColor(track.thumbnailUrl)

    val dominantColor by animateColorAsState(
        targetValue = if (rawDominant == Color.Transparent)
            MaterialTheme.colorScheme.surfaceVariant
        else
            rawDominant.copy(alpha = 0.6f),
        animationSpec = tween(600),
        label = "miniPlayerBg"
    )
    val accentColor by animateColorAsState(
        targetValue = if (rawAccent == Color.Transparent)
            MaterialTheme.colorScheme.primary
        else
            rawAccent,
        animationSpec = tween(600),
        label = "miniAccent"
    )

    val progress = if (playerState.durationMs > 0) {
        (playerState.positionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onExpand() }
    ) {
        // Sfondo glassmorphism: gradiente dominante semi-trasparente + overlay scuro
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            dominantColor,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                        )
                    )
                )
        )
        // Sottile bordo luminoso per effetto glass
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column {
            // Barra progresso con colore accent
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = accentColor,
                trackColor = Color.Transparent
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail
                MuseThumbnail(
                    url = track.thumbnailUrl,
                    contentDescription = null,
                    size = 44.dp,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Info brano + equalizzatore
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isCasting) {
                                Icon(
                                    imageVector = Icons.Default.Speaker,
                                    contentDescription = "Cast attivo",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = if (isCasting) "Cast ▸ ${track.artist}" else track.artist,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.65f)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Equalizzatore animato accanto al nome brano
                    EqualizerBars(
                        isPlaying = playerState.isPlaying,
                        color = accentColor,
                        maxHeight = 18.dp
                    )
                }

                // Bottoni controllo
                IconButton(onClick = onTogglePlay, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pausa" else "Riproduci",
                        tint = Color.White
                    )
                }

                IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Successivo",
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
