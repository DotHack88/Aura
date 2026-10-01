package com.muse.app.ui.player

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.muse.app.ui.components.MuseThumbnail
import android.widget.FrameLayout
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.muse.app.data.repository.MusicRepository
import com.muse.app.domain.model.PlayerMode
import com.muse.app.domain.model.RepeatMode
import com.muse.app.player.PlayerManager
import com.muse.app.cast.CastManager
import com.muse.app.ui.components.TrackOptionsBottomSheet
import com.muse.app.ui.components.SmartSpeakerBottomSheet
import com.muse.app.ui.components.QueueBottomSheet
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import com.muse.app.ui.components.rememberDominantColor
import com.muse.app.ui.components.rememberAccentColor
import com.muse.app.ui.components.shimmerEffect
import com.muse.app.ui.components.FavoriteBurstButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.interaction.MutableInteractionSource
import java.util.Locale
import com.muse.app.ui.components.EqualizerBottomSheet
import com.muse.app.ui.components.CrossfadeBottomSheet
import com.muse.app.ui.components.SleepTimerDialog
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import com.muse.app.ui.components.RelatedBottomSheet
import kotlinx.coroutines.delay
import com.muse.app.share.WhatsAppShareManager
import kotlinx.coroutines.launch
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun FullPlayerScreen(
    playerManager: PlayerManager,
    musicRepository: MusicRepository,
    onNavigateToLyrics: () -> Unit,
    onNavigateToSearch: (String) -> Unit = {},
    onNavigateToAlbum: ((browseId: String, title: String, artist: String, cover: String) -> Unit)? = null,
    onDismiss: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by playerManager.playerState.collectAsState()
    val track = state.currentTrack ?: return
    val isCasting by CastManager.isCasting.collectAsState()
    var isVideoLoading by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showSmartSpeaker by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showRelated by remember { mutableStateOf(false) }
    var showCrossfade by remember { mutableStateOf(false) }

    // Auto-hide controlli in modalità Canvas (video mode)
    var controlsVisible by remember { mutableStateOf(true) }
    // Timer per nascondere i controlli dopo 3 secondi di inattività
    LaunchedEffect(controlsVisible, state.isVideoMode) {
        if (state.isVideoMode && controlsVisible) {
            delay(3000L)
            controlsVisible = false
        }
    }

    // Rilevamento swipe verso l'alto per aprire la modalità lyrics
    var dragAccumulated by remember { mutableFloatStateOf(0f) }
    val draggableState = rememberDraggableState { delta ->
        dragAccumulated += delta
        if (dragAccumulated < -140f) { // Swipe verso l'alto
            dragAccumulated = 0f
            onNavigateToLyrics()
        }
    }

    // Estrazione colore dominante (scuro) per lo sfondo e colore accent (vivace) per i controlli
    val dominantColor = rememberDominantColor(track.thumbnailUrl)
    val rawAccentColor = rememberAccentColor(track.thumbnailUrl)
    val backgroundColor by animateColorAsState(
        targetValue = if (dominantColor == androidx.compose.ui.graphics.Color.Transparent)
            MaterialTheme.colorScheme.surfaceVariant
        else
            dominantColor,
        animationSpec = tween(durationMillis = 800),
        label = "playerBg"
    )
    // Fallback sul viola del tema se la palette non ha ancora estratto un colore
    val accentColor by animateColorAsState(
        targetValue = if (rawAccentColor == androidx.compose.ui.graphics.Color.Transparent)
            MaterialTheme.colorScheme.primary
        else
            rawAccentColor,
        animationSpec = tween(durationMillis = 800),
        label = "playerAccent"
    )
    // Animazione copertina rimossa come richiesto
    val animatedCoverScale = 1f
    val sliderInteractionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        backgroundColor,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { dragAccumulated = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        dragAccumulated += dragAmount
                        if (dragAccumulated < -140f) {
                            dragAccumulated = 0f
                            onNavigateToLyrics()
                        }
                    }
                )
            }
    ) {
        // --- 1. FULLSCREEN CANVAS LAYER (Motion Artwork) ---
        if (state.isVideoMode) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = playerManager.exoPlayer
                        useController = false
                        useArtwork = false
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { pv ->
                    pv.player = playerManager.exoPlayer
                    if (state.isPlaying) isVideoLoading = false
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Dark Gradient Overlay for Readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f), // Top (leggero scuro per la top bar)
                                Color.Transparent, 
                                Color.Black.copy(alpha = 0.85f) // Bottom (molto scuro per controlli e testo)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY // Il gradiente si calcola dinamicamente
                        )
                    )
            )
            // Tap sul video per mostrare/nascondere i controlli
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        controlsVisible = !controlsVisible
                    }
            )
        } // CHIUSURA di if (state.isVideoMode)

        // --- 2. FOREGROUND CONTENT LAYER ---
        AnimatedVisibility(
            visible = !state.isVideoMode || controlsVisible,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp), // Padding applicato ai contenuti, non al background
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Chiudi, EQ, Sleep Timer, Switch Video/Audio
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Chiudi",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Correlati
                    IconButton(onClick = { showRelated = true }) {
                        Icon(
                            imageVector = Icons.Default.Recommend,
                            contentDescription = "Correlati",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Sleep Timer
                    IconButton(onClick = { showSleepTimer = true }) {
                        Icon(
                            imageVector = if (state.sleepTimerRemainingMs > 0) Icons.Default.Nightlight else Icons.Default.BedtimeOff,
                            contentDescription = "Sleep Timer",
                            tint = if (state.sleepTimerRemainingMs > 0) accentColor else MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Crossfade
                    IconButton(onClick = { showCrossfade = true }) {
                        Icon(
                            imageVector = if (state.crossfadeDurationMs > 0) Icons.Default.BlurOn else Icons.Default.BlurOff,
                            contentDescription = "Crossfade",
                            tint = if (state.crossfadeDurationMs > 0) accentColor else MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Equalizzatore
                    IconButton(onClick = { showEqualizer = true }) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = "Equalizzatore",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    IconButton(onClick = {
                        if (!state.isVideoMode) {
                            isVideoLoading = true
                            playerManager.switchToVideoMode()
                        } else {
                            isVideoLoading = false
                            playerManager.switchToAudioMode()
                        }
                    }) {
                        Icon(
                            imageVector = if (state.isVideoMode) Icons.Default.MusicNote else Icons.Default.Movie,
                            contentDescription = if (state.isVideoMode) "Disattiva Motion Artwork" else "Attiva Motion Artwork",
                            tint = if (state.isVideoMode) Color.White else accentColor
                        )
                    }
                }
            }

            // Copertina Album o Placeholder Video
            if (state.isVideoMode) {
                // In Motion Artwork mode, the cover is hidden and we show a spacer to maintain layout
                Spacer(modifier = Modifier.weight(1f))
                
                // Indicatore di caricamento sovrapposto mentre lo stream video viene estratto
                if (isVideoLoading) {
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                    }
                }
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .aspectRatio(1f, matchHeightConstraintsFirst = false)
                        .padding(vertical = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        MuseThumbnail(
                            url = track.thumbnailUrl,
                            contentDescription = track.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .border(
                                    width = 1.dp,
                                    brush = Brush.radialGradient(
                                        colors = listOf(accentColor.copy(alpha = 0.8f), Color.Transparent)
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                ),
                            size = 300.dp,
                            shape = RoundedCornerShape(24.dp)
                        )
                    }
                }
            }

            // Info Brano & Tasto Preferito
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (state.isLoading) Color.Transparent else MaterialTheme.colorScheme.onBackground
                        ),
                        modifier = if (state.isLoading) 
                            Modifier.clip(RoundedCornerShape(4.dp)).shimmerEffect() 
                        else Modifier,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .graphicsLayer(alpha = 0.99f)
                            .then(
                                if (state.isLoading) {
                                    Modifier.clip(RoundedCornerShape(4.dp)).shimmerEffect()
                                } else {
                                    Modifier.drawWithCache {
                                        val brush = Brush.horizontalGradient(listOf(Color.White, accentColor))
                                        onDrawWithContent {
                                            drawContent()
                                            drawRect(brush, blendMode = BlendMode.SrcAtop)
                                        }
                                    }
                                }
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FavoriteBurstButton(
                    isFavorite = track.isFavorite,
                    onClick = { playerManager.toggleFavoriteCurrent() }
                )
            }

            // Slider Progresso
            Column(modifier = Modifier.fillMaxWidth()) {
                val progress = if (state.durationMs > 0) {
                    (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Slider(
                    value = progress,
                    onValueChange = { frac ->
                        val targetMs = (frac * state.durationMs).toLong()
                        playerManager.seekTo(targetMs)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = accentColor.copy(alpha = 0.25f)
                    ),
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource = sliderInteractionSource,
                            colors = SliderDefaults.colors(thumbColor = accentColor),
                            modifier = Modifier
                                .size(22.dp)
                                .shadow(
                                    elevation = 8.dp,
                                    shape = CircleShape,
                                    ambientColor = accentColor,
                                    spotColor = accentColor
                                )
                        )
                    },
                    interactionSource = sliderInteractionSource,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(state.positionMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Text(
                        text = formatTime(state.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }

            // Controlli Principali (Shuffle, Prev, Play/Pause, Next, Repeat)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { playerManager.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (state.shuffle) accentColor else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }

                IconButton(
                    onClick = { playerManager.previous() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Precedente",
                        modifier = Modifier.size(32.dp)
                    )
                }

                FloatingActionButton(
                    onClick = { playerManager.togglePlayPause() },
                    shape = CircleShape,
                    containerColor = accentColor,
                    contentColor = Color.White,
                    modifier = Modifier.size(68.dp)
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pausa" else "Play",
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = { playerManager.next() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Successivo",
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(onClick = { playerManager.cycleRepeatMode() }) {
                    val icon = when (state.repeat) {
                        RepeatMode.ONE -> Icons.Default.RepeatOne
                        RepeatMode.ALL -> Icons.Default.Repeat
                        RepeatMode.OFF -> Icons.Default.Repeat
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat",
                        tint = if (state.repeat != RepeatMode.OFF) accentColor else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }

            // Prompt Swipe Up verso i Testi
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                // Tasto Smart Speaker Cast a sinistra
                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                    IconButton(onClick = { showSmartSpeaker = true }) {
                        Icon(
                            imageVector = Icons.Default.Speaker,
                            contentDescription = "Riproduci su speaker",
                            tint = if (isCasting) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                    // Badge verde quando Cast è attivo
                    if (isCasting) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .align(Alignment.TopEnd)
                                .background(Color(0xFF4CAF50), androidx.compose.foundation.shape.CircleShape)
                        )
                    }
                }

                // Indicatore Testi perfettamente centrato
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Lyrics Swipe Up",
                        tint = accentColor
                    )
                    Text(
                        text = "TESTI",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = accentColor.copy(alpha = 0.8f),
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Tasti Destri
                Row(modifier = Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                    // Tasto Condividi (solo in modalità Canva/Video)
                    if (state.isVideoMode) {
                        var isSharing by remember { mutableStateOf(false) }
                        IconButton(onClick = {
                            if (isSharing) return@IconButton
                            isSharing = true
                            Toast.makeText(context, "Preparazione video per lo stato (30s)...", Toast.LENGTH_SHORT).show()
                            
                            val videoIdToShare = state.currentYouTubeVideoId ?: track.id
                            scope.launch {
                                WhatsAppShareManager.shareToWhatsApp(
                                    context = context,
                                    track = track,
                                    videoId = videoIdToShare,
                                    startMs = state.positionMs,
                                    onProgress = { /* ignorato, usiamo il CircularProgressIndicator */ },
                                    onSuccess = { isSharing = false },
                                    onError = { error ->
                                        isSharing = false
                                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }) {
                            if (isSharing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color(0xFF1DB954),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Condividi",
                                    tint = Color(0xFF1DB954) // Verde come da richiesta
                                )
                            }
                        }
                    }

                    // Tasto Coda
                    IconButton(onClick = { showQueue = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Coda di riproduzione",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }

                    // Tasto Opzioni (⋮)
                    IconButton(onClick = { showOptionsMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opzioni brano",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            }
        }
        } // chiusura AnimatedVisibility
    }

    // Bottom sheet opzioni brano
    if (showOptionsMenu) {
        TrackOptionsBottomSheet(
            track = track,
            musicRepository = musicRepository,
            playerManager = playerManager,
            onDismiss = { showOptionsMenu = false },
            showAddToQueue = false,
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToAlbum = onNavigateToAlbum
        )
    }

    // Smart Speaker bottom sheet
    if (showSmartSpeaker) {
        SmartSpeakerBottomSheet(
            track = track,
            onDismiss = { showSmartSpeaker = false }
        )
    }

    // Related bottom sheet
    if (showRelated) {
        RelatedBottomSheet(
            currentTrackId = track.id,
            musicRepository = musicRepository,
            playerManager = playerManager,
            onDismiss = { showRelated = false }
        )
    }

    // Queue bottom sheet
    if (showQueue) {
        QueueBottomSheet(
            playerManager = playerManager,
            onDismiss = { showQueue = false }
        )
    }

    // Equalizer bottom sheet
    if (showEqualizer) {
        EqualizerBottomSheet(
            equalizerManager = playerManager.equalizerManager,
            onDismiss = { showEqualizer = false }
        )
    }

    // Sleep Timer dialog
    if (showSleepTimer) {
        SleepTimerDialog(
            remainingMs = state.sleepTimerRemainingMs,
            onSetTimer = { playerManager.setSleepTimer(it) },
            onCancelTimer = { playerManager.cancelSleepTimer() },
            onDismiss = { showSleepTimer = false }
        )
    }

    // Crossfade bottom sheet
    if (showCrossfade) {
        CrossfadeBottomSheet(
            currentDurationMs = state.crossfadeDurationMs,
            onSetDuration = { playerManager.setCrossfadeDuration(it) },
            onDismiss = { showCrossfade = false }
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
