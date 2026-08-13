package com.wellnesscompanion.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellnesscompanion.app.data.model.Category

@Composable
fun CategoryCard(
    category: Category,
    summary: String,
    streak: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardScale"
    )

    val cardBg = category.colors.cardBg
    val textColor = category.colors.textColor

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        cardBg.copy(alpha = 0.92f),
                        cardBg.copy(alpha = 0.70f)
                    )
                )
            )
            .border(1.dp, textColor.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                        onClick()
                    }
                )
            }
    ) {
        // Decorative circle in top-right corner
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                Color.White.copy(alpha = 0.18f),
                radius = size.width * 0.32f,
                center = Offset(size.width * 0.88f, size.height * 0.12f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Emoji with colored circle backdrop
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(textColor.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.icon,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            // Category name
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Summary
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )

            // Streak badge \u2014 subtle infinite pulse once the streak is worth celebrating.
            // Quiet cards (streak < 3) never allocate the infinite transition at all.
            if (streak > 0) {
                Spacer(modifier = Modifier.height(3.dp))
                val streakModifier = if (streak >= 3) {
                    val infiniteTransition = rememberInfiniteTransition(label = "streakPulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.06f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "streakPulseScale"
                    )
                    Modifier.scale(pulseScale)
                } else {
                    Modifier
                }
                Text(
                    text = "\uD83D\uDD25 $streak",
                    fontSize = 10.sp,
                    color = textColor.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = streakModifier
                )
            }
        }
    }
}
