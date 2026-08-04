package com.paulo.prismagrab.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Espectro do prisma — MESMAS cores do desktop (splash-rays / anel do G6).
val SPECTRUM = listOf(
    Color(0xFFFF453A), Color(0xFFFF9F0A), Color(0xFFFFD60A), Color(0xFF30D158),
    Color(0xFF0A84FF), Color(0xFF5E5CE6), Color(0xFFBF5AF2), Color(0xFFFF453A),
)

@Composable
private fun spin(durationMs: Int): Float {
    val t = rememberInfiniteTransition(label = "spin")
    val a by t.animateFloat(
        0f, 360f, infiniteRepeatable(tween(durationMs, easing = LinearEasing)), label = "ang",
    )
    return a
}

/** Anel de espectro girando — loader que remete ao prisma (troca o spinner cinza). */
@Composable
fun PrismRing(size: Dp, stroke: Dp = 3.dp, modifier: Modifier = Modifier) {
    val angle = spin(2400)
    Canvas(modifier.size(size)) {
        val sw = stroke.toPx()
        rotate(angle) {
            drawCircle(
                brush = Brush.sweepGradient(SPECTRUM, center),
                radius = (this.size.minDimension - sw) / 2f,
                style = Stroke(sw),
            )
        }
    }
}

/** Halo de raios prismáticos girando (atrás da logo no splash). */
@Composable
fun PrismRays(size: Dp, modifier: Modifier = Modifier) {
    val angle = spin(9000)
    Canvas(modifier.size(size)) {
        rotate(angle) {
            drawCircle(
                brush = Brush.sweepGradient(SPECTRUM, center),
                radius = this.size.minDimension / 2f,
                style = Stroke(this.size.minDimension * 0.16f),
            )
        }
    }
}

/**
 * Barra de progresso com o espectro DESLIZANDO (mesma identidade das barras do desktop).
 * `progress` null = indeterminada (varre sozinha).
 */
@Composable
fun PrismProgress(progress: Float?, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "prog")
    val shift by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "shift",
    )
    Box(
        modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(PrismSurface2),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .then(if (progress != null) Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)) else Modifier.fillMaxWidth())
                .clip(RoundedCornerShape(3.dp))
                .drawBehind {
                    val w = size.width
                    // gradiente 2x a largura, transladando → espectro "escorre"
                    val off = shift * 2f * w
                    drawRect(
                        Brush.linearGradient(
                            SPECTRUM,
                            start = Offset(-2f * w + off, 0f),
                            end = Offset(off, 0f),
                            tileMode = androidx.compose.ui.graphics.TileMode.Repeated,
                        ),
                    )
                },
        )
    }
}

/** Splash de abertura: raios girando atrás da marca + nome + barra espectro (enquanto o motor prepara). */
@Composable
fun PrismSplash(mark: @Composable (Dp) -> Unit) {
    Box(Modifier.fillMaxSize().background(PrismBg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                PrismRays(120.dp)
                mark(64.dp)
            }
            Spacer(Modifier.height(26.dp))
            androidx.compose.material3.Text(
                "PRISMA",
                color = PrismText,
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(20.dp))
            PrismProgress(progress = null, modifier = Modifier.width(150.dp))
        }
    }
}
