package com.paulo.prismagrab.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Identidade PRISMA: fundo quase-preto, vidro/prisma, acento espectro (violeta-índigo).
val PrismBg = Color(0xFF0B0B0F)
val PrismSurface = Color(0xFF16161D)
val PrismSurface2 = Color(0xFF1E1E27)
val PrismAccent = Color(0xFF7C5CFF)
val PrismAccent2 = Color(0xFF00D3B8)
val PrismText = Color(0xFFEDEDF2)
val PrismMuted = Color(0xFF9A9AA8)
val PrismError = Color(0xFFFF6B6B)

private val PrismScheme = darkColorScheme(
    primary = PrismAccent,
    onPrimary = Color.White,
    secondary = PrismAccent2,
    background = PrismBg,
    onBackground = PrismText,
    surface = PrismSurface,
    onSurface = PrismText,
    surfaceVariant = PrismSurface2,
    onSurfaceVariant = PrismMuted,
    error = PrismError,
)

@Composable
fun PrismaTheme(content: @Composable () -> Unit) {
    // App é sempre dark (identidade), independente do sistema.
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()
    MaterialTheme(colorScheme = PrismScheme, content = content)
}
