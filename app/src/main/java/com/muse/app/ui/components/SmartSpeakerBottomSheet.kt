package com.muse.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.muse.app.cast.CastManager
import com.muse.app.domain.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartSpeakerBottomSheet(
    track: Track,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isCasting by CastManager.castSession.collectAsState()
    
    // Recupera i route (dispositivi) disponibili
    var availableRoutes by remember { mutableStateOf<List<MediaRouter.RouteInfo>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val router = MediaRouter.getInstance(context)
        val selector = MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            .build()

        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                if (!route.isDefault && !availableRoutes.contains(route)) {
                    availableRoutes = (availableRoutes + route).distinctBy { it.id }
                }
            }
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
                availableRoutes = availableRoutes.filter { it.id != route.id }
            }
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {}
        }

        router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        // Popola i route già noti
        availableRoutes = router.routes.filter { !it.isDefault && it.matchesSelector(selector) }
        isScanning = false

        onDispose {
            router.removeCallback(callback)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Riproduci su dispositivo",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        track.title,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        ),
                        maxLines = 1
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Chiudi")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Se c'è una sessione Cast attiva, mostra stop
            if (isCasting != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Speaker,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "In riproduzione su",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                )
                                Text(
                                    isCasting!!.castDevice?.friendlyName ?: "Dispositivo Cast",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = { CastManager.stopCasting(); onDismiss() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ferma")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Sezione Google Cast
            Text(
                "Google Home / Chromecast",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isScanning) {
                Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (availableRoutes.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Nessun dispositivo trovato nella rete locale.\nAssicurati che il tuo Google Home/Nest sia acceso e sulla stessa Wi-Fi.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    )
                }
            } else {
                availableRoutes.forEach { route ->
                    DeviceRouteItem(
                        name = route.name,
                        description = route.description ?: "Google Cast",
                        isSelected = isCasting?.castDevice?.deviceId == route.id,
                        onClick = {
                            val router = MediaRouter.getInstance(context)
                            router.selectRoute(route)
                            // Una volta selezionato il route, avvia il cast
                            CastManager.castTrack(track)
                            onDismiss()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            // Sezione Alexa
            Text(
                "Amazon Alexa",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            AlexaInfoRow(context = context)
        }
    }
}

@Composable
private fun DeviceRouteItem(
    name: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else Color.Transparent
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Speaker,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text(description, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)))
        }
        if (isSelected) {
            Icon(
                Icons.Default.Speaker,
                contentDescription = "Attivo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AlexaInfoRow(context: Context) {
    val alexaPackage = "com.amazon.dee.app"
    val alexaInstalled = remember {
        try {
            context.packageManager.getPackageInfo(alexaPackage, 0)
            true
        } catch (e: Exception) { false }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔵", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Amazon Alexa",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    if (alexaInstalled)
                        "Apri l'app Alexa per gestire la riproduzione su Echo."
                    else
                        "Alexa non supporta lo streaming diretto da app esterne. Installa l'app Alexa per controllare i tuoi Echo.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                )
            }
        }
        if (alexaInstalled) {
            TextButton(
                onClick = {
                    val intent = context.packageManager.getLaunchIntentForPackage(alexaPackage)
                    if (intent != null) context.startActivity(intent)
                },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 8.dp, bottom = 4.dp)
            ) {
                Text("Apri Alexa")
            }
        } else {
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id="))
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 8.dp, bottom = 4.dp)
            ) {
                Text("Installa Alexa")
            }
        }
    }
}
