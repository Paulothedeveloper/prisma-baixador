package com.paulo.prismagrab.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.paulo.prismagrab.R

// ---- Identidade PRISMA (espelhada 1:1 do desktop: tokens do App.css) ----
val PrismBg = Color(0xFF0E0F13)        // --bg
val PrismBg2 = Color(0xFF090A0D)       // --bg-2
val PrismSurface = Color(0xFF191B21)   // --glass
val PrismSurface2 = Color(0xFF202329)  // --glass-strong
val PrismBorder = Color(0x14FFFFFF)    // --glass-border (8% branco)
val PrismAccent = Color(0xFF0A84FF)    // --accent (azul Apple)
val PrismAccentCta = Color(0xFF0A6FD8) // --accent-cta
val PrismText = Color(0xFFF5F5F7)      // --text
val PrismMuted = Color(0xFF98989D)     // --text-2
val PrismSuccess = Color(0xFF30D158)   // verde sistema (par do azul)
val PrismError = Color(0xFFFF453A)     // vermelho sistema

// Inter (mesma família do desktop), estática embutida (offline).
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun interType(): Typography {
    val base = Typography()
    fun TextStyle.i() = copy(fontFamily = Inter)
    return base.copy(
        displaySmall = base.displaySmall.i(),
        headlineMedium = base.headlineMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.i(),
        titleLarge = base.titleLarge.i(),
        titleMedium = base.titleMedium.i(),
        bodyLarge = base.bodyLarge.copy(fontFamily = Inter, letterSpacing = (-0.1).sp),
        bodyMedium = base.bodyMedium.i(),
        bodySmall = base.bodySmall.i(),
        labelLarge = base.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.i(),
    )
}

private val PrismScheme = darkColorScheme(
    primary = PrismAccent,
    onPrimary = Color.White,
    secondary = PrismAccent,
    background = PrismBg,
    onBackground = PrismText,
    surface = PrismSurface,
    onSurface = PrismText,
    surfaceVariant = PrismSurface2,
    onSurfaceVariant = PrismMuted,
    outline = PrismBorder,
    error = PrismError,
)

@Composable
fun PrismaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PrismScheme, typography = interType(), content = content)
}
