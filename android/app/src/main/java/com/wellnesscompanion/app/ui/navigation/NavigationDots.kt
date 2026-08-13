package com.wellnesscompanion.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wellnesscompanion.app.data.model.Category

@Composable
fun NavigationDots(
    currentIndex: Int,
    onHomeTap: () -> Unit,
    onDotTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Home square dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.6f))
                .clickable { onHomeTap() }
        )

        // Category dots
        Screen.categoryScreens.forEachIndexed { index, screen ->
            val isActive = index == currentIndex
            val dotColor = if (isActive) {
                screen.category?.colors?.textColor ?: Color.White
            } else {
                Color.White.copy(alpha = 0.4f)
            }

            val dotWidth by animateDpAsState(
                targetValue = if (isActive) 20.dp else 7.dp,
                animationSpec = spring(dampingRatio = 0.7f),
                label = "dotWidth"
            )
            val dotFillColor by animateColorAsState(
                targetValue = dotColor,
                label = "dotFillColor"
            )

            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .width(dotWidth)
                    .height(7.dp)
                    .clip(if (isActive) RoundedCornerShape(4.dp) else CircleShape)
                    .background(dotFillColor)
                    .clickable { onDotTap(index) }
            )
        }
    }
}
