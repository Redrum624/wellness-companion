package com.wellnesscompanion.app.ui.water

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun WaterBottleCanvas(
    fillFraction: Float,
    capacity: Int,
    onDragDelta: (pixelDelta: Float, canvasHeight: Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 200f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveOffset"
    )

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { onDragEnd() },
                    onVerticalDrag = { _, dragAmount ->
                        onDragDelta(dragAmount, size.height.toFloat())
                    }
                )
            }
    ) {
        drawBottle(fillFraction, waveOffset, capacity)
    }
}

private fun DrawScope.drawBottle(fill: Float, waveOffset: Float, capacity: Int) {
    val w = size.width
    val h = size.height
    val sx = w / 220f
    val sy = h / 490f

    // Bottle outline path
    val bottlePath = Path().apply {
        moveTo(91f * sx, 8f * sy)
        lineTo(129f * sx, 8f * sy)
        lineTo(129f * sx, 40f * sy)
        quadraticTo(178f * sx, 66f * sy, 195f * sx, 128f * sy)
        lineTo(195f * sx, 442f * sy)
        quadraticTo(195f * sx, 462f * sy, 110f * sx, 462f * sy)
        quadraticTo(25f * sx, 462f * sy, 25f * sx, 442f * sy)
        lineTo(25f * sx, 128f * sy)
        quadraticTo(42f * sx, 66f * sy, 91f * sx, 40f * sy)
        close()
    }

    // Glass background
    drawPath(bottlePath, Color.White.copy(alpha = 0.2f))

    // Water fill — clipped to bottle
    if (fill > 0.01f) {
        clipPath(bottlePath) {
            val bodyTop = 80f * sy
            val bodyBottom = 460f * sy
            val bodyHeight = bodyBottom - bodyTop
            val waterTop = bodyBottom - (bodyHeight * fill)

            // Wave path at water surface
            val wavePath = Path().apply {
                moveTo(0f, waterTop)
                var x = 0f
                while (x <= w) {
                    val y = waterTop + sin((x + waveOffset) * PI / 100f).toFloat() * 6f * sy
                    lineTo(x, y)
                    x += 2f
                }
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }

            drawPath(wavePath, Color(0xFF8EC8F8).copy(alpha = 0.55f))
        }
    }

    // Graduation marks
    val markPositions = listOf(0.25f, 0.5f, 0.75f)
    val bodyTop = 80f * sy
    val bodyBottom = 460f * sy
    val bodyHeight = bodyBottom - bodyTop
    markPositions.forEach { pct ->
        val markY = bodyBottom - (bodyHeight * pct)
        val aboveFill = pct > fill
        val markColor = if (aboveFill) Color.White.copy(alpha = 0.35f) else Color(0xFF2A5A80).copy(alpha = 0.2f)
        drawLine(markColor, Offset(55f * sx, markY), Offset(85f * sx, markY), strokeWidth = 1.5f)
    }

    // Glass highlight
    drawLine(
        Color.White.copy(alpha = 0.38f),
        Offset(55f * sx, 140f * sy),
        Offset(52f * sx, 400f * sy),
        strokeWidth = 5f * sx
    )
    drawLine(
        Color.White.copy(alpha = 0.18f),
        Offset(165f * sx, 160f * sy),
        Offset(168f * sx, 380f * sy),
        strokeWidth = 2.5f * sx
    )

    // Cap
    drawPath(
        Path().apply {
            moveTo(85f * sx, 8f * sy)
            lineTo(135f * sx, 8f * sy)
            lineTo(135f * sx, 30f * sy)
            lineTo(85f * sx, 30f * sy)
            close()
        },
        Color.White.copy(alpha = 0.34f)
    )
    // Cap ridges
    for (i in 0..3) {
        val y = (12f + i * 5f) * sy
        drawLine(Color.White.copy(alpha = 0.2f), Offset(88f * sx, y), Offset(132f * sx, y), strokeWidth = 1.2f)
    }

    // Bottle outline
    drawPath(bottlePath, Color(0xFF2A5A80).copy(alpha = 0.18f), style = Stroke(width = 2f))
}
