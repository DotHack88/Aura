package com.muse.app.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import com.muse.app.domain.lyrics.DefaultLyricsProvider
import com.muse.app.domain.model.Lyrics
import com.muse.app.domain.model.Track
import com.muse.app.player.PlayerManager
import com.muse.app.utils.ComposeCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedShareBottomSheet(
    track: Track,
    playerManager: PlayerManager? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var shareMode by remember { mutableStateOf("Brano") } // "Brano" o "Testo"
    var selectedColor by remember { mutableStateOf(Color(0xFF5B629A)) }
    var trackCoverBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isSharing by remember { mutableStateOf(false) }
    var lyricsText by remember { mutableStateOf("Sto ascoltando\nquesto brano\ne non riesco\na smettere.") }

    val colors = listOf(
        Color(0xFF5B629A),
        Color(0xFF8B5A8B),
        Color(0xFF2E4057),
        Color(0xFF4A6B53),
        Color(0xFF8A3A3A),
        Color(0xFF2D2D2D)
    )

    // Preload track cover for synchronous drawing
    LaunchedEffect(track.thumbnailUrl) {
        if (track.thumbnailUrl.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(track.thumbnailUrl)
                    .allowHardware(false) // Must be false for Canvas drawing
                    .build()
                val result = loader.execute(request)
                val drawable = result.drawable
                if (drawable != null) {
                    val bitmap = drawable.toBitmap()
                    trackCoverBitmap = bitmap.asImageBitmap()
                }
            }
        }
    }

    // Use already-loaded lyrics from PlayerManager if available, else fetch fresh
    val currentLyricsFromPlayer by (playerManager?.currentLyrics ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
    val currentPositionMs by (playerManager?.playerState ?: kotlinx.coroutines.flow.MutableStateFlow(com.muse.app.domain.model.PlayerState())).collectAsState()

    LaunchedEffect(track.id, currentLyricsFromPlayer) {
        val lyrics: Lyrics? = currentLyricsFromPlayer ?: withContext(Dispatchers.IO) {
            DefaultLyricsProvider().getLyrics(track.id, track.artist, track.title)
        }

        if (lyrics != null) {
            val posMs = currentPositionMs.positionMs
            // Find the index of the line currently being sung
            val currentIndex = if (lyrics.lines.isNotEmpty()) {
                val idx = lyrics.lines.indexOfLast { it.timestampMs <= posMs }
                if (idx < 0) 0 else idx
            } else -1

            val snippet = if (currentIndex >= 0 && lyrics.lines.isNotEmpty()) {
                // Show 4 lines window starting a bit before current line
                val from = (currentIndex - 1).coerceAtLeast(0)
                val to = (from + 4).coerceAtMost(lyrics.lines.size)
                lyrics.lines.subList(from, to).map { it.text }.filter { it.isNotBlank() }.joinToString("\n")
            } else {
                // Fallback to plainText first 4 lines
                lyrics.plainText?.split("\n")?.filter { it.isNotBlank() }?.take(4)?.joinToString("\n")
                    ?: lyricsText
            }
            if (snippet.isNotBlank()) lyricsText = snippet
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = Color.White)
                }
                Text(
                    text = "Condividi",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.width(48.dp)) // balance center
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Card Preview (The actual UI the user sees)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (shareMode == "Brano") Color(0xFF181818) else selectedColor),
                contentAlignment = Alignment.Center
            ) {
                ShareCardContent(
                    track = track,
                    shareMode = shareMode,
                    trackCoverBitmap = trackCoverBitmap,
                    lyricsText = lyricsText
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Brano / Testo Toggle
            Row(
                modifier = Modifier
                    .background(Color(0xFF2A2A2A), RoundedCornerShape(24.dp))
                    .padding(4.dp)
            ) {
                ShareModeButton("Brano", shareMode == "Brano") { shareMode = "Brano" }
                ShareModeButton("Testo", shareMode == "Testo") { shareMode = "Testo" }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Color Picker
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selectedColor == color) 2.dp else 0.dp,
                                color = if (selectedColor == color) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { selectedColor = color }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Share Actions
            Button(
                onClick = {
                    if (isSharing) return@Button
                    isSharing = true
                    coroutineScope.launch {
                        try {
                            // Genera la bitmap (3:4 ratio per sembrare una card Spotify)
                            val W = 1080
                            val H = 1440
                            val bitmap = withContext(Dispatchers.Default) {
                                val bmp = android.graphics.Bitmap.createBitmap(W, H, android.graphics.Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bmp)

                                // Clip card rotonda
                                val path = android.graphics.Path()
                                val rect = android.graphics.RectF(0f, 0f, W.toFloat(), H.toFloat())
                                path.addRoundRect(rect, 48f, 48f, android.graphics.Path.Direction.CW)
                                canvas.clipPath(path)

                                // Sfondo
                                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                                if (shareMode == "Brano") {
                                    paint.color = android.graphics.Color.parseColor("#181818")
                                } else {
                                    paint.color = android.graphics.Color.argb(
                                        (selectedColor.alpha * 255).toInt(),
                                        (selectedColor.red * 255).toInt(),
                                        (selectedColor.green * 255).toInt(),
                                        (selectedColor.blue * 255).toInt()
                                    )
                                }
                                canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)

                                val coverBmp = trackCoverBitmap?.asAndroidBitmap()

                                if (shareMode == "Brano" && coverBmp != null) {
                                    // Cover quadrata grande in alto
                                    val coverSize = 1080f
                                    val src = android.graphics.Rect(0, 0, coverBmp.width, coverBmp.height)
                                    val dst = android.graphics.RectF(0f, 0f, coverSize, coverSize)
                                    canvas.drawBitmap(coverBmp, src, dst, paint)

                                    val padX = 60f
                                    var textY = coverSize + 110f

                                    // Nome Brano
                                    paint.color = android.graphics.Color.WHITE
                                    paint.textSize = 64f
                                    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
                                    canvas.drawText(track.title.take(28), padX, textY, paint)

                                    // Nome Artista e Album
                                    textY += 70f
                                    paint.textSize = 44f
                                    paint.color = android.graphics.Color.parseColor("#B3B3B3")
                                    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
                                    val artistAlbum = buildString {
                                        append(track.artist)
                                        if (!track.album.isNullOrBlank()) {
                                            append(" • ")
                                            append(track.album)
                                        }
                                    }
                                    canvas.drawText(artistAlbum.take(40), padX, textY, paint)

                                    // Footer (Aura Music + Icon)
                                    val bottomY = H - 60f
                                    paint.textSize = 38f
                                    canvas.drawText("Aura Music", padX, bottomY, paint)

                                    val iconSize = 64f
                                    val iconX = W - padX - iconSize
                                    val drawable = context.packageManager.getApplicationIcon(context.packageName)
                                    val appIcon = android.graphics.Bitmap.createBitmap(128, 128, android.graphics.Bitmap.Config.ARGB_8888)
                                    val iconCanvas = android.graphics.Canvas(appIcon)
                                    drawable.setBounds(0, 0, 128, 128)
                                    drawable.draw(iconCanvas)
                                    
                                    val iconSrc = android.graphics.Rect(0, 0, appIcon.width, appIcon.height)
                                    val iconDst = android.graphics.RectF(iconX, bottomY - iconSize + 5f, iconX + iconSize, bottomY + 5f)
                                    canvas.drawBitmap(appIcon, iconSrc, iconDst, paint)

                                } else if (shareMode == "Testo") {
                                    paint.color = android.graphics.Color.WHITE
                                    paint.textSize = 58f
                                    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
                                    var textY = H * 0.3f
                                    val padX = 60f
                                    val padLyrics = 80f
                                    
                                    val lyricsToDraw = lyricsText.split("\n").take(8).joinToString("\n")
                                    val textPaint = android.text.TextPaint(paint)
                                    val staticLayout = android.text.StaticLayout.Builder.obtain(
                                        lyricsToDraw,
                                        0,
                                        lyricsToDraw.length,
                                        textPaint,
                                        W - (padLyrics.toInt() * 2)
                                    )
                                        .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                                        .setLineSpacing(0f, 1.2f)
                                        .build()

                                    canvas.save()
                                    canvas.translate(padLyrics, textY)
                                    staticLayout.draw(canvas)
                                    canvas.restore()
                                    
                                    // Footer Testo (Uguale a Brano)
                                    val footY = H - 230f
                                    paint.color = android.graphics.Color.WHITE
                                    paint.textSize = 64f
                                    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
                                    canvas.drawText(track.title.take(28), padX, footY, paint)

                                    // Nome Artista e Album
                                    val artistY = footY + 70f
                                    paint.textSize = 44f
                                    paint.color = android.graphics.Color.parseColor("#E0E0E0")
                                    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
                                    val artistAlbum = buildString {
                                        append(track.artist)
                                        if (!track.album.isNullOrBlank()) {
                                            append(" • ")
                                            append(track.album)
                                        }
                                    }
                                    canvas.drawText(artistAlbum.take(40), padX, artistY, paint)

                                    // Footer (Aura Music + Icon)
                                    val bottomY = H - 60f
                                    paint.textSize = 38f
                                    canvas.drawText("Aura Music", padX, bottomY, paint)

                                    val iconSize = 64f
                                    val iconX = W - padX - iconSize
                                    val drawable = context.packageManager.getApplicationIcon(context.packageName)
                                    val appIcon = android.graphics.Bitmap.createBitmap(128, 128, android.graphics.Bitmap.Config.ARGB_8888)
                                    val iconCanvas = android.graphics.Canvas(appIcon)
                                    drawable.setBounds(0, 0, 128, 128)
                                    drawable.draw(iconCanvas)
                                    
                                    val iconSrc = android.graphics.Rect(0, 0, appIcon.width, appIcon.height)
                                    val iconDst = android.graphics.RectF(iconX, bottomY - iconSize + 5f, iconX + iconSize, bottomY + 5f)
                                    canvas.drawBitmap(appIcon, iconSrc, iconDst, paint)
                                }

                                bmp
                            }
                            val uri = ComposeCapture.saveBitmapAndGetUri(context, bitmap)
                            if (uri != null) {
                                shareImageIntent(context, uri)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            isSharing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isSharing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Condividi Immagine", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ShareModeButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Color.White else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.Black else Color.LightGray,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ShareCardContent(
    track: Track,
    shareMode: String,
    trackCoverBitmap: ImageBitmap?,
    lyricsText: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (shareMode == "Brano") {
            // Brano Cover Mode - Stile Spotify
            Column(modifier = Modifier.fillMaxSize()) {
                // Cover Art (Square at top)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(Color(0xFF333333))
                ) {
                    if (trackCoverBitmap != null) {
                        Image(
                            bitmap = trackCoverBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Title
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Artist • Album
                    val artistAlbum = buildString {
                        append(track.artist)
                        if (!track.album.isNullOrBlank()) {
                            append(" • ")
                            append(track.album)
                        }
                    }
                    Text(
                        text = artistAlbum,
                        color = Color(0xFFB3B3B3),
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }

                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Aura Music",
                        color = Color(0xFFB3B3B3),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }        } else {
            // Testo / Quote Mode
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = lyricsText,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 34.sp
                    )
                }

                // Inserimento informazioni uguali alla modalità Brano
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    // Title
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Artist • Album
                    val artistAlbum = buildString {
                        append(track.artist)
                        if (!track.album.isNullOrBlank()) {
                            append(" • ")
                            append(track.album)
                        }
                    }
                    Text(
                        text = artistAlbum,
                        color = Color(0xFFE0E0E0),
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }

                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Aura Music",
                        color = Color(0xFFE0E0E0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun shareImageIntent(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "Condividi con").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
