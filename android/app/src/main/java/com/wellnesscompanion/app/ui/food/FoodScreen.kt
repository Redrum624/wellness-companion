package com.wellnesscompanion.app.ui.food

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wellnesscompanion.app.data.model.Category
import com.wellnesscompanion.app.ui.components.CategoryWeeklyTrend
import com.wellnesscompanion.app.ui.components.CelebrationOverlay
import com.wellnesscompanion.app.ui.theme.AccentTeal
import com.wellnesscompanion.app.ui.theme.FoodText

@Composable
fun FoodScreen(
    viewModel: FoodViewModel = hiltViewModel()
) {
    val meals by viewModel.meals.collectAsState()
    val loggedCount by viewModel.mealsLoggedCount.collectAsState()
    var editingMeal by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }

    // Goal celebration — all four meal slots logged for today
    var showCelebration by remember { mutableStateOf(false) }
    var goalWasReached by remember { mutableStateOf(false) }

    LaunchedEffect(loggedCount) {
        if (loggedCount >= 4 && !goalWasReached) {
            goalWasReached = true
            showCelebration = true
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "$loggedCount/4 meals logged",
            style = MaterialTheme.typography.bodyMedium,
            color = FoodText.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        meals.forEach { meal ->
            MealSlotCard(
                slot = meal,
                onClick = {
                    editingMeal = meal.type
                    editText = meal.description ?: ""
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Weekly trend
        CategoryWeeklyTrend(category = Category.FOOD)

        Spacer(modifier = Modifier.height(60.dp))
    }

    // Edit dialog
    if (editingMeal != null) {
        val mealType = editingMeal!!
        val slot = meals.find { it.type == mealType }

        AlertDialog(
            onDismissRequest = { editingMeal = null },
            title = { Text("${slot?.label ?: mealType}", color = FoodText) },
            text = {
                TextField(
                    value = editText,
                    onValueChange = { editText = it },
                    placeholder = { Text("What did you eat?") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.3f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.2f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = FoodText,
                        unfocusedTextColor = FoodText
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editText.isNotBlank()) {
                        viewModel.logMeal(mealType, editText)
                    }
                    editingMeal = null
                }) {
                    Text("Save", color = FoodText)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMeal = null }) {
                    Text("Cancel", color = FoodText.copy(alpha = 0.5f))
                }
            },
            containerColor = Color(0xFFD2E8C8)
        )
    }

    // Celebration overlay when all four meal slots are logged
    CelebrationOverlay(
        visible = showCelebration,
        goalName = "daily meals",
        onDismiss = { showCelebration = false }
    )
}

@Composable
fun MealSlotCard(slot: MealSlot, onClick: () -> Unit) {
    val isLogged = slot.description != null
    val borderColor = if (isLogged) AccentTeal else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                if (isLogged) {
                    drawLine(
                        color = borderColor,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 8f
                    )
                }
            }
            .background(Color.White.copy(alpha = if (isLogged) 0.45f else 0.3f))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(slot.icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                slot.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = FoodText
            )
            Text(
                slot.description ?: "Tap to log",
                style = MaterialTheme.typography.labelSmall,
                color = if (isLogged) FoodText.copy(alpha = 0.7f) else FoodText.copy(alpha = 0.4f)
            )
        }
    }
}
