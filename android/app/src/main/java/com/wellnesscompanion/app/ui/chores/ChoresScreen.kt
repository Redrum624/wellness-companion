package com.wellnesscompanion.app.ui.chores

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wellnesscompanion.app.data.model.Category
import com.wellnesscompanion.app.ui.components.CategoryWeeklyTrend
import com.wellnesscompanion.app.ui.components.CelebrationOverlay
import com.wellnesscompanion.app.data.model.DailyGoals
import com.wellnesscompanion.app.ui.theme.AccentTeal
import com.wellnesscompanion.app.ui.theme.ChoresText

@Composable
fun ChoresScreen(
    viewModel: ChoresViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsState()
    val completedCount by viewModel.completedCount.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val timerTask by viewModel.timerTask.collectAsState()
    var showAddTask by remember { mutableStateOf(false) }
    var newTaskName by remember { mutableStateOf("") }

    // Goal celebration — all of today's chores completed
    var showCelebration by remember { mutableStateOf(false) }
    var goalWasReached by remember { mutableStateOf(false) }

    LaunchedEffect(completedCount, totalCount) {
        if (totalCount > 0 && completedCount == totalCount && !goalWasReached) {
            goalWasReached = true
            showCelebration = true
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Progress
        Text(
            "$completedCount/$totalCount tasks done (goal: ${DailyGoals.CHORES_TASKS_MIN}+)",
            style = MaterialTheme.typography.bodyMedium,
            color = ChoresText.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(6.dp))

        // Progress bar
        val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AccentTeal.copy(alpha = 0.6f))
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Task list (plain Column, not lazy — animate overall size so
        // completed-task reordering resizes smoothly)
        Column(modifier = Modifier.animateContentSize()) {
            tasks.forEachIndexed { index, task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = if (task.completed) 0.2f else 0.35f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Checkbox
                    val boxColor by animateColorAsState(
                        targetValue = if (task.completed) AccentTeal else Color.White.copy(alpha = 0.3f),
                        label = "choreCheckboxColor"
                    )
                    val checkScale by animateFloatAsState(
                        targetValue = if (task.completed) 1f else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "choreCheckScale"
                    )
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(boxColor)
                            .clickable { viewModel.toggleTask(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        // Always composed — an underdamped spring can overshoot below 0
                        // while settling on uncheck, so visibility must not be gated on
                        // the animated value itself (that caused decompose/recompose
                        // flicker). Clamp the rendered scale instead; scale 0 already
                        // draws nothing.
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(14.dp)
                                .graphicsLayer {
                                    val clamped = checkScale.coerceAtLeast(0f)
                                    scaleX = clamped
                                    scaleY = clamped
                                }
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Task name
                    Text(
                        task.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (task.completed) ChoresText.copy(alpha = 0.4f) else ChoresText,
                        textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.weight(1f)
                    )

                    // Time + timer
                    if (task.timeSpentMin != null && task.timeSpentMin > 0) {
                        Text(
                            "${task.timeSpentMin} min",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChoresText.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Timer button
                    val isTimerActive = timerTask == task.name
                    Text(
                        text = if (isTimerActive) "⏹" else "⏱",
                        modifier = Modifier.clickable {
                            if (isTimerActive) viewModel.stopTimer() else viewModel.startTimer(task.name)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Add task button
        Button(
            onClick = { showAddTask = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.4f),
                contentColor = ChoresText
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Add task")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Weekly trend
        CategoryWeeklyTrend(category = Category.CHORES)

        Spacer(modifier = Modifier.height(60.dp))
    }

    // Add task dialog
    if (showAddTask) {
        AlertDialog(
            onDismissRequest = { showAddTask = false },
            title = { Text("Add task", color = ChoresText) },
            text = {
                TextField(
                    value = newTaskName,
                    onValueChange = { newTaskName = it },
                    placeholder = { Text("Task name") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.3f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.2f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newTaskName.isNotBlank()) {
                        viewModel.addTask(newTaskName.trim())
                        newTaskName = ""
                    }
                    showAddTask = false
                }) { Text("Add", color = ChoresText) }
            },
            dismissButton = {
                TextButton(onClick = { showAddTask = false }) { Text("Cancel", color = ChoresText.copy(alpha = 0.5f)) }
            },
            containerColor = Color(0xFFDDD8CE)
        )
    }

    // Celebration overlay when all of today's chores are completed
    CelebrationOverlay(
        visible = showCelebration,
        goalName = "daily chores",
        onDismiss = { showCelebration = false }
    )
}
