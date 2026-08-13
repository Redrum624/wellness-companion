package com.wellnesscompanion.app.ui.hobbies

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * A glass bowl that fills with origami cranes: one crane per 5 minutes of hobby time.
 * The first 23 land inside the bowl, the next 15 pile up above the rim, and any more
 * tumble onto the ground beside it. Cranes inside the bowl are clipped to its interior
 * (open at the top), so nothing can poke through the glass.
 */

private val GlassEdge = Color(0xFF72243E) // HobbiesText plum, used at low alpha

/** One facet of the origami crane, in unit coordinates (y down, facing left). */
private class Facet(points: List<Pair<Float, Float>>, val shade: Int, val alpha: Float) {
    val xs = FloatArray(points.size) { points[it].first }
    val ys = FloatArray(points.size) { points[it].second }
}

private const val LIGHT = 0
private const val BASE = 1
private const val MID = 2
private const val DARK = 3

// Drawn back-to-front: tail, neck, head, far wing, body, near wing.
private val CRANE_FACETS = listOf(
    Facet(listOf(0.30f to -0.02f, 0.55f to -0.32f, 0.36f to 0.10f), MID, 1f),
    Facet(listOf(-0.28f to -0.02f, -0.50f to -0.44f, -0.22f to 0.06f), MID, 1f),
    Facet(listOf(-0.50f to -0.44f, -0.63f to -0.39f, -0.47f to -0.34f), DARK, 1f),
    Facet(listOf(-0.02f to -0.10f, 0.32f to -0.74f, 0.30f to -0.02f), DARK, 0.9f),
    Facet(listOf(-0.30f to 0.02f, 0.02f to -0.14f, 0.34f to 0.02f, 0.04f to 0.20f), BASE, 1f),
    Facet(listOf(-0.12f to -0.08f, 0.04f to -0.90f, 0.20f to -0.02f), LIGHT, 1f),
)

// Unit-scale facet paths, built once — withTransform scales them per crane, so no
// per-frame Path allocation during the drop animation.
private val FACET_PATHS: List<Path> = CRANE_FACETS.map { facet ->
    Path().apply {
        moveTo(facet.xs[0], facet.ys[0])
        for (j in 1 until facet.xs.size) lineTo(facet.xs[j], facet.ys[j])
        close()
    }
}

private data class CraneSlot(val x: Float, val y: Float, val rotation: Float, val flip: Boolean)

// (yFrac, count, halfWidthFrac) — bowl rows bottom-up, then the mountain pyramid above the rim.
// Row half-widths leave room for a crane's own extent (~0.074w with jitter and rotation)
// inside the clip wall at that row's depth, so edge cranes lean on the glass instead of
// being flat-cut by the clip (at most ~0.02w of a wingtip may tuck behind the wall).
private val BOWL_ROWS = listOf(
    Triple(0.855f, 3, 0.130f), Triple(0.775f, 4, 0.185f), Triple(0.695f, 5, 0.225f),
    Triple(0.615f, 5, 0.245f), Triple(0.535f, 6, 0.252f),
)
private val MOUNTAIN_ROWS = listOf(
    Triple(0.435f, 5, 0.26f), Triple(0.355f, 4, 0.20f), Triple(0.28f, 3, 0.145f),
    Triple(0.21f, 2, 0.085f), Triple(0.15f, 1, 0.0f),
)
private val BOWL_CAPACITY = BOWL_ROWS.sumOf { it.second }
private val MAIN_CAPACITY = BOWL_CAPACITY + MOUNTAIN_ROWS.sumOf { it.second }

/** Deterministic per-index jitter so the heap looks organic but never reshuffles. */
private fun fracHash(i: Int, salt: Int): Float {
    val x = sin(i * 127.1 + salt * 311.7) * 43758.5453
    return (x - floor(x)).toFloat()
}

