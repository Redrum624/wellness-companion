package com.wellnesscompanion.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.wellnesscompanion.app.data.model.Category
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PastelBackground(category: Category, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        when (category) {
            Category.WATER -> drawWaterBackground()
            Category.FOOD -> drawFoodBackground()
            Category.BATHROOM -> drawBathroomBackground()
            Category.HEALTH -> drawHealthBackground()
            Category.SLEEP -> drawSleepBackground()
            Category.EMOTIONS -> drawEmotionsBackground()
            Category.INTERACTIONS -> drawInteractionsBackground()
            Category.CHORES -> drawChoresBackground()
            Category.HOBBIES -> drawHobbiesBackground()
            Category.IDEAS -> drawIdeasBackground()
            Category.CYCLE -> drawCycleBackground()
            Category.BADHABITS -> drawBadHabitsBackground()
        }
    }
}

// ─── Bad Habits: Smoke, Bottle, Ember, Wilted Leaves ───────────────────────

private fun DrawScope.drawBadHabitsBackground() {
    val w = size.width
    val h = size.height

    // Dusky wash blobs
    drawCircle(Color(0xFF4A2538).copy(alpha = 0.08f), w * 0.35f, Offset(w * 0.20f, h * 0.22f))
    drawCircle(Color(0xFF6B2A5E).copy(alpha = 0.07f), w * 0.32f, Offset(w * 0.82f, h * 0.70f))
    drawCircle(Color(0xFF8A3A4F).copy(alpha = 0.06f), w * 0.25f, Offset(w * 0.60f, h * 0.40f))

    // Smoke plumes
    val smokeColor = Color(0xFF6B5562)
    listOf(0.15f, 0.40f, 0.72f).forEachIndexed { i, fx ->
        val px = w * fx
        val smokePath = Path().apply {
            moveTo(px, h * 0.90f)
            cubicTo(px - 18f, h * 0.82f, px + 22f, h * 0.74f, px - 12f, h * 0.66f)
            cubicTo(px - 30f, h * 0.58f, px + 18f, h * 0.50f, px, h * 0.42f)
            cubicTo(px - 22f, h * 0.34f, px + 14f, h * 0.26f, px - 6f, h * 0.18f)
        }
        drawPath(
            smokePath,
            smokeColor.copy(alpha = (0.09f - i * 0.01f).coerceAtLeast(0.06f)),
            style = Stroke(width = (16f - i * 2f).coerceAtLeast(10f))
        )
    }
    listOf(0.18f, 0.44f, 0.76f).forEach { fx ->
        val px = w * fx
        val p2 = Path().apply {
            moveTo(px, h * 0.86f)
            cubicTo(px + 35f, h * 0.78f, px - 10f, h * 0.70f, px + 22f, h * 0.62f)
            cubicTo(px + 40f, h * 0.54f, px - 6f, h * 0.46f, px + 18f, h * 0.38f)
        }
        drawPath(p2, smokeColor.copy(alpha = 0.07f), style = Stroke(width = 7f))
    }

    // Cigarette with ember
    val cigStart = Offset(w * 0.06f, h * 0.94f)
    val cigEnd = Offset(w * 0.20f, h * 0.90f)
    drawLine(Color(0xFFF5EEDD).copy(alpha = 0.24f), cigStart, cigEnd, strokeWidth = 7f)
    drawLine(
        Color(0xFFC9A46B).copy(alpha = 0.28f),
        cigStart,
        Offset(cigStart.x + (cigEnd.x - cigStart.x) * 0.28f, cigStart.y + (cigEnd.y - cigStart.y) * 0.28f),
        strokeWidth = 7f
    )
    drawCircle(Color(0xFFF0997B).copy(alpha = 0.5f), 8f, cigEnd)
    drawCircle(Color(0xFFF0997B).copy(alpha = 0.15f), 16f, cigEnd)

    // Wine bottle silhouette
    val bottleColor = Color(0xFF4A2538)
    val bx = w * 0.88f
    drawRoundRect(
        bottleColor.copy(alpha = 0.22f),
        topLeft = Offset(bx - 17f, h * 0.42f),
        size = Size(34f, h * 0.12f),
        cornerRadius = CornerRadius(6f, 6f)
    )
    drawRoundRect(
        bottleColor.copy(alpha = 0.22f),
        topLeft = Offset(bx - 5f, h * 0.37f),
        size = Size(10f, h * 0.055f),
        cornerRadius = CornerRadius(2f, 2f)
    )
    drawRoundRect(
        Color(0xFF2A1020).copy(alpha = 0.28f),
        topLeft = Offset(bx - 9f, h * 0.36f),
        size = Size(18f, 12f),
        cornerRadius = CornerRadius(2f, 2f)
    )
    drawOval(
        Color(0xFFF5E5C4).copy(alpha = 0.15f),
        topLeft = Offset(bx - 14f, h * 0.475f - 5f),
        size = Size(28f, 10f)
    )

    // Wine glass (simplified)
    val gx = w * 0.78f
    val bowlTop = h * 0.48f
    val bowlBottom = h * 0.56f
    val bowlPath = Path().apply {
        moveTo(gx - 30f, bowlTop)
        quadraticTo(gx - 30f, bowlBottom, gx, bowlBottom + 10f)
        quadraticTo(gx + 30f, bowlBottom, gx + 30f, bowlTop)
        close()
    }
    drawPath(bowlPath, Color(0xFF7A2040).copy(alpha = 0.20f))
    drawLine(
        Color(0xFF7A2040).copy(alpha = 0.20f),
        Offset(gx, bowlBottom + 10f),
        Offset(gx, h * 0.62f),
        strokeWidth = 2.5f
    )
    drawOval(
        Color(0xFF7A2040).copy(alpha = 0.20f),
        topLeft = Offset(gx - 22f, h * 0.62f - 3f),
        size = Size(44f, 6f)
    )
    drawOval(
        Color(0xFFC73E60).copy(alpha = 0.30f),
        topLeft = Offset(gx - 22f, bowlTop),
        size = Size(44f, 10f)
    )

    // Wilted cannabis leaves
    val leafColor = Color(0xFF27500A).copy(alpha = 0.16f)
    listOf(
        Triple(0.32f, 0.68f, -0.4f),
        Triple(0.52f, 0.78f, 0.3f),
        Triple(0.68f, 0.62f, -0.2f)
    ).forEach { (fx, fy, tilt) ->
        val cx = w * fx
        val cy = h * fy
        for (k in 0..6) {
            val baseAngle = -PI / 2 + (k - 3) * 0.35
            val tilted = baseAngle + tilt
            val len = 28f - kotlin.math.abs(k - 3) * 5f
            val ex = cx + cos(tilted).toFloat() * len
            val ey = cy + sin(tilted).toFloat() * len
            drawLine(leafColor, Offset(cx, cy), Offset(ex, ey), strokeWidth = 3f)
        }
        drawCircle(leafColor, 3f, Offset(cx, cy))
    }

    // Scattered ash dots
    listOf(
        Triple(0.12f, 0.14f, 3f),
        Triple(0.22f, 0.08f, 2f),
        Triple(0.32f, 0.18f, 2.5f),
        Triple(0.46f, 0.12f, 3f),
        Triple(0.58f, 0.06f, 2f),
        Triple(0.66f, 0.18f, 2.5f),
        Triple(0.44f, 0.26f, 2f)
    ).forEach { (fx, fy, r) ->
        drawCircle(Color(0xFF6B5562).copy(alpha = 0.22f), r, Offset(w * fx, h * fy))
    }

    // Drip / fall lines
    drawLine(Color(0xFF4A2538).copy(alpha = 0.12f), Offset(w * 0.10f, h * 0.34f), Offset(w * 0.10f, h * 0.42f), strokeWidth = 1.5f)
    drawLine(Color(0xFF4A2538).copy(alpha = 0.10f), Offset(w * 0.30f, h * 0.40f), Offset(w * 0.30f, h * 0.47f), strokeWidth = 1.5f)
    drawLine(Color(0xFF4A2538).copy(alpha = 0.10f), Offset(w * 0.55f, h * 0.30f), Offset(w * 0.55f, h * 0.39f), strokeWidth = 1.5f)

    // Faint crescent moon
    drawCircle(Color(0xFFF5E5C4).copy(alpha = 0.15f), 32f, Offset(w * 0.92f, h * 0.12f))
    drawCircle(Color(0xFFDDC9D4).copy(alpha = 0.9f), 28f, Offset(w * 0.94f, h * 0.11f))
}

