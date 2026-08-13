package com.wellnesscompanion.app.ui.emotions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val emotionColors = mapOf(
    "happy" to Color(0xFFF5E5C4),
    "calm" to Color(0xFFCCE8DD),
    "sad" to Color(0xFFD4E9F7),
    "angry" to Color(0xFFF5C4B3),
    "anxious" to Color(0xFFF5D6E3),
    "tired" to Color(0xFFDEDCF7)
)

@Composable
fun DayArcBar(
    entries: List<EmotionEntry>,
    modifier: Modifier = Modifier
) {
    // Grow-in: segments sweep in from 0 width on first composition.
    val growth = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        growth.animateTo(1f, animationSpec = tween(600, easing = FastOutSlowInEasing))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
    ) {
        val w = size.width
        val h = size.height

        // Background
        drawRoundRect(
            Color.White.copy(alpha = 0.3f),
            cornerRadius = CornerRadius(h / 2, h / 2),
            size = Size(w, h)
        )

        if (entries.isEmpty()) return@Canvas

        // Map entries to bar segments based on time position in day (6am-midnight)
        val dayStartMin = 6 * 60 // 6:00 AM
        val dayEndMin = 24 * 60  // midnight
        val dayRange = dayEndMin - dayStartMin

        val sorted = entries.sortedBy { it.timestamp }

        sorted.forEachIndexed { index, entry ->
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            val min = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            val startFraction = ((min - dayStartMin).toFloat() / dayRange).coerceIn(0f, 1f)

            val endFraction = if (index < sorted.size - 1) {
                val nextCal = java.util.Calendar.getInstance().apply { timeInMillis = sorted[index + 1].timestamp }
                val nextMin = nextCal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + nextCal.get(java.util.Calendar.MINUTE)
                ((nextMin - dayStartMin).toFloat() / dayRange).coerceIn(0f, 1f)
            } else {
                1f
            }

            val color = emotionColors[entry.emotion] ?: Color.Gray
            val x = startFraction * w
            val segW = (endFraction - startFraction) * w

            if (segW > 0) {
                drawRect(
                    color,
                    topLeft = Offset(x, 0f),
                    size = Size(segW * growth.value, h)
                )
            }
        }

        // Re-draw rounded corners overlay
        drawRoundRect(
            Color.Transparent,
            cornerRadius = CornerRadius(h / 2, h / 2),
            size = Size(w, h)
        )
    }
}
