package com.muse.app.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Composable che estrae il colore dominante da un URL immagine.
 * Ritorna [Color.Transparent] finche l`immagine non e caricata.
 */
@Composable
fun rememberDominantColor(imageUrl: String?): Color {
    var dominantColor by remember(imageUrl) { mutableStateOf(Color.Transparent) }
    val context = LocalContext.current

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) return@LaunchedEffect
        val bitmap = loadBitmapFromUrl(context, imageUrl)
        if (bitmap != null) {
            val palette = Palette.from(bitmap).generate()
            val argb = palette.darkVibrantSwatch?.rgb
                ?: palette.vibrantSwatch?.rgb
                ?: palette.darkMutedSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
            if (argb != null) {
                dominantColor = Color(argb)
            }
        }
    }

    return dominantColor
}

/**
 * Composable che estrae il colore ACCENT vibrante (luminoso) da un URL immagine.
 * Usato per tingere i controlli UI (bottone play, slider, icone attive) in modo armonioso
 * con la copertina corrente. Ritorna [Color.Transparent] finche l`immagine non e caricata.
 */
@Composable
fun rememberAccentColor(imageUrl: String?): Color {
    var accentColor by remember(imageUrl) { mutableStateOf(Color.Transparent) }
    val context = LocalContext.current

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) return@LaunchedEffect
        val bitmap = loadBitmapFromUrl(context, imageUrl)
        if (bitmap != null) {
            val palette = Palette.from(bitmap).generate()
            // Preferisce vibrantSwatch (luminoso e saturo) per i controlli
            val argb = palette.vibrantSwatch?.rgb
                ?: palette.lightVibrantSwatch?.rgb
                ?: palette.lightMutedSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
            if (argb != null) {
                accentColor = Color(argb)
            }
        }
    }

    return accentColor
}

private suspend fun loadBitmapFromUrl(context: android.content.Context, url: String): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(200, 200)
                .build()
            val result = loader.execute(request)
            (result as? SuccessResult)?.drawable?.let { (it as? BitmapDrawable)?.bitmap }
        } catch (e: Exception) {
            null
        }
    }