private fun buildMainSlots(w: Float, h: Float): List<CraneSlot> {
    val cx = 0.5f * w
    val slots = ArrayList<CraneSlot>(MAIN_CAPACITY)
    var idx = 0
    for ((yf, n, hwf) in BOWL_ROWS + MOUNTAIN_ROWS) {
        // Fill each row from the center outward so a partial row reads as a heap.
        val order = (0 until n).sortedBy { kotlin.math.abs(it - (n - 1) / 2f) }
        for (k in order) {
            val x = if (n == 1) cx else cx - hwf * w + (2 * hwf * w) * k / (n - 1)
            val jx = (fracHash(idx, 1) - 0.5f) * 0.022f * w
            val jy = (fracHash(idx, 2) - 0.5f) * 0.018f * h
            val rot = (fracHash(idx, 3) - 0.5f) * 36f
            slots.add(CraneSlot(x + jx, yf * h + jy, rot, fracHash(idx, 4) > 0.5f))
            idx++
        }
    }
    return slots
}

private fun overflowSlot(k: Int, w: Float, h: Float): CraneSlot {
    val side = if (k % 2 == 0) -1f else 1f
    val step = min(k / 2, 7)
    val x = 0.5f * w + side * (0.415f * w + step * 0.062f * w + (fracHash(k, 5) - 0.5f) * 0.02f * w)
    val y = 0.895f * h - fracHash(k, 6) * 0.012f * h
    val rot = side * (34f + fracHash(k, 7) * 26f)
    return CraneSlot(x, y, rot, k % 2 == 1)
}

