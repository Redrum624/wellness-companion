package com.wellnesscompanion.app.ui.hobbies

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Pre-computed crane positions for three stages: inside bowl, mountain, overflow */
private data class CraneSlot(val x: Float, val y: Float, val rotation: Float)

// Bowl positions (cranes 0-18): fill inside the bowl, bottom to top
private val bowlSlots = listOf(
    CraneSlot(0.19f, 0.88f, 8f), CraneSlot(0.35f, 0.90f, -15f), CraneSlot(0.52f, 0.88f, 5f),
    CraneSlot(0.68f, 0.90f, -10f), CraneSlot(0.82f, 0.88f, 18f),
    CraneSlot(0.23f, 0.80f, -20f), CraneSlot(0.40f, 0.81f, 12f), CraneSlot(0.57f, 0.79f, -8f),
    CraneSlot(0.72f, 0.81f, 22f), CraneSlot(0.80f, 0.78f, -14f),
    CraneSlot(0.20f, 0.72f, 16f), CraneSlot(0.36f, 0.73f, -22f), CraneSlot(0.52f, 0.71f, 8f),
    CraneSlot(0.68f, 0.73f, -15f), CraneSlot(0.82f, 0.72f, 20f),
    CraneSlot(0.25f, 0.65f, -10f), CraneSlot(0.43f, 0.66f, 18f), CraneSlot(0.60f, 0.64f, -6f),
    CraneSlot(0.75f, 0.66f, 14f)
)

// Mountain positions (cranes 19-35): pyramid above the bowl rim
private val mountainSlots = listOf(
    CraneSlot(0.19f, 0.56f, 12f), CraneSlot(0.36f, 0.55f, -18f), CraneSlot(0.52f, 0.56f, 8f),
    CraneSlot(0.68f, 0.55f, -12f), CraneSlot(0.82f, 0.56f, 16f),
    CraneSlot(0.25f, 0.48f, -14f), CraneSlot(0.42f, 0.47f, 20f), CraneSlot(0.58f, 0.48f, -8f),
    CraneSlot(0.74f, 0.47f, 15f),
    CraneSlot(0.32f, 0.40f, 10f), CraneSlot(0.50f, 0.39f, -16f), CraneSlot(0.65f, 0.40f, 22f),
    CraneSlot(0.38f, 0.33f, -10f), CraneSlot(0.55f, 0.32f, 14f), CraneSlot(0.70f, 0.33f, -18f),
    CraneSlot(0.44f, 0.26f, 8f), CraneSlot(0.60f, 0.25f, -12f)
)

// Overflow positions (cranes 36+): spill to left and right of the bowl
private val overflowLeftSlots = listOf(
    CraneSlot(0.06f, 0.92f, 55f), CraneSlot(0.03f, 0.86f, -42f),
    CraneSlot(0.09f, 0.80f, 68f), CraneSlot(0.01f, 0.96f, -50f), CraneSlot(0.07f, 0.98f, 38f)
)
private val overflowRightSlots = listOf(
    CraneSlot(0.91f, 0.91f, -55f), CraneSlot(0.95f, 0.85f, 42f),
    CraneSlot(0.88f, 0.79f, -68f), CraneSlot(0.97f, 0.95f, 50f), CraneSlot(0.91f, 0.98f, -38f)
)

private fun getSlot(index: Int): CraneSlot {
    val allMain = bowlSlots + mountainSlots
    if (index < allMain.size) return allMain[index]
    val overflowIdx = index - allMain.size
    val isLeft = overflowIdx % 2 == 0
    val sideIdx = overflowIdx / 2
    return if (isLeft) {
        overflowLeftSlots.getOrElse(sideIdx) { CraneSlot(0.05f, 0.92f, 55f) }
    } else {
        overflowRightSlots.getOrElse(sideIdx) { CraneSlot(0.93f, 0.92f, -55f) }
    }
}

