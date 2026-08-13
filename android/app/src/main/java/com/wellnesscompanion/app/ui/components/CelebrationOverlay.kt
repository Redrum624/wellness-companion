package com.wellnesscompanion.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val size: Float
)

@Composable
fun CelebrationOverlay(
    visible: Boolean,
    goalName: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val progress = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
            )
        }

        // Card entrance: pop in with a bouncy scale + fade, independent of confetti timing
        val cardScale = remember { Animatable(0.8f) }
        val cardAlpha = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            cardScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            )
        }
        LaunchedEffect(Unit) {
            cardAlpha.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            )
        }

        val particles = remember {
            val colors = listOf(
                Color(0xFFF0997B), Color(0xFF5DCAA5), Color(0xFFAFA9EC),
                Color(0xFFED93B1), Color(0xFFF5E5C4), Color(0xFF85B7EB),
                Color(0xFFD6EDCC), Color(0xFFF5D6E3)
            )
            val bursts = listOf(
                Offset(0.2f, 0.15f),
                Offset(0.8f, 0.12f),
                Offset(0.5f, 0.2f),
                Offset(0.3f, 0.75f),
                Offset(0.7f, 0.8f)
            )
            bursts.flatMap { center ->
                (0..25).map {
                    val angle = Random.nextFloat() * 2f * PI.toFloat()
                    val speed = Random.nextFloat() * 5f + 1.5f
                    Particle(
                        x = center.x,
                        y = center.y,
                        vx = cos(angle) * speed,
                        vy = sin(angle) * speed,
                        color = colors.random(),
                        size = Random.nextFloat() * 8f + 3f
                    )
                }
            }
        }

        // Full-screen scrim + centered card — renders above everything
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A2A40).copy(alpha = 0.75f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            // Firework particles
            Canvas(modifier = Modifier.fillMaxSize()) {
                val t = progress.value
                particles.forEach { p ->
                    val gravity = 0.4f
                    val px = (p.x + p.vx * t * 0.08f) * size.width
                    val py = (p.y + p.vy * t * 0.08f + gravity * t * t * 0.015f) * size.height
                    val alpha = (1f - t * 0.7f).coerceIn(0f, 1f) * 0.9f
                    val radius = p.size * (1f - t * 0.3f)

                    if (alpha > 0.01f) {
                        drawCircle(
                            color = p.color.copy(alpha = alpha),
                            radius = radius,
                            center = Offset(px, py)
                        )
                    }
                }
            }

            // Card
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .scale(cardScale.value)
                    .alpha(cardAlpha.value)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.95f),
                                Color(0xFFF5E5C4).copy(alpha = 0.9f)
                            )
                        )
                    )
                    .padding(horizontal = 32.dp, vertical = 40.dp)
            ) {
                Text(
                    "\uD83C\uDF89",
                    fontSize = 56.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "CONGRATULATIONS!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2A5A80),
                    letterSpacing = 2.sp,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "You achieved your",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF2A5A80).copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                Text(
                    "$goalName goal!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2A5A80),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "tap anywhere to dismiss",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2A5A80).copy(alpha = 0.35f)
                )
            }
        }
    }
}