// ─── Water: Layered Waves, Koi Silhouettes, Ripples ────────────────────────

private fun DrawScope.drawWaterBackground() {
    val w = size.width
    val h = size.height

    // Distant mist hills
    val mistPath = Path().apply {
        moveTo(0f, h * 0.25f)
        cubicTo(w * 0.15f, h * 0.18f, w * 0.35f, h * 0.22f, w * 0.5f, h * 0.20f)
        cubicTo(w * 0.7f, h * 0.17f, w * 0.85f, h * 0.23f, w, h * 0.21f)
        lineTo(w, h * 0.30f)
        cubicTo(w * 0.8f, h * 0.28f, w * 0.5f, h * 0.32f, 0f, h * 0.27f)
        close()
    }
    drawPath(mistPath, Color(0xFF85B7EB).copy(alpha = 0.12f))

    // Layered wave bands
    for (layer in 0..3) {
        val baseY = h * (0.70f + layer * 0.07f)
        val amplitude = 18f - layer * 3f
        val period = 100f + layer * 30f
        val alpha = 0.18f - layer * 0.03f
        val path = Path().apply {
            moveTo(0f, baseY)
            var x = 0f
            while (x <= w) {
                val y = baseY + sin((x + layer * 60f).toDouble() * PI / period).toFloat() * amplitude
                lineTo(x, y)
                x += 4f
            }
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(path, Color(0xFF85B7EB).copy(alpha = alpha))
    }

    // Koi fish silhouettes
    drawKoiFish(Offset(w * 0.25f, h * 0.75f), 28f, -0.3f, Color(0xFFF0997B).copy(alpha = 0.16f))
    drawKoiFish(Offset(w * 0.65f, h * 0.82f), 22f, 0.5f, Color(0xFFF0997B).copy(alpha = 0.13f))

    // Circular ripples
    val rippleColor = Color(0xFF2A5A80).copy(alpha = 0.10f)
    listOf(
        Offset(w * 0.3f, h * 0.68f) to listOf(12f, 22f, 34f),
        Offset(w * 0.75f, h * 0.72f) to listOf(8f, 16f, 26f),
    ).forEach { (center, radii) ->
        radii.forEach { r -> drawCircle(rippleColor, r, center, style = Stroke(width = 1.2f)) }
    }

    // Cherry blossom petals drifting
    val petalColor = Color(0xFFED93B1).copy(alpha = 0.20f)
    listOf(
        Offset(w * 0.88f, h * 0.06f) to 10f,
        Offset(w * 0.82f, h * 0.10f) to 7f,
        Offset(w * 0.14f, h * 0.12f) to 8f,
        Offset(w * 0.08f, h * 0.18f) to 6f,
        Offset(w * 0.50f, h * 0.04f) to 5f,
        Offset(w * 0.35f, h * 0.14f) to 4f,
        Offset(w * 0.72f, h * 0.16f) to 5f,
    ).forEach { (pos, r) -> drawCircle(petalColor, r, pos) }

    // Bamboo stalk accent (left edge)
    val bambooColor = Color(0xFF5A8EB0).copy(alpha = 0.14f)
    drawLine(bambooColor, Offset(w * 0.04f, h), Offset(w * 0.04f, h * 0.15f), strokeWidth = 7f)
    // Nodes
    listOf(0.35f, 0.50f, 0.65f, 0.80f).forEach { frac ->
        drawLine(bambooColor, Offset(w * 0.02f, h * frac), Offset(w * 0.06f, h * frac), strokeWidth = 2f)
    }
    // Leaves
    drawBambooLeaf(Offset(w * 0.04f, h * 0.35f), 25f, -0.6f, bambooColor)
    drawBambooLeaf(Offset(w * 0.04f, h * 0.50f), 20f, 0.4f, bambooColor)
}

// ─── Food: Bamboo Grove, Noren Curtain, Steam Wisps ─────────────────────────

private fun DrawScope.drawFoodBackground() {
    val w = size.width
    val h = size.height
    val color = Color(0xFF27500A).copy(alpha = 0.12f)

    // Bamboo grove (multiple stalks with nodes and leaves)
    listOf(0.88f, 0.93f, 0.96f).forEachIndexed { i, xFrac ->
        val thickness = 8f - i * 1.5f
        val topY = h * (0.12f + i * 0.08f)
        drawLine(color, Offset(w * xFrac, h), Offset(w * xFrac, topY), strokeWidth = thickness)
        // Nodes
        val nodeCount = 4 - i
        for (n in 1..nodeCount) {
            val ny = topY + (h - topY) * n / (nodeCount + 1)
            drawLine(color, Offset(w * xFrac - 6f, ny), Offset(w * xFrac + 6f, ny), strokeWidth = 2f)
        }
        // Leaves
        val leafColor = Color(0xFF27500A).copy(alpha = 0.10f)
        drawBambooLeaf(Offset(w * xFrac, topY + (h - topY) * 0.25f), 22f, if (i % 2 == 0) -0.5f else 0.5f, leafColor)
        drawBambooLeaf(Offset(w * xFrac, topY + (h - topY) * 0.55f), 18f, if (i % 2 == 0) 0.4f else -0.4f, leafColor)
    }

    // Noren curtain (top)
    val norenBar = Color(0xFF27500A).copy(alpha = 0.12f)
    drawRoundRect(norenBar, Offset(w * 0.05f, 0f), Size(w * 0.55f, h * 0.015f), CornerRadius(4f))
    val norenColor = Color(0xFF27500A).copy(alpha = 0.09f)
    for (i in 0..4) {
        val cx = w * 0.08f + i * (w * 0.11f)
        val stripPath = Path().apply {
            moveTo(cx, h * 0.015f)
            cubicTo(cx - 8f, h * 0.04f, cx + 8f, h * 0.07f, cx - 3f, h * 0.10f)
            lineTo(cx + w * 0.06f - 3f, h * 0.10f)
            cubicTo(cx + w * 0.06f + 8f, h * 0.07f, cx + w * 0.06f - 8f, h * 0.04f, cx + w * 0.06f, h * 0.015f)
            close()
        }
        drawPath(stripPath, norenColor)
    }

    // Steam wisps
    val steamColor = Color(0xFF27500A).copy(alpha = 0.07f)
    listOf(0.15f, 0.30f).forEach { xFrac ->
        val steamPath = Path().apply {
            moveTo(w * xFrac, h * 0.92f)
            cubicTo(w * xFrac - 12f, h * 0.87f, w * xFrac + 15f, h * 0.82f, w * xFrac - 8f, h * 0.77f)
            cubicTo(w * xFrac - 20f, h * 0.73f, w * xFrac + 10f, h * 0.68f, w * xFrac, h * 0.64f)
        }
        drawPath(steamPath, steamColor, style = Stroke(width = 3f))
    }

    // Chopstick pair
    val chopColor = Color(0xFF27500A).copy(alpha = 0.08f)
    drawLine(chopColor, Offset(w * 0.20f, h * 0.92f), Offset(w * 0.30f, h * 0.80f), strokeWidth = 2.5f)
    drawLine(chopColor, Offset(w * 0.22f, h * 0.92f), Offset(w * 0.32f, h * 0.80f), strokeWidth = 2.5f)
}

// ─── Sleep: Night Sky, Moon, Clouds, Stars, Lantern ─────────────────────────

private fun DrawScope.drawSleepBackground() {
    val w = size.width
    val h = size.height

    // Layered mountain silhouettes
    val mt1 = Path().apply {
        moveTo(0f, h * 0.80f)
        cubicTo(w * 0.15f, h * 0.68f, w * 0.30f, h * 0.74f, w * 0.45f, h * 0.70f)
        cubicTo(w * 0.55f, h * 0.67f, w * 0.70f, h * 0.72f, w * 0.85f, h * 0.68f)
        cubicTo(w * 0.95f, h * 0.65f, w, h * 0.70f, w, h * 0.75f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(mt1, Color(0xFF3C3489).copy(alpha = 0.12f))

    val mt2 = Path().apply {
        moveTo(0f, h * 0.87f)
        cubicTo(w * 0.2f, h * 0.80f, w * 0.5f, h * 0.84f, w * 0.7f, h * 0.82f)
        cubicTo(w * 0.85f, h * 0.80f, w * 0.95f, h * 0.85f, w, h * 0.88f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(mt2, Color(0xFF3C3489).copy(alpha = 0.09f))

    // Crescent moon with glow
    drawCircle(Color.White.copy(alpha = 0.12f), 55f, Offset(w * 0.80f, h * 0.08f))
    drawCircle(Color.White.copy(alpha = 0.20f), 38f, Offset(w * 0.80f, h * 0.08f))
    drawCircle(Color(0xFFCCC8EE).copy(alpha = 0.95f), 30f, Offset(w * 0.83f, h * 0.065f))

    // Cloud wisps
    val cloudColor = Color.White.copy(alpha = 0.10f)
    listOf(
        Triple(0.10f, 0.14f, 80f),
        Triple(0.55f, 0.20f, 60f),
        Triple(0.30f, 0.10f, 50f),
    ).forEach { (xFrac, yFrac, width) ->
        drawRoundRect(cloudColor, Offset(w * xFrac, h * yFrac), Size(width, 12f), CornerRadius(6f))
        drawRoundRect(cloudColor, Offset(w * xFrac + width * 0.2f, h * yFrac - 8f), Size(width * 0.6f, 10f), CornerRadius(5f))
    }

    // Stars with cross rays for bright ones
    val starColor = Color.White.copy(alpha = 0.22f)
    val dimStar = Color.White.copy(alpha = 0.14f)
    listOf(
        Triple(0.12f, 0.04f, 3.5f), Triple(0.28f, 0.08f, 2.5f),
        Triple(0.48f, 0.03f, 4f), Triple(0.62f, 0.11f, 2f),
        Triple(0.08f, 0.22f, 2.5f), Triple(0.38f, 0.16f, 3f),
        Triple(0.72f, 0.05f, 2f), Triple(0.92f, 0.18f, 3f),
        Triple(0.55f, 0.13f, 1.5f), Triple(0.20f, 0.19f, 2f),
    ).forEach { (xFrac, yFrac, radius) ->
        val pos = Offset(w * xFrac, h * yFrac)
        drawCircle(if (radius > 2.5f) starColor else dimStar, radius, pos)
        if (radius >= 3.5f) {
            val rayLen = radius * 2.5f
            drawLine(starColor, Offset(pos.x - rayLen, pos.y), Offset(pos.x + rayLen, pos.y), strokeWidth = 0.8f)
            drawLine(starColor, Offset(pos.x, pos.y - rayLen), Offset(pos.x, pos.y + rayLen), strokeWidth = 0.8f)
        }
    }

    // Paper lantern
    val lanternColor = Color(0xFFF5E5C4).copy(alpha = 0.15f)
    val lx = w * 0.12f
    val ly = h * 0.70f
    drawLine(Color(0xFF3C3489).copy(alpha = 0.08f), Offset(lx, ly - 30f), Offset(lx, ly), strokeWidth = 1f)
    drawOval(lanternColor, Offset(lx - 12f, ly), Size(24f, 32f))
    drawCircle(Color(0xFFF5E5C4).copy(alpha = 0.08f), 25f, Offset(lx, ly + 16f))
}

// ─── Emotions: Sakura Tree, Petal Shower, Wind, Hills ───────────────────────

private fun DrawScope.drawEmotionsBackground() {
    val w = size.width
    val h = size.height

    // Rolling hills
    val hillPath = Path().apply {
        moveTo(0f, h * 0.88f)
        cubicTo(w * 0.2f, h * 0.82f, w * 0.4f, h * 0.86f, w * 0.6f, h * 0.83f)
        cubicTo(w * 0.8f, h * 0.80f, w * 0.9f, h * 0.85f, w, h * 0.87f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(hillPath, Color(0xFF633806).copy(alpha = 0.07f))

    // Sakura tree trunk and branches
    val branchColor = Color(0xFF633806).copy(alpha = 0.16f)
    val thinBranch = Color(0xFF633806).copy(alpha = 0.11f)
    drawLine(branchColor, Offset(w * 0.82f, h * 0.88f), Offset(w * 0.78f, h * 0.35f), strokeWidth = 8f)
    drawLine(branchColor, Offset(w * 0.78f, h * 0.35f), Offset(w * 0.60f, h * 0.18f), strokeWidth = 5f)
    drawLine(branchColor, Offset(w * 0.78f, h * 0.45f), Offset(w * 0.92f, h * 0.25f), strokeWidth = 4f)
    drawLine(thinBranch, Offset(w * 0.78f, h * 0.55f), Offset(w * 0.65f, h * 0.40f), strokeWidth = 3f)
    drawLine(thinBranch, Offset(w * 0.68f, h * 0.26f), Offset(w * 0.55f, h * 0.15f), strokeWidth = 2f)
    drawLine(thinBranch, Offset(w * 0.72f, h * 0.22f), Offset(w * 0.78f, h * 0.12f), strokeWidth = 2f)
    drawLine(thinBranch, Offset(w * 0.88f, h * 0.30f), Offset(w * 0.95f, h * 0.18f), strokeWidth = 2f)

    // Blossom clusters at branch tips
    val blossomColor = Color(0xFFED93B1).copy(alpha = 0.22f)
    val blossomLight = Color(0xFFF5C4D8).copy(alpha = 0.16f)
    listOf(
        Offset(w * 0.60f, h * 0.17f) to 16f, Offset(w * 0.55f, h * 0.14f) to 12f,
        Offset(w * 0.64f, h * 0.20f) to 10f, Offset(w * 0.92f, h * 0.24f) to 14f,
        Offset(w * 0.96f, h * 0.20f) to 10f, Offset(w * 0.88f, h * 0.28f) to 11f,
        Offset(w * 0.65f, h * 0.39f) to 12f, Offset(w * 0.62f, h * 0.43f) to 9f,
        Offset(w * 0.78f, h * 0.11f) to 10f, Offset(w * 0.95f, h * 0.16f) to 8f,
    ).forEach { (pos, r) -> drawCircle(blossomColor, r, pos) }
    listOf(
        Offset(w * 0.59f, h * 0.16f) to 8f,
        Offset(w * 0.93f, h * 0.23f) to 7f,
        Offset(w * 0.64f, h * 0.40f) to 6f,
    ).forEach { (pos, r) -> drawCircle(blossomLight, r, pos) }

    // Wind-scattered petals across screen
    val petalColor = Color(0xFFED93B1).copy(alpha = 0.18f)
    listOf(
        Offset(w * 0.10f, h * 0.20f) to 5f, Offset(w * 0.22f, h * 0.35f) to 4f,
        Offset(w * 0.18f, h * 0.50f) to 6f, Offset(w * 0.35f, h * 0.28f) to 3f,
        Offset(w * 0.42f, h * 0.45f) to 5f, Offset(w * 0.08f, h * 0.65f) to 4f,
        Offset(w * 0.30f, h * 0.60f) to 3f, Offset(w * 0.50f, h * 0.55f) to 4f,
        Offset(w * 0.15f, h * 0.78f) to 5f, Offset(w * 0.40f, h * 0.72f) to 3f,
    ).forEach { (pos, r) -> drawCircle(petalColor, r, pos) }

    // Wind lines
    val windColor = Color(0xFF633806).copy(alpha = 0.06f)
    listOf(
        Offset(w * 0.05f, h * 0.30f) to Offset(w * 0.15f, h * 0.28f),
        Offset(w * 0.25f, h * 0.42f) to Offset(w * 0.38f, h * 0.40f),
        Offset(w * 0.10f, h * 0.55f) to Offset(w * 0.22f, h * 0.53f),
    ).forEach { (start, end) -> drawLine(windColor, start, end, strokeWidth = 1.5f) }
}

// ─── Bathroom: Zen Garden, Raked Patterns, Shishi-odoshi ────────────────────

private fun DrawScope.drawBathroomBackground() {
    val w = size.width
    val h = size.height
    val fineColor = Color(0xFF72243E).copy(alpha = 0.07f)

    // Raked sand — parallel horizontal lines
    for (i in 0..12) {
        val y = h * 0.65f + i * 18f
        drawLine(fineColor, Offset(0f, y), Offset(w, y), strokeWidth = 0.8f)
    }

    // Raked circles around main stone
    val stoneCenter1 = Offset(w * 0.65f, h * 0.78f)
    for (r in 1..6) {
        drawCircle(fineColor, 18f + r * 14f, stoneCenter1, style = Stroke(width = 0.8f))
    }

    // Raked circles around secondary stone
    val stoneCenter2 = Offset(w * 0.25f, h * 0.82f)
    for (r in 1..4) {
        drawCircle(fineColor, 12f + r * 12f, stoneCenter2, style = Stroke(width = 0.8f))
    }

    // Stones
    val stoneColor = Color(0xFF72243E).copy(alpha = 0.14f)
    val stoneDark = Color(0xFF72243E).copy(alpha = 0.11f)
    drawOval(stoneColor, Offset(stoneCenter1.x - 18f, stoneCenter1.y - 12f), Size(36f, 24f))
    drawOval(stoneDark, Offset(stoneCenter1.x + 14f, stoneCenter1.y - 6f), Size(16f, 12f))
    drawOval(stoneColor, Offset(stoneCenter2.x - 14f, stoneCenter2.y - 10f), Size(28f, 20f))
    drawCircle(stoneDark, 6f, Offset(w * 0.45f, h * 0.85f))

    // Shishi-odoshi
    val bambooColor = Color(0xFF72243E).copy(alpha = 0.11f)
    drawLine(bambooColor, Offset(w * 0.90f, h * 0.45f), Offset(w * 0.90f, h * 0.65f), strokeWidth = 4f)
    drawLine(bambooColor, Offset(w * 0.85f, h * 0.50f), Offset(w * 0.95f, h * 0.50f), strokeWidth = 3f)
    drawLine(Color(0xFF85B7EB).copy(alpha = 0.10f), Offset(w * 0.95f, h * 0.50f), Offset(w * 0.95f, h * 0.56f), strokeWidth = 1.5f)
    drawOval(bambooColor, Offset(w * 0.92f, h * 0.56f), Size(16f, 8f))

    // Bamboo fence (top area)
    val fenceColor = Color(0xFF72243E).copy(alpha = 0.08f)
    for (i in 0..8) {
        val x = w * 0.02f + i * (w * 0.04f)
        drawLine(fenceColor, Offset(x, h * 0.08f), Offset(x, h * 0.18f), strokeWidth = 2.5f)
    }
    drawLine(fenceColor, Offset(w * 0.02f, h * 0.11f), Offset(w * 0.34f, h * 0.11f), strokeWidth = 1.5f)
    drawLine(fenceColor, Offset(w * 0.02f, h * 0.15f), Offset(w * 0.34f, h * 0.15f), strokeWidth = 1.5f)
}

// ─── Health: Bonsai, Lotus Pond, Stepping Stones ────────────────────────────

private fun DrawScope.drawHealthBackground() {
    val w = size.width
    val h = size.height

    // Stepping stones path
    val stoneColor = Color(0xFF085041).copy(alpha = 0.09f)
    listOf(
        Offset(w * 0.08f, h * 0.92f) to Size(28f, 18f),
        Offset(w * 0.18f, h * 0.87f) to Size(24f, 16f),
        Offset(w * 0.30f, h * 0.90f) to Size(26f, 17f),
        Offset(w * 0.42f, h * 0.85f) to Size(22f, 15f),
    ).forEach { (pos, sz) -> drawOval(stoneColor, Offset(pos.x - sz.width / 2, pos.y - sz.height / 2), sz) }

    // Bonsai tree
    val trunkColor = Color(0xFF085041).copy(alpha = 0.15f)
    drawRoundRect(Color(0xFF085041).copy(alpha = 0.12f), Offset(w * 0.72f, h * 0.62f), Size(w * 0.12f, h * 0.03f), CornerRadius(4f))
    val trunkPath = Path().apply {
        moveTo(w * 0.78f, h * 0.62f)
        cubicTo(w * 0.76f, h * 0.55f, w * 0.80f, h * 0.48f, w * 0.77f, h * 0.40f)
    }
    drawPath(trunkPath, trunkColor, style = Stroke(width = 6f))
    drawLine(trunkColor, Offset(w * 0.77f, h * 0.45f), Offset(w * 0.68f, h * 0.38f), strokeWidth = 3f)
    drawLine(trunkColor, Offset(w * 0.77f, h * 0.42f), Offset(w * 0.86f, h * 0.36f), strokeWidth = 3f)
    drawLine(trunkColor, Offset(w * 0.77f, h * 0.50f), Offset(w * 0.70f, h * 0.48f), strokeWidth = 2f)

    // Foliage clouds
    val foliageColor = Color(0xFF085041).copy(alpha = 0.12f)
    val foliageLight = Color(0xFF5DCAA5).copy(alpha = 0.10f)
    listOf(
        Offset(w * 0.77f, h * 0.38f) to 22f, Offset(w * 0.73f, h * 0.36f) to 18f,
        Offset(w * 0.81f, h * 0.37f) to 16f, Offset(w * 0.68f, h * 0.36f) to 15f,
        Offset(w * 0.86f, h * 0.34f) to 14f, Offset(w * 0.75f, h * 0.33f) to 13f,
        Offset(w * 0.70f, h * 0.40f) to 12f, Offset(w * 0.84f, h * 0.38f) to 11f,
    ).forEach { (pos, r) -> drawCircle(foliageColor, r, pos) }
    listOf(
        Offset(w * 0.76f, h * 0.35f) to 10f,
        Offset(w * 0.82f, h * 0.35f) to 8f,
    ).forEach { (pos, r) -> drawCircle(foliageLight, r, pos) }

    // Lotus pond
    drawOval(Color(0xFF85B7EB).copy(alpha = 0.08f), Offset(w * 0.02f, h * 0.72f), Size(w * 0.30f, h * 0.10f))

    // Lotus flowers
    val lotusColor = Color(0xFFED93B1).copy(alpha = 0.16f)
    val lotusLight = Color(0xFFF5C4D8).copy(alpha = 0.12f)
    drawLotusFlower(Offset(w * 0.12f, h * 0.75f), 14f, lotusColor, lotusLight)
    drawLotusFlower(Offset(w * 0.22f, h * 0.77f), 11f, lotusColor, lotusLight)

    // Lily pads
    val padColor = Color(0xFF085041).copy(alpha = 0.09f)
    listOf(
        Offset(w * 0.08f, h * 0.78f) to 10f,
        Offset(w * 0.18f, h * 0.80f) to 8f,
        Offset(w * 0.28f, h * 0.76f) to 9f,
    ).forEach { (pos, r) -> drawCircle(padColor, r, pos) }
}

// ─── Interactions: Bridge, Pagoda, Lanterns, Willow ─────────────────────────

private fun DrawScope.drawInteractionsBackground() {
    val w = size.width
    val h = size.height
    val color = Color(0xFF712B13).copy(alpha = 0.12f)

    // River/stream
    val riverPath = Path().apply {
        moveTo(0f, h * 0.84f)
        var x = 0f
        while (x <= w) {
            lineTo(x, h * 0.84f + sin(x.toDouble() * PI / 150).toFloat() * 5f)
            x += 4f
        }
        lineTo(w, h * 0.90f)
        x = w
        while (x >= 0f) {
            lineTo(x, h * 0.90f + sin(x.toDouble() * PI / 130).toFloat() * 4f)
            x -= 4f
        }
        close()
    }
    drawPath(riverPath, Color(0xFF85B7EB).copy(alpha = 0.09f))

    // Arched bridge with railing
    val bridgePath = Path().apply {
        moveTo(w * 0.15f, h * 0.88f)
        quadraticTo(w * 0.35f, h * 0.72f, w * 0.55f, h * 0.88f)
    }
    drawPath(bridgePath, color, style = Stroke(width = 5f))
    val railPath = Path().apply {
        moveTo(w * 0.17f, h * 0.865f)
        quadraticTo(w * 0.35f, h * 0.71f, w * 0.53f, h * 0.865f)
    }
    drawPath(railPath, color.copy(alpha = 0.08f), style = Stroke(width = 2f))
    for (frac in listOf(0.22f, 0.28f, 0.35f, 0.42f, 0.48f)) {
        val archHeight = 1f - 4f * (frac - 0.35f) * (frac - 0.35f)
        val topY = (h * 0.88f - archHeight * h * 0.12f).coerceAtMost(h * 0.88f)
        drawLine(color.copy(alpha = 0.07f), Offset(w * frac, topY), Offset(w * frac, h * 0.88f), strokeWidth = 1.5f)
    }

    // Multi-tier pagoda
    val pagodaColor = Color(0xFF712B13).copy(alpha = 0.12f)
    val px = w * 0.85f
    val py = h * 0.50f
    drawLine(pagodaColor, Offset(px, py + 120f), Offset(px, py), strokeWidth = 4f)
    for (i in 0..2) {
        val tierY = py + i * 35f
        val tierW = 35f - i * 6f
        drawLine(pagodaColor, Offset(px - tierW, tierY), Offset(px + tierW, tierY), strokeWidth = 3f)
        val eavePath = Path().apply {
            moveTo(px - tierW - 8f, tierY + 2f)
            quadraticTo(px, tierY + 8f, px + tierW + 8f, tierY + 2f)
        }
        drawPath(eavePath, pagodaColor.copy(alpha = 0.08f), style = Stroke(width = 1.5f))
    }
    drawLine(pagodaColor, Offset(px, py), Offset(px, py - 20f), strokeWidth = 2f)

    // Hanging lanterns
    val lanternColor = Color(0xFFF0997B).copy(alpha = 0.14f)
    val stringColor = Color(0xFF712B13).copy(alpha = 0.06f)
    drawLine(stringColor, Offset(w * 0.05f, h * 0.06f), Offset(w * 0.60f, h * 0.04f), strokeWidth = 1f)
    listOf(0.12f, 0.25f, 0.38f, 0.50f).forEach { frac ->
        val lx = w * frac
        val ly = h * 0.05f + (if (frac < 0.3f) 4f else -2f)
        drawLine(stringColor, Offset(lx, ly), Offset(lx, ly + 12f), strokeWidth = 0.8f)
        drawOval(lanternColor, Offset(lx - 5f, ly + 12f), Size(10f, 14f))
    }

    // Willow branches
    val willowColor = Color(0xFF085041).copy(alpha = 0.09f)
    listOf(
        Pair(Offset(w * 0.04f, h * 0.05f), Offset(w * 0.08f, h * 0.30f)),
        Pair(Offset(w * 0.06f, h * 0.04f), Offset(w * 0.12f, h * 0.28f)),
        Pair(Offset(w * 0.03f, h * 0.06f), Offset(w * 0.02f, h * 0.32f)),
        Pair(Offset(w * 0.07f, h * 0.03f), Offset(w * 0.15f, h * 0.25f)),
    ).forEach { (start, end) ->
        val path = Path().apply {
            moveTo(start.x, start.y)
            cubicTo(start.x + 10f, (start.y + end.y) / 2, end.x - 5f, end.y - 20f, end.x, end.y)
        }
        drawPath(path, willowColor, style = Stroke(width = 1.5f))
    }

    // Cherry blossom accents
    val petalColor = Color(0xFFED93B1).copy(alpha = 0.16f)
    listOf(
        Offset(w * 0.93f, h * 0.10f) to 7f,
        Offset(w * 0.88f, h * 0.14f) to 5f,
        Offset(w * 0.96f, h * 0.06f) to 4f,
    ).forEach { (pos, r) -> drawCircle(petalColor, r, pos) }
}

// ─── Chores: Elaborate Raked Garden, Stone Arrangements, Fence ──────────────

private fun DrawScope.drawChoresBackground() {
    val w = size.width
    val h = size.height
    val color = Color(0xFF444441).copy(alpha = 0.10f)
    val fineColor = Color(0xFF444441).copy(alpha = 0.06f)

    // Bamboo fence (top border)
    val fenceColor = Color(0xFF444441).copy(alpha = 0.08f)
    for (i in 0..20) {
        val x = i * (w / 20f)
        drawLine(fenceColor, Offset(x, h * 0.04f), Offset(x, h * 0.14f), strokeWidth = 3f)
    }
    drawLine(fenceColor, Offset(0f, h * 0.07f), Offset(w, h * 0.07f), strokeWidth = 2f)
    drawLine(fenceColor, Offset(0f, h * 0.11f), Offset(w, h * 0.11f), strokeWidth = 2f)

    // Raked sand lines
    for (i in 0..18) {
        val y = h * 0.55f + i * 14f
        drawLine(fineColor, Offset(0f, y), Offset(w, y), strokeWidth = 0.7f)
    }

    // Stone arrangement 1
    val s1 = Offset(w * 0.60f, h * 0.72f)
    drawOval(color, Offset(s1.x - 20f, s1.y - 15f), Size(40f, 30f))
    drawOval(color.copy(alpha = 0.08f), Offset(s1.x + 18f, s1.y - 8f), Size(20f, 16f))
    drawOval(color.copy(alpha = 0.07f), Offset(s1.x - 30f, s1.y - 6f), Size(16f, 12f))
    for (r in 1..5) {
        drawOval(fineColor,
            Offset(s1.x - 20f - r * 12f, s1.y - 15f - r * 8f),
            Size(40f + r * 24f, 30f + r * 16f),
            style = Stroke(width = 0.7f))
    }

    // Stone arrangement 2
    val s2 = Offset(w * 0.20f, h * 0.80f)
    drawOval(color, Offset(s2.x - 12f, s2.y - 10f), Size(24f, 20f))
    drawCircle(color.copy(alpha = 0.07f), 7f, Offset(s2.x + 16f, s2.y))
    for (r in 1..3) {
        drawOval(fineColor,
            Offset(s2.x - 12f - r * 10f, s2.y - 10f - r * 7f),
            Size(24f + r * 20f, 20f + r * 14f),
            style = Stroke(width = 0.7f))
    }

    // Moss patch
    drawOval(Color(0xFF444441).copy(alpha = 0.05f), Offset(w * 0.78f, h * 0.65f), Size(w * 0.15f, h * 0.04f))

    // Broom silhouette
    val broomColor = Color(0xFF444441).copy(alpha = 0.08f)
    drawLine(broomColor, Offset(w * 0.92f, h * 0.88f), Offset(w * 0.88f, h * 0.70f), strokeWidth = 3f)
    for (i in -3..3) {
        drawLine(broomColor, Offset(w * 0.92f, h * 0.88f), Offset(w * 0.92f + i * 4f, h * 0.95f), strokeWidth = 1.5f)
    }
}

// ─── Hobbies: Origami Cranes, Paper Fan, Ink Splash ─────────────────────────

private fun DrawScope.drawHobbiesBackground() {
    val w = size.width
    val h = size.height
    val color = Color(0xFF72243E).copy(alpha = 0.12f)

    // Origami cranes in V formation
    listOf(
        Triple(Offset(w * 0.50f, h * 0.06f), 24f, 0f),
        Triple(Offset(w * 0.38f, h * 0.10f), 20f, -0.15f),
        Triple(Offset(w * 0.62f, h * 0.10f), 20f, 0.15f),
        Triple(Offset(w * 0.28f, h * 0.15f), 16f, -0.25f),
        Triple(Offset(w * 0.72f, h * 0.15f), 16f, 0.25f),
    ).forEach { (pos, sz, tilt) ->
        drawOrigamiCrane(pos, sz, tilt, color)
    }

    // Paper fan (sensu)
    val fanColor = Color(0xFF72243E).copy(alpha = 0.10f)
    val fanCenter = Offset(w * 0.12f, h * 0.88f)
    val fanRadius = 60f
    for (i in 0..8) {
        val angle = -PI / 2 - PI / 6 + i * (PI / 3) / 8
        val endX = fanCenter.x + cos(angle).toFloat() * fanRadius
        val endY = fanCenter.y + sin(angle).toFloat() * fanRadius
        drawLine(fanColor, fanCenter, Offset(endX, endY), strokeWidth = 1f)
    }
    val fanPath = Path().apply {
        val startAngle = -PI / 2 - PI / 6
        moveTo(
            fanCenter.x + cos(startAngle).toFloat() * fanRadius,
            fanCenter.y + sin(startAngle).toFloat() * fanRadius
        )
        for (i in 1..20) {
            val angle = startAngle + i * (PI / 3) / 20
            lineTo(
                fanCenter.x + cos(angle).toFloat() * fanRadius,
                fanCenter.y + sin(angle).toFloat() * fanRadius
            )
        }
    }
    drawPath(fanPath, fanColor, style = Stroke(width = 2f))

    // Ink splash (sumi-e style)
    val inkColor = Color(0xFF72243E).copy(alpha = 0.09f)
    val strokePath = Path().apply {
        moveTo(w * 0.60f, h * 0.85f)
        cubicTo(w * 0.70f, h * 0.80f, w * 0.80f, h * 0.82f, w * 0.92f, h * 0.78f)
    }
    drawPath(strokePath, inkColor, style = Stroke(width = 8f))
    listOf(
        Offset(w * 0.88f, h * 0.75f) to 4f,
        Offset(w * 0.92f, h * 0.80f) to 3f,
        Offset(w * 0.85f, h * 0.82f) to 2f,
        Offset(w * 0.95f, h * 0.76f) to 2.5f,
    ).forEach { (pos, r) -> drawCircle(inkColor, r, pos) }

    // Cherry blossom accents
    val petalColor = Color(0xFFED93B1).copy(alpha = 0.16f)
    listOf(
        Offset(w * 0.90f, h * 0.04f) to 7f, Offset(w * 0.85f, h * 0.08f) to 5f,
        Offset(w * 0.06f, h * 0.45f) to 6f, Offset(w * 0.10f, h * 0.50f) to 4f,
    ).forEach { (pos, r) -> drawCircle(petalColor, r, pos) }

    // Washi paper texture
    val washiColor = Color(0xFF72243E).copy(alpha = 0.05f)
    listOf(
        Offset(w * 0.35f, h * 0.55f) to Size(50f, 65f),
        Offset(w * 0.40f, h * 0.50f) to Size(45f, 60f),
    ).forEach { (pos, sz) ->
        drawRoundRect(washiColor, pos, sz, CornerRadius(2f), style = Stroke(width = 1f))
    }
}

// ─── Ideas: Enso Circle, Scroll, Constellations, Lantern ────────────────────

private fun DrawScope.drawIdeasBackground() {
    val w = size.width
    val h = size.height

    // Enso circle (incomplete zen circle)
    val ensoCenter = Offset(w * 0.55f, h * 0.40f)
    val ensoRadius = w * 0.22f
    val ensoPath = Path().apply {
        val startAngle = -PI * 0.1
        for (i in 0..85) {
            val angle = startAngle + i * (2 * PI * 0.85) / 85
            val x = ensoCenter.x + cos(angle).toFloat() * ensoRadius
            val y = ensoCenter.y + sin(angle).toFloat() * ensoRadius
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    drawPath(ensoPath, Color(0xFF2A3A6B).copy(alpha = 0.14f), style = Stroke(width = 6f))

    // Scroll shape (top right)
    val scrollColor = Color(0xFF2A3A6B).copy(alpha = 0.08f)
    drawRoundRect(scrollColor, Offset(w * 0.72f, h * 0.05f), Size(w * 0.22f, h * 0.25f), CornerRadius(4f))
    drawRoundRect(Color(0xFF2A3A6B).copy(alpha = 0.12f), Offset(w * 0.70f, h * 0.04f), Size(w * 0.26f, 8f), CornerRadius(4f))
    drawRoundRect(Color(0xFF2A3A6B).copy(alpha = 0.12f), Offset(w * 0.70f, h * 0.295f), Size(w * 0.26f, 8f), CornerRadius(4f))
    for (i in 0..5) {
        val y = h * 0.08f + i * (h * 0.035f)
        drawLine(Color(0xFF2A3A6B).copy(alpha = 0.06f), Offset(w * 0.75f, y), Offset(w * 0.90f, y), strokeWidth = 1f)
    }

    // Constellation points and connections
    val starColor = Color(0xFF2A3A6B).copy(alpha = 0.18f)
    val lineAlpha = Color(0xFF2A3A6B).copy(alpha = 0.08f)
    val stars = listOf(
        Offset(w * 0.08f, h * 0.12f), Offset(w * 0.15f, h * 0.08f),
        Offset(w * 0.22f, h * 0.14f), Offset(w * 0.18f, h * 0.20f),
        Offset(w * 0.10f, h * 0.18f),
    )
    for (i in 0 until stars.size - 1) drawLine(lineAlpha, stars[i], stars[i + 1], strokeWidth = 1f)
    drawLine(lineAlpha, stars.last(), stars.first(), strokeWidth = 1f)
    stars.forEach { pos -> drawCircle(starColor, 3f, pos) }

    val stars2 = listOf(
        Offset(w * 0.35f, h * 0.05f), Offset(w * 0.42f, h * 0.10f),
        Offset(w * 0.40f, h * 0.18f),
    )
    for (i in 0 until stars2.size - 1) drawLine(lineAlpha, stars2[i], stars2[i + 1], strokeWidth = 1f)
    stars2.forEach { pos -> drawCircle(starColor, 2.5f, pos) }

    // Floating lantern
    val lanternColor = Color(0xFFF5E5C4).copy(alpha = 0.15f)
    val lx = w * 0.15f
    val ly = h * 0.78f
    drawOval(lanternColor, Offset(lx - 14f, ly), Size(28f, 36f))
    drawCircle(Color(0xFFF5E5C4).copy(alpha = 0.08f), 28f, Offset(lx, ly + 18f))
    drawCircle(Color(0xFFF0997B).copy(alpha = 0.12f), 4f, Offset(lx, ly + 12f))
}

// ─── Cycle: Moon Phases, Waves, Floral Ring ─────────────────────────────────

private fun DrawScope.drawCycleBackground() {
    val w = size.width
    val h = size.height

    // Moon phases arc
    val moonY = h * 0.10f
    val moonSpacing = w / 7f
    val moonRadius = 14f
    val moonColor = Color(0xFF7A2040).copy(alpha = 0.16f)
    val shadowColor = Color(0xFFF0C8D0).copy(alpha = 0.9f)

    drawCircle(moonColor, moonRadius, Offset(moonSpacing, moonY), style = Stroke(width = 1.5f))
    drawCircle(moonColor, moonRadius, Offset(moonSpacing * 2, moonY))
    drawCircle(shadowColor, moonRadius - 3f, Offset(moonSpacing * 2 + 6f, moonY))
    drawCircle(moonColor, moonRadius, Offset(moonSpacing * 3, moonY))
    drawCircle(shadowColor, moonRadius, Offset(moonSpacing * 3 + moonRadius, moonY))
    drawCircle(moonColor, moonRadius, Offset(moonSpacing * 4, moonY))
    drawCircle(Color(0xFFF5C4D8).copy(alpha = 0.10f), moonRadius + 6f, Offset(moonSpacing * 4, moonY))
    drawCircle(moonColor, moonRadius, Offset(moonSpacing * 5, moonY))
    drawCircle(shadowColor, moonRadius, Offset(moonSpacing * 5 - moonRadius, moonY))
    drawCircle(moonColor, moonRadius, Offset(moonSpacing * 6, moonY))
    drawCircle(shadowColor, moonRadius - 3f, Offset(moonSpacing * 6 - 6f, moonY))

    // Gentle wave pattern (bottom)
    val waveColor = Color(0xFF7A2040).copy(alpha = 0.08f)
    for (layer in 0..2) {
        val baseY = h * (0.82f + layer * 0.05f)
        val path = Path().apply {
            moveTo(0f, baseY)
            var x = 0f
            while (x <= w) {
                val y = baseY + sin((x + layer * 50f).toDouble() * PI / 90).toFloat() * 10f
                lineTo(x, y)
                x += 4f
            }
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(path, waveColor)
    }

    // Floral wreath ring (center)
    val wreathCenter = Offset(w * 0.50f, h * 0.48f)
    val wreathRadius = w * 0.20f
    drawCircle(Color(0xFF7A2040).copy(alpha = 0.08f), wreathRadius, wreathCenter, style = Stroke(width = 2f))
    for (i in 0..11) {
        val angle = i * PI * 2 / 12
        val fx = wreathCenter.x + cos(angle).toFloat() * wreathRadius
        val fy = wreathCenter.y + sin(angle).toFloat() * wreathRadius
        drawCircle(Color(0xFFED93B1).copy(alpha = 0.16f), 6f, Offset(fx, fy))
        val lAngle = angle + PI / 8
        drawCircle(Color(0xFF085041).copy(alpha = 0.08f), 4f, Offset(fx + cos(lAngle).toFloat() * 10f, fy + sin(lAngle).toFloat() * 10f))
    }

    // Flowing ribbon
    val ribbonPath = Path().apply {
        moveTo(moonSpacing * 4, moonY + moonRadius)
        cubicTo(w * 0.45f, h * 0.20f, w * 0.55f, h * 0.28f, wreathCenter.x, wreathCenter.y - wreathRadius)
    }
    drawPath(ribbonPath, Color(0xFF7A2040).copy(alpha = 0.07f), style = Stroke(width = 2f))

    // Rose petals scattered
    val petalColor = Color(0xFFED93B1).copy(alpha = 0.13f)
    listOf(
        Offset(w * 0.08f, h * 0.30f) to 5f, Offset(w * 0.88f, h * 0.35f) to 6f,
        Offset(w * 0.12f, h * 0.60f) to 4f, Offset(w * 0.85f, h * 0.55f) to 5f,
        Offset(w * 0.75f, h * 0.70f) to 4f, Offset(w * 0.20f, h * 0.72f) to 3f,
    ).forEach { (pos, r) -> drawCircle(petalColor, r, pos) }
}

// ─── Helper Drawing Functions ───────────────────────────────────────────────

private fun DrawScope.drawKoiFish(center: Offset, bodyLength: Float, angle: Float, color: Color) {
    val cosA = cos(angle.toDouble()).toFloat()
    val sinA = sin(angle.toDouble()).toFloat()
    val headX = center.x + cosA * bodyLength * 0.4f
    val headY = center.y + sinA * bodyLength * 0.4f
    val tailX = center.x - cosA * bodyLength * 0.6f
    val tailY = center.y - sinA * bodyLength * 0.6f
    val perpX = -sinA * bodyLength * 0.2f
    val perpY = cosA * bodyLength * 0.2f

    val path = Path().apply {
        moveTo(headX, headY)
        cubicTo(center.x + perpX, center.y + perpY, tailX + perpX * 0.3f, tailY + perpY * 0.3f, tailX, tailY)
        cubicTo(tailX - perpX * 0.3f, tailY - perpY * 0.3f, center.x - perpX, center.y - perpY, headX, headY)
        close()
    }
    drawPath(path, color)

    val finPath = Path().apply {
        moveTo(tailX, tailY)
        lineTo(tailX - cosA * bodyLength * 0.3f + sinA * bodyLength * 0.15f,
               tailY - sinA * bodyLength * 0.3f - cosA * bodyLength * 0.15f)
        lineTo(tailX - cosA * bodyLength * 0.3f - sinA * bodyLength * 0.15f,
               tailY - sinA * bodyLength * 0.3f + cosA * bodyLength * 0.15f)
        close()
    }
    drawPath(finPath, color)
}

private fun DrawScope.drawBambooLeaf(base: Offset, length: Float, angle: Float, color: Color) {
    val cosA = cos(angle.toDouble()).toFloat()
    val sinA = sin(angle.toDouble()).toFloat()
    val tipX = base.x + cosA * length
    val tipY = base.y + sinA * length
    val perpX = -sinA * length * 0.15f
    val perpY = cosA * length * 0.15f
    val path = Path().apply {
        moveTo(base.x, base.y)
        quadraticTo((base.x + tipX) / 2 + perpX, (base.y + tipY) / 2 + perpY, tipX, tipY)
        quadraticTo((base.x + tipX) / 2 - perpX, (base.y + tipY) / 2 - perpY, base.x, base.y)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawOrigamiCrane(center: Offset, sz: Float, tilt: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - sz * 0.1f)
        lineTo(center.x - sz * (1f + tilt), center.y - sz * 0.7f)
        lineTo(center.x - sz * 0.3f, center.y + sz * 0.05f)
        moveTo(center.x, center.y - sz * 0.1f)
        lineTo(center.x + sz * (1f - tilt), center.y - sz * 0.7f)
        lineTo(center.x + sz * 0.3f, center.y + sz * 0.05f)
        moveTo(center.x - sz * 0.1f, center.y)
        lineTo(center.x - sz * 0.4f, center.y + sz * 0.4f)
        moveTo(center.x + sz * 0.1f, center.y - sz * 0.2f)
        lineTo(center.x + sz * 0.35f, center.y - sz * 0.5f)
    }
    drawPath(path, color, style = Stroke(width = 1.5f))
}

private fun DrawScope.drawLotusFlower(center: Offset, petalSize: Float, color: Color, lightColor: Color) {
    for (i in 0..4) {
        val angle = i * PI * 2 / 5 - PI / 2
        val px = center.x + cos(angle).toFloat() * petalSize * 0.5f
        val py = center.y + sin(angle).toFloat() * petalSize * 0.5f
        drawCircle(color, petalSize * 0.45f, Offset(px, py))
    }
    drawCircle(lightColor, petalSize * 0.3f, center)
    drawCircle(Color(0xFFF5E5C4).copy(alpha = 0.10f), petalSize * 0.2f, center)
}
