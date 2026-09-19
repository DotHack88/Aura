package com.muse.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.muse.app.domain.model.Track
import com.muse.app.player.PlayerManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    playerManager: PlayerManager,
    onDismiss: () -> Unit
) {
    val state by playerManager.playerState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "In coda",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(
                    items = state.playlist,
                    key = { index, track -> "${track.id}_$index" }
                ) { index, track ->
                    val isCurrent = index == state.currentIndex
                    val isPast = index < state.currentIndex

                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                                if (!isCurrent) {
                                    playerManager.removeTrack(index)
                                    true
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = !isCurrent,
                        enableDismissFromEndToStart = !isCurrent,
                        backgroundContent = {
                            val color by animateColorAsState(
                                targetValue = if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) MaterialTheme.colorScheme.errorContainer else Color.Transparent,
                                label = "dismissColor"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Rimuovi",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    ) {
                        QueueItem(
                            track = track,
                            isCurrent = isCurrent,
                            isPast = isPast,
                            onMoveUp = if (index > 0) { { playerManager.moveTrack(index, index - 1) } } else null,
                            onMoveDown = if (index < state.playlist.size - 1) { { playerManager.moveTrack(index, index + 1) } } else null,
                            onClick = {
                                if (!isCurrent) {
                                    playerManager.playTrack(track, state.playlist)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueItem(
    track: Track,
    isCurrent: Boolean,
    isPast: Boolean,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onClick: () -> Unit
) {
    val alpha = if (isPast) 0.5f else 1f
    val elevation by animateDpAsState(if (isCurrent) 4.dp else 0.dp, label = "elevation")
    val backgroundColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        }

        if (!isCurrent) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (onMoveUp != null) {
                    IconButton(
                        onClick = onMoveUp,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Sposta su",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (onMoveDown != null) {
                    IconButton(
                        onClick = onMoveDown,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Sposta giù",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
