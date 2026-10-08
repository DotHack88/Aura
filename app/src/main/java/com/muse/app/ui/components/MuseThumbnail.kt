package com.muse.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Componente wrapper attorno a [AsyncImage] con fallback visivo integrato.
 *
 * - Se [url] è vuota o nulla mostra un placeholder colorato con un'icona.
 * - Usa [ContentScale.Crop] per default.
 * - L'ImageLoader globale (configurato in MuseApplication) applica automaticamente
 *   il User-Agent corretto per evitare i 403 di YouTube/Google.
 *
 * @param url        URL dell'immagine da caricare (può essere vuota o nulla)
 * @param contentDescription Descrizione accessibilità
 * @param size       Dimensione del componente (larghezza e altezza uguali)
 * @param shape      Forma del clip (default: angoli arrotondati 8dp)
 * @param fallbackIcon Icona da mostrare nel placeholder (default: nota musicale)
 */
@Composable
fun MuseThumbnail(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    shape: Shape = RoundedCornerShape(8.dp),
    fallbackIcon: ImageVector = Icons.Default.MusicNote
) {
    val effectiveUrl = url?.takeIf { it.isNotBlank() }
    // Se nessun modifier size è specificato esternamente, applichiamo size(size) di default
    val baseModifier = modifier.then(Modifier.size(size))

    if (effectiveUrl != null) {
        val urls = effectiveUrl.split(",")
        if (urls.size >= 4) {
            Column(modifier = baseModifier.clip(shape)) {
                Row(modifier = Modifier.weight(1f)) {
                    AsyncImage(
                        model = urls[0],
                        contentDescription = null,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentScale = ContentScale.Crop
                    )
                    AsyncImage(
                        model = urls[1],
                        contentDescription = null,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentScale = ContentScale.Crop
                    )
                }
                Row(modifier = Modifier.weight(1f)) {
                    AsyncImage(
                        model = urls[2],
                        contentDescription = null,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentScale = ContentScale.Crop
                    )
                    AsyncImage(
                        model = urls[3],
                        contentDescription = null,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        } else {
            AsyncImage(
                model = urls.first(),
                contentDescription = contentDescription,
                modifier = baseModifier.clip(shape),
                contentScale = ContentScale.Crop
            )
        }
    } else {
        Box(
            modifier = baseModifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                modifier = Modifier.size(size * 0.5f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Variante circolare per avatar artisti.
 */
@Composable
fun MuseArtistThumbnail(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    MuseThumbnail(
        url = url,
        contentDescription = contentDescription,
        modifier = modifier,
        size = size,
        shape = CircleShape,
        fallbackIcon = Icons.Default.Person
    )
}
