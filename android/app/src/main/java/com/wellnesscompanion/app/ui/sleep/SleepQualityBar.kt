package com.wellnesscompanion.app.ui.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SleepQualityBar(
    bedtime: String,
    wakeTime: String,
    wakeUps: List<String>,
    totalHours: Float,
    modifier: Modifier = Modifier
) {
    val sleepColor = Color(0xFFAEA9EC).copy(alpha = 0.45f)
    val wakeColor = Color(0xFFF0997B).copy(alpha = 0.5f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
    ) {
        val w = size.width
        val h = size.height

        // Background bar
        drawRoundRect(
            Color.White.copy(alpha = 0.3f),
            cornerRadius = CornerRadius(h / 2, h / 2),
            size = Size(w, h)
        )

        if (totalHours <= 0f) return@Canvas

        // Compute segments
        val bedMinutes = parseMinutes(bedtime)
        val wakeMinutes = parseMinutes(wakeTime)
        val totalMinutes = if (wakeMinutes > bedMinutes) wakeMinutes - bedMinutes else (1440 - bedMinutes) + wakeMinutes

        if (totalMinutes <= 0) return@Canvas

        // Sort wake-ups
        val wakeUpMinutes = wakeUps.map { parseMinutes(it) }.sorted()

        // Draw sleep bar (full width = total sleep period)
        drawRoundRect(
            sleepColor,
            cornerRadius = CornerRadius(h / 2, h / 2),
            size = Size(w, h)
        )

        // Draw wake-up markers
        wakeUpMinutes.forEach { wuMin ->
            val offset = if (wuMin >= bedMinutes) {
                wuMin - bedMinutes
            } else {
                (1440 - bedMinutes) + wuMin
            }
            val fraction = offset.toFloat() / totalMinutes.toFloat()
            val x = fraction * w
            val markerWidth = w * 0.04f // Each wake-up is ~4% of the bar

            drawRoundRect(
                wakeColor,
                topLeft = Offset(x - markerWidth / 2, 0f),
                size = Size(markerWidth, h),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}

private fun parseMinutes(time: String): Int {
    val parts = time.split(":")
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}