@Composable
fun CraneBowlCanvas(
    craneCount: Int,
    craneColors: List<String>,
    modifier: Modifier = Modifier
) {
    val pageColor = Color(0xFFEACCE0)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Draw bowl background (opaque fill behind cranes)
        val bowlPath = Path().apply {
            moveTo(w * 0.065f, h * 0.58f)
            quadraticTo(w * 0.03f, h * 0.72f, w * 0.10f, h * 0.84f)
            quadraticTo(w * 0.20f, h * 0.95f, w * 0.50f, h * 0.96f)
            quadraticTo(w * 0.80f, h * 0.95f, w * 0.90f, h * 0.84f)
            quadraticTo(w * 0.97f, h * 0.72f, w * 0.935f, h * 0.58f)
            close()
        }
        drawPath(bowlPath, pageColor)

        // Draw cranes
        for (i in 0 until craneCount) {
            val slot = getSlot(i)
            val colorHex = craneColors.getOrElse(i) { "#AFA9EC" }
            val color = parseHexColor(colorHex)
            val darkerColor = color.copy(
                red = (color.red * 0.7f).coerceIn(0f, 1f),
                green = (color.green * 0.7f).coerceIn(0f, 1f),
                blue = (color.blue * 0.7f).coerceIn(0f, 1f)
            )
            val cx = slot.x * w
            val cy = slot.y * h
            val isOverflow = i >= bowlSlots.size + mountainSlots.size
            val opacity = if (isOverflow) 0.75f else 1f
            drawCrane(cx, cy, 127f, slot.rotation, color, darkerColor, opacity)
        }

        // Draw bowl outline on top
        drawPath(bowlPath, Color(0xFF2A5A80).copy(alpha = 0.18f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))

        // Bowl rim shine
        drawLine(
            Color.White.copy(alpha = 0.25f),
            Offset(w * 0.10f, h * 0.60f),
            Offset(w * 0.30f, h * 0.72f),
            strokeWidth = 2f
        )
    }
}

private fun DrawScope.drawCrane(
    cx: Float, cy: Float, scale: Float, rotDeg: Float,
    color: Color, darkColor: Color, opacity: Float
) {
    val r = rotDeg * PI.toFloat() / 180f
    val co = cos(r)
    val si = sin(r)
    val s = scale

    fun t(px: Float, py: Float): Offset {
        val x = px * s
        val y = py * s
        return Offset(cx + x * co - y * si, cy + x * si + y * co)
    }

    fun drawPoly(points: List<Pair<Float, Float>>, fillColor: Color, alpha: Float) {
        if (points.size < 3) return
        val path = Path().apply {
            val first = t(points[0].first, points[0].second)
            moveTo(first.x, first.y)
            for (i in 1 until points.size) {
                val p = t(points[i].first, points[i].second)
                lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(path, fillColor.copy(alpha = (fillColor.alpha * alpha * opacity).coerceIn(0f, 1f)))
    }

    // Wing 1 (left front)
    drawPoly(listOf(0.05f to -0.12f, -0.38f to -0.95f, -0.32f to -0.05f), color, 1f)
    // Wing 1 (left back)
    drawPoly(listOf(0.05f to -0.12f, -0.38f to -0.95f, 0.0f to -0.25f), darkColor, 0.82f)
    // Wing 1 (right front)
    drawPoly(listOf(0.05f to -0.12f, 0.48f to -0.82f, 0.35f to -0.05f), color, 0.82f)
    // Wing 1 (right back)
    drawPoly(listOf(0.05f to -0.12f, 0.48f to -0.82f, 0.55f to -0.25f), darkColor, 0.68f)
    // Body
    drawPoly(listOf(-0.32f to -0.05f, 0.05f to -0.12f, 0.35f to -0.05f, 0.12f to 0.22f, -0.18f to 0.18f), color, 0.68f)
    // Neck
    drawPoly(listOf(-0.18f to 0.18f, 0.12f to 0.22f, 0.05f to 0.32f, -0.1f to 0.28f), darkColor, 0.55f)
}

private fun parseHexColor(hex: String): Color {
    val cleaned = hex.removePrefix("#")
    return try {
        Color(android.graphics.Color.parseColor("#$cleaned"))
    } catch (e: Exception) {
        Color(0xFFAFA9EC)
    }
}
