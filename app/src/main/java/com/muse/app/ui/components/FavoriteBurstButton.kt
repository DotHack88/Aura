package com.muse.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FavoriteBurstButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animazione di scala per il cuore (rimbalzo)
    val scale by animateFloatAsState(
        targetValue = if (isFavorite) 1.25f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "favoriteScale"
    )

    val color by animateColorAsState(
        targetValue = if (isFavorite) Color(0xFFE91E63) else MaterialTheme.colorScheme.onBackground,
        animationSpec = tween(300),
        label = "favoriteColor"
    )

    // Animazione per il burst di particelle
    val transition = updateTransition(targetState = isFavorite, label = "burstTransition")
    val burstProgress by transition.animateFloat(
        transitionSpec = {
            if (targetState) tween(durationMillis = 500, easing = LinearOutSlowInEasing)
            else snap()
        },
        label = "burstProgress"
    ) { state ->
        if (state) 1f else 0f
    }

    Box(
        modifier = modifier
            .size(64.dp) // Leggermente più grande per far spazio al burst
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material.ripple.rememberRipple(bounded = false, radius = 24.dp),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Disegno particelle
        if (burstProgress > 0f && burstProgress < 1f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = (size.width / 2f) * 0.8f // max estensione
                
                // 6 particelle
                val particleCount = 6
                for (i in 0 until particleCount) {
                    val angle = (i * (360f / particleCount)) * (Math.PI / 180f)
                    
                    // Distanza progressiva dal centro verso l'esterno
                    val currentRadius = radius * burstProgress
                    
                    val x = center.x + cos(angle).toFloat() * currentRadius
                    val y = center.y + sin(angle).toFloat() * currentRadius
                    
                    // Svanisce verso la fine
                    val alpha = (1f - burstProgress).coerceIn(0f, 1f)
                    
                    drawCircle(
                        color = Color(0xFFE91E63),
                        radius = 4.dp.toPx() * (1f - burstProgress), // rimpicciolisce
                        center = Offset(x, y),
                        alpha = alpha
                    )
                }
            }
        }

        Icon(
            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = "Preferiti",
            tint = color,
            modifier = Modifier
                .scale(if (isFavorite) scale else 1f)
                .size(28.dp)
        )
    }
}
