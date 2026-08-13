package com.wellnesscompanion.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellnesscompanion.app.util.StreakTracker

/**
 * A reusable weekly bar chart that shows 7 days of data.
 * [values] — list of 7 floats (one per day, Mon→Sun or last 7 days)
 * [maxValue] — the y-axis max; bars are scaled relative to this
 * [barColor] — the color for the bars
 * [textColor] — color for day labels
 * [title] — optional section title
 */
@Composable
fun WeeklyTrendChart(
    values: List<Float>,
    maxValue: Float,
    barColor: Color,
    textColor: Color,
    title: String? = null,
    modifier: Modifier = Modifier
) {
    val labels = StreakTracker.last7DayLabels()
    val textMeasurer = rememberTextMeasurer()

    // Grow-in: bars rise from the baseline on first composition.
    val growth = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        growth.animateTo(1f, animationSpec = tween(600, easing = FastOutSlowInEasing))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .padding(horizontal = 4.dp)
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        ) {
            drawBarChart(
                values = values,
                labels = labels,
                maxValue = maxValue,
                barColor = barColor,
                textColor = textColor,
                textMeasurer = textMeasurer,
                growth = growth.value
            )
        }
    }
}

private fun DrawScope.drawBarChart(
    values: List<Float>,
    labels: List<String>,
    maxValue: Float,
    barColor: Color,
    textColor: Color,
    textMeasurer: TextMeasurer,
    growth: Float
) {
    val w = size.width
    val h = size.height
    val labelHeight = 16f
    val chartHeight = h - labelHeight - 8f
    val barCount = values.size.coerceAtLeast(1)
    val gap = w * 0.04f
    val barWidth = (w - gap * (barCount + 1)) / barCount
    val safeMax = if (maxValue > 0f) maxValue else 1f

    // Goal line at max
    drawLine(
        barColor.copy(alpha = 0.15f),
        Offset(0f, 4f),
        Offset(w, 4f),
        strokeWidth = 1f
    )

    for (i in values.indices) {
        val x = gap + i * (barWidth + gap)
        val barH = (values[i] / safeMax).coerceIn(0f, 1f) * chartHeight * growth
        val barY = chartHeight - barH + 4f

        // Bar
        drawRoundRect(
            color = barColor.copy(alpha = if (values[i] > 0f) 0.65f else 0.15f),
            topLeft = Offset(x, barY),
            size = Size(barWidth, barH.coerceAtLeast(2f)),
            cornerRadius = CornerRadius(4f, 4f)
        )

        // Day label
        if (i < labels.size) {
            val labelStyle = TextStyle(fontSize = 9.sp, color = textColor.copy(alpha = 0.5f))
            val measured = textMeasurer.measure(labels[i], labelStyle)
            drawText(
                measured,
                topLeft = Offset(x + (barWidth - measured.size.width) / 2, chartHeight + 8f)
            )
        }
    }
}
