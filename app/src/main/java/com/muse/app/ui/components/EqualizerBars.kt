package com.muse.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Equalizzatore animato karaoke-style.
 * Mostra 3 barrette che "ballano" quando [isPlaying] è true.
 * Si ferma (barrette abbassate) quando la riproduzione è in pausa.
 *
 * @param isPlaying  se true le barre animano, altrimenti si abbassano
 * @param color      colore delle barre (tipicamente accent dinamico)
 * @param barWidth   larghezza di ciascuna barra
 * @param maxHeight  altezza massima raggiungibile da ogni barra
 */
@Composable
fun EqualizerBars(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    barWidth: Dp = 3.dp,
    maxHeight: Dp = 16.dp
) {
    // Offset di fase diversi per le 3 barre → effetto realistico non sincrono
    val phases = listOf(0, 180, 90)
    val durations = listOf(600, 450, 520)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        phases.forEachIndexed { index, phase ->
            val infiniteTransition = rememberInfiniteTransition(label = "eq_$index")
            val heightFraction by infiniteTransition.animateFloat(
                initialValue = if (phase == 0) 0.3f else 0.15f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = durations[index],
                        easing = FastOutSlowInEasing,
                        delayMillis = phase * durations[index] / 360
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )

            val targetFraction = if (isPlaying) heightFraction else 0.2f
            val animatedFraction by animateFloatAsState(
                targetValue = targetFraction,
                animationSpec = tween(durationMillis = 300),
                label = "eq_anim_$index"
            )

            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(maxHeight * animatedFraction)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(color)
            )
        }
    }
}