@Composable
fun CraneBowlCanvas(
    craneCount: Int,
    craneColors: List<String>,
    lastAddedIndex: Int = -1,
    modifier: Modifier = Modifier
) {
    // New cranes drop in from above the rim and settle with a soft bounce — but only for
    // counts that increase while this screen is showing. The ViewModel's lastAddedIndex
    // survives navigation, so without the lastSeenCount guard the last batch would
    // re-drop on every screen re-entry.
    val drop = remember { Animatable(1f) }
    var lastSeenCount by remember { mutableIntStateOf(-1) }
    LaunchedEffect(craneCount, lastAddedIndex) {
        val isLiveIncrease = lastSeenCount in 0 until craneCount
        lastSeenCount = craneCount
        if (isLiveIncrease && lastAddedIndex in 0 until craneCount) {
            drop.snapTo(0f)
            drop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = 0.5f * w
        val rimY = 0.46f * h
        val rw = 0.335f * w
        val groundY = 0.945f * h
        val craneScale = 0.096f * w

        val silhouette = bowlSilhouette(w, h)
        val interior = bowlInterior(w, h)

        // Ground shadow
        drawOval(
            GlassEdge.copy(alpha = 0.10f),
            topLeft = Offset(cx - 0.36f * w, groundY - 0.022f * h),
            size = Size(0.72f * w, 0.044f * h)
        )

        // Glass, seen from behind the cranes
        drawPath(silhouette, Color.White.copy(alpha = 0.235f))

        val slots = buildMainSlots(w, h)
        val dropP = drop.value

        fun drawAt(i: Int, slot: CraneSlot, scale: Float, opacity: Float) {
            val animating = lastAddedIndex in 0..i && dropP < 1f
            val cy = if (animating) slot.y - (1f - dropP) * 0.32f * h else slot.y
            val rot = if (animating) slot.rotation + (1f - dropP) * (if (slot.flip) -18f else 18f) else slot.rotation
            val alpha = if (animating) opacity * (0.4f + 0.6f * dropP) else opacity
            drawCrane(slot.x, cy, scale, rot, slot.flip, hexColor(craneColors.getOrElse(i) { "#AFA9EC" }), alpha)
        }

        // Cranes inside the bowl — clipped to the interior, which is open above the rim,
        // so a crane can never poke through the glass bottom or walls.
        clipPath(interior) {
            for (i in 0 until min(craneCount, BOWL_CAPACITY)) drawAt(i, slots[i], craneScale, 1f)
        }

        // Glass front: sheen, edge, rim, highlight
        drawPath(silhouette, Color.White.copy(alpha = 0.10f))
        drawPath(silhouette, Color.White.copy(alpha = 0.65f), style = Stroke(0.0035f * w))
        drawPath(silhouette, GlassEdge.copy(alpha = 0.235f), style = Stroke(0.0016f * w))
        val rimRy = 0.030f * h
        drawOval(
            Color.White.copy(alpha = 0.59f),
            topLeft = Offset(cx - rw, rimY - rimRy),
            size = Size(2 * rw, 2 * rimRy),
            style = Stroke(0.0030f * w)
        )
        val highlight = Path().apply {
            moveTo(cx - rw * 0.86f, rimY + 0.06f * h)
            quadraticTo(cx - rw * 0.92f, 0.68f * h, cx - rw * 0.55f, 0.83f * h)
        }
        drawPath(highlight, Color.White.copy(alpha = 0.47f), style = Stroke(0.006f * w, cap = StrokeCap.Round))

        // The pile rising above the rim sits in front of the glass edge
        for (i in BOWL_CAPACITY until min(craneCount, MAIN_CAPACITY)) drawAt(i, slots[i], craneScale, 1f)

        // Overflow tumbles onto the ground beside the bowl
        if (craneCount > MAIN_CAPACITY) {
            clipRect(0f, 0f, w, groundY) {
                for (i in MAIN_CAPACITY until craneCount) {
                    drawAt(i, overflowSlot(i - MAIN_CAPACITY, w, h), craneScale * 0.92f, 0.95f)
                }
            }
        }
    }
}

private fun bowlSilhouette(w: Float, h: Float): Path {
    val cx = 0.5f * w
    val rimY = 0.46f * h
    val rw = 0.335f * w
    val botY = 0.925f * h
    val footHw = 0.135f * w
    return Path().apply {
        moveTo(cx - rw, rimY)
        cubicTo(cx - rw - 0.015f * w, 0.70f * h, cx - 0.30f * w, 0.875f * h, cx - footHw, botY)
        quadraticTo(cx, botY + 0.022f * h, cx + footHw, botY)
        cubicTo(cx + 0.30f * w, 0.875f * h, cx + rw + 0.015f * w, 0.70f * h, cx + rw, rimY)
        close()
    }
}

/** The bowl's inside, inset from the walls, extended upward as an open column. */
private fun bowlInterior(w: Float, h: Float): Path {
    val cx = 0.5f * w
    val rimY = 0.46f * h
    val rw = 0.335f * w
    val k = 0.95f // wall inset
    fun ix(x: Float) = cx + (x - cx) * k
    fun iy(y: Float) = rimY + (y - rimY) * k
    val botY = 0.925f * h
    val footHw = 0.135f * w
    return Path().apply {
        moveTo(ix(cx - rw), rimY)
        cubicTo(ix(cx - rw - 0.015f * w), iy(0.70f * h), ix(cx - 0.30f * w), iy(0.875f * h), ix(cx - footHw), iy(botY))
        quadraticTo(cx, iy(botY + 0.022f * h), ix(cx + footHw), iy(botY))
        cubicTo(ix(cx + 0.30f * w), iy(0.875f * h), ix(cx + rw + 0.015f * w), iy(0.70f * h), ix(cx + rw), rimY)
        lineTo(ix(cx + rw), 0f)
        lineTo(ix(cx - rw), 0f)
        close()
    }
}

private fun DrawScope.drawCrane(
    cx: Float, cy: Float, scale: Float, rotDeg: Float, flip: Boolean,
    color: Color, opacity: Float
) {
    val shades = arrayOf(
        lerpToWhite(color, 0.20f),
        color,
        shadeOf(color, 0.78f),
        shadeOf(color, 0.55f),
    )
    withTransform({
        translate(cx, cy)
        rotate(rotDeg, Offset.Zero)
        scale(if (flip) -scale else scale, scale, Offset.Zero)
    }) {
        for ((i, facet) in CRANE_FACETS.withIndex()) {
            drawPath(FACET_PATHS[i], shades[facet.shade], alpha = (facet.alpha * opacity).coerceIn(0f, 1f))
        }
    }
}

private fun lerpToWhite(c: Color, t: Float) = Color(
    c.red + (1f - c.red) * t, c.green + (1f - c.green) * t, c.blue + (1f - c.blue) * t, c.alpha
)

private fun shadeOf(c: Color, f: Float) = Color(c.red * f, c.green * f, c.blue * f, c.alpha)

private fun hexColor(hex: String): Color {
    val cleaned = hex.removePrefix("#")
    return try {
        Color(android.graphics.Color.parseColor("#$cleaned"))
    } catch (e: Exception) {
        Color(0xFFAFA9EC)
    }
}
