package com.wellnesscompanion.app.ui.water

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wellnesscompanion.app.data.model.Category
import com.wellnesscompanion.app.ui.components.CategoryWeeklyTrend
import com.wellnesscompanion.app.ui.theme.WaterText
import com.wellnesscompanion.app.ui.theme.WaterSecondary
import com.wellnesscompanion.app.ui.theme.AccentTeal
import com.wellnesscompanion.app.util.formatMl
import com.wellnesscompanion.app.util.formatMlCompact
import com.wellnesscompanion.app.util.mlToFlOz
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun WaterScreen(
    viewModel: WaterViewModel = hiltViewModel()
) {
    val capacity by viewModel.capacity.collectAsState()
    val totalConsumed by viewModel.totalConsumed.collectAsState()
    val bottleFill by viewModel.bottleFill.collectAsState()
    val todayEntries by viewModel.todayEntries.collectAsState()
    val lastDelta by viewModel.lastDelta.collectAsState()

    // Drag state: only downward drag (drinking)
    var isDragging by remember { mutableStateOf(false) }
    var dragFill by remember { mutableFloatStateOf(1f) }
    var dragStartFill by remember { mutableFloatStateOf(1f) }

    val displayFill = if (isDragging) dragFill else bottleFill
    val isNearEmpty = bottleFill <= 0.1f

    // Goal celebration
    val dailyProgress by viewModel.dailyProgress.collectAsState()
    var showCelebration by remember { mutableStateOf(false) }
    var goalWasReached by remember { mutableStateOf(false) }

    LaunchedEffect(dailyProgress) {
        if (dailyProgress >= 1f && !goalWasReached) {
            goalWasReached = true
            showCelebration = true
        }
    }

    // Auto-clear delta message
    LaunchedEffect(lastDelta) {
        if (lastDelta != null) {
            delay(3200)
            viewModel.clearDelta()
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main display
        val animatedConsumed by animateIntAsState(
            targetValue = totalConsumed,
            animationSpec = tween(500),
            label = "consumedCountUp"
        )
        Text(
            text = "$animatedConsumed",
            style = MaterialTheme.typography.displayLarge,
            color = WaterText
        )
        val goalOz = mlToFlOz(viewModel.dailyGoalMl).roundToInt()
        Text(
            "of ${viewModel.dailyGoalMl} ml (${goalOz} fl oz) daily goal",
            style = MaterialTheme.typography.bodyMedium,
            color = WaterSecondary
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Daily goal progress bar
        val dailyProgress by viewModel.dailyProgress.collectAsState()
        val animatedDailyProgress by animateFloatAsState(
            targetValue = dailyProgress,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "dailyProgressBar"
        )
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        ) {
            drawRect(Color.White.copy(alpha = 0.3f))
            drawRect(
                color = if (dailyProgress >= 1f) AccentTeal.copy(alpha = 0.7f) else WaterText.copy(alpha = 0.3f),
                size = size.copy(width = size.width * animatedDailyProgress)
            )
        }
        Text(
            "${(dailyProgress * 100).roundToInt()}% of daily goal",
            style = MaterialTheme.typography.labelSmall,
            color = if (dailyProgress >= 1f) AccentTeal else WaterSecondary.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        val currentMl = (displayFill * capacity).roundToInt()
        val currentOz = mlToFlOz(currentMl).roundToInt()
        val capOz = mlToFlOz(capacity).roundToInt()
        Text(
            "${currentMl}/${capacity} ml (${currentOz}/${capOz} fl oz) in bottle",
            style = MaterialTheme.typography.labelSmall,
            color = WaterSecondary.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Delta pill
        AnimatedVisibility(
            visible = lastDelta != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val isDrink = lastDelta?.startsWith("-") == true
            Text(
                text = lastDelta ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = WaterText,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isDrink) Color(0xFFB4DAF8).copy(alpha = 0.65f)
                        else Color(0xFFA8E4C0).copy(alpha = 0.65f)
                    )
                    .padding(horizontal = 16.dp, vertical = 3.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Water bottle canvas — drag down only (drinking)
        val animatedBottleFill by animateFloatAsState(
            targetValue = displayFill,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "bottleFill"
        )
        // While actively dragging, render the raw finger-tracked value so the fill edge
        // doesn't lag the gesture; the spring still runs underneath and takes over on
        // release (quick-add taps / refill) so those transitions stay smooth.
        val renderedBottleFill = if (isDragging) displayFill else animatedBottleFill
        WaterBottleCanvas(
            fillFraction = renderedBottleFill,
            capacity = capacity,
            onDragDelta = { pixelDelta, canvasHeight ->
                // Only allow downward drag (positive pixelDelta = finger moves down = drink)
                if (pixelDelta > 0 || isDragging) {
                    if (!isDragging) {
                        isDragging = true
                        dragStartFill = bottleFill
                        dragFill = bottleFill
                    }
                    val fractionDelta = pixelDelta / (canvasHeight * 0.75f)
                    // Only decrease (drink), don't allow dragging up past start
                    dragFill = (dragFill - fractionDelta).coerceIn(0f, dragStartFill)
                }
            },
            onDragEnd = {
                if (isDragging) {
                    val ml = ((dragStartFill - dragFill) * capacity).roundToInt()
                    if (ml >= 10) {
                        viewModel.logDrink(ml)
                    }
                    isDragging = false
                }
            },
            modifier = Modifier
                .width(176.dp)
                .height(340.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "\u2193 drag down to drink",
            style = MaterialTheme.typography.labelSmall,
            color = WaterSecondary.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Refill button — prominent when near empty
        val refillInteractionSource = remember { MutableInteractionSource() }
        val isRefillPressed by refillInteractionSource.collectIsPressedAsState()
        val refillScale by animateFloatAsState(
            targetValue = if (isRefillPressed) 0.94f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "refillScale"
        )
        Button(
            onClick = { viewModel.logRefill() },
            enabled = bottleFill < 0.95f,
            interactionSource = refillInteractionSource,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isNearEmpty) AccentTeal.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.5f),
                contentColor = if (isNearEmpty) Color.White else WaterText,
                disabledContainerColor = Color.White.copy(alpha = 0.2f),
                disabledContentColor = WaterSecondary.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .scale(refillScale)
        ) {
            Text(
                if (isNearEmpty) "\uD83D\uDCA7 Refill bottle!" else "\uD83D\uDCA7 Refill bottle",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Capacity picker
        Text("Bottle size", style = MaterialTheme.typography.labelSmall, color = WaterSecondary, letterSpacing = 0.06.sp)
        Spacer(modifier = Modifier.height(4.dp))
        CapacityPicker(
            selectedCapacity = capacity,
            onSelect = { viewModel.setCapacity(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Today's log
        if (todayEntries.isNotEmpty()) {
            Text("Today's log", style = MaterialTheme.typography.labelSmall, color = WaterSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            todayEntries.take(10).forEach { (timestamp, data) ->
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
                val label = if (data.type == "drink") "consumed" else "refilled"
                val bgColor = if (data.type == "drink") Color(0xFFB4DAF8).copy(alpha = 0.3f) else Color(0xFFA8E4C0).copy(alpha = 0.3f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bgColor)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(timeStr, style = MaterialTheme.typography.labelSmall, color = WaterText)
                    val entryOz = mlToFlOz(data.ml).roundToInt()
                    Text("${data.ml}ml (${entryOz}oz) $label", style = MaterialTheme.typography.labelSmall, color = WaterSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Weekly trend
        CategoryWeeklyTrend(category = Category.WATER)

        Spacer(modifier = Modifier.height(60.dp))
    }

    // Celebration overlay when daily goal reached
    com.wellnesscompanion.app.ui.components.CelebrationOverlay(
        visible = showCelebration,
        goalName = "daily water intake",
        onDismiss = { showCelebration = false }
    )
    } // end Box
}

@Composable
fun CapacityPicker(selectedCapacity: Int, onSelect: (Int) -> Unit) {
    val capacities = (200..2000 step 50).toList()
    val listState = rememberLazyListState()

    LaunchedEffect(selectedCapacity) {
        val idx = capacities.indexOf(selectedCapacity)
        if (idx >= 0) listState.animateScrollToItem(idx)
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        items(capacities) { cap ->
            val isSelected = cap == selectedCapacity
            val capPickerOz = mlToFlOz(cap).roundToInt()
            Text(
                text = "${cap}ml / ${capPickerOz}oz",
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) WaterText else WaterSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) Color.White.copy(alpha = 0.72f)
                        else Color.White.copy(alpha = 0.38f)
                    )
                    .clickable { onSelect(cap) }
                    .padding(horizontal = 11.dp, vertical = 4.dp)
            )
        }
    }
}
