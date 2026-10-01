package com.muse.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muse.app.player.EqualizerManager
import com.muse.app.player.EqPreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerBottomSheet(
    equalizerManager: EqualizerManager,
    onDismiss: () -> Unit
) {
    // Colleziona i StateFlow: ogni cambio aggiorna automaticamente l'UI
    val bandLevels by equalizerManager.bandLevelsFlow.collectAsState()
    val selectedPreset by equalizerManager.currentPresetFlow.collectAsState()

    val bandFreqs = equalizerManager.bandFrequencies
    val minLevel = equalizerManager.minLevel.toFloat()
    val maxLevel = equalizerManager.maxLevel.toFloat()

    // Se l'EQ non è ancora inizializzato, mostra 5 bande a zero come fallback
    val safeBandLevels = bandLevels.ifEmpty { List(equalizerManager.bandCount) { 0 } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Equalizer,
                        contentDescription = null,
                        tint = Color(0xFF9B59B6),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Equalizzatore",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Preset chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EqPreset.values().toList()) { preset ->
                    val isSelected = preset == selectedPreset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) Color(0xFF9B59B6)
                                else Color(0xFF2A2A40)
                            )
                            .border(
                                1.dp,
                                if (isSelected) Color.Transparent else Color(0xFF9B59B6).copy(alpha = 0.3f),
                                RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                // Applica il preset: i StateFlow aggiornano l'UI in automatico
                                equalizerManager.applyPreset(preset)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = preset.displayName,
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Slider per ogni banda — si aggiornano automaticamente al cambio preset
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                safeBandLevels.forEachIndexed { index, level ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Valore in dB sopra
                        Text(
                            text = if (level >= 0) "+${level / 100}" else "${level / 100}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (level != 0) Color(0xFF9B59B6) else Color.Gray
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Slider verticale
                        Slider(
                            value = level.toFloat(),
                            onValueChange = { newVal ->
                                equalizerManager.setBandLevel(index, newVal.toInt())
                            },
                            valueRange = if (minLevel < maxLevel) minLevel..maxLevel else -1500f..1500f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF9B59B6),
                                activeTrackColor = Color(0xFF9B59B6),
                                inactiveTrackColor = Color(0xFF2A2A40)
                            ),
                            modifier = Modifier
                                .height(160.dp)
                                .graphicsLayer { rotationZ = -90f }
                                .width(160.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Frequenza sotto
                        Text(
                            text = bandFreqs.getOrElse(index) { "" },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.Gray
                            )
                        )
                    }
                }
            }
        }
    }
}
