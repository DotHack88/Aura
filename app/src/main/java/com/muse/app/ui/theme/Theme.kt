package com.muse.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette minimale, elegante e ad alto contrasto
val DarkBackground = Color(0xFF0F0F12)
val DarkSurface = Color(0xFF18181D)
val DarkSurfaceVariant = Color(0xFF22222B)
val PrimaryAccent = Color(0xFF8B5CF6)      // Viola moderno
val SecondaryAccent = Color(0xFFEC4899)    // Fucsia/Rosa delicato
val TextPrimary = Color(0xFFF9FAFB)
val TextSecondary = Color(0xFF9CA3AF)
val LyricsHighlight = Color(0xFFFFFFFF)
val LyricsDimmed = Color(0xFF4B5563)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryAccent,
    secondary = SecondaryAccent,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryAccent,
    secondary = SecondaryAccent,
    background = Color(0xFFF8F9FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F3F5),
    onPrimary = Color.White,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A)
)

@Composable
fun MuseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else DarkColorScheme // Prevalenza Dark Minimal per player

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
