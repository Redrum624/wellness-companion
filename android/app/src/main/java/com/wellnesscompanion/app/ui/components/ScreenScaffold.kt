package com.wellnesscompanion.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wellnesscompanion.app.data.model.Category
import com.wellnesscompanion.app.ui.navigation.NavigationDots
import com.wellnesscompanion.app.ui.navigation.Screen

@Composable
fun ScreenScaffold(
    category: Category,
    currentIndex: Int,
    onBack: () -> Unit,
    onHomeTap: () -> Unit,
    onDotTap: (Int) -> Unit,
    content: @Composable () -> Unit
) {
    val colors = category.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.screenBg)
    ) {
        // Background decorations
        PastelBackground(category = category)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header with back button and title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.5f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textColor
                )
            }

            // Scrollable content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 0.dp)
            ) {
                content()
            }

            // Bottom navigation dots
            NavigationDots(
                currentIndex = currentIndex,
                onHomeTap = onHomeTap,
                onDotTap = onDotTap,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
