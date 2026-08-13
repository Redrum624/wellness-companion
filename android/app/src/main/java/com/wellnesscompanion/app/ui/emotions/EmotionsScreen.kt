package com.wellnesscompanion.app.ui.emotions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wellnesscompanion.app.data.model.Category
import com.wellnesscompanion.app.ui.components.CategoryWeeklyTrend
import com.wellnesscompanion.app.ui.theme.EmotionsText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MoodOption(
    val key: String,
    val emoji: String,
    val label: String,
    val color: Color
)

val moods = listOf(
    MoodOption("happy", "\uD83D\uDE0A", "Happy", Color(0xFFF5E5C4)),
    MoodOption("calm", "\uD83D\uDE0C", "Calm", Color(0xFFCCE8DD)),
    MoodOption("sad", "\u2639\uFE0F", "Sad", Color(0xFFD4E9F7)),
    MoodOption("angry", "\uD83D\uDE20", "Angry", Color(0xFFF5C4B3)),
    MoodOption("anxious", "\uD83D\uDE30", "Anxious", Color(0xFFF5D6E3)),
    MoodOption("tired", "\uD83D\uDE34", "Tired", Color(0xFFDEDCF7))
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmotionsScreen(
    viewModel: EmotionsViewModel = hiltViewModel()
) {
    val selectedMood by viewModel.selectedMood.collectAsState()
    val note by viewModel.note.collectAsState()
    val todayEntries by viewModel.todayEntries.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "How are you feeling?",
            style = MaterialTheme.typography.titleMedium,
            color = EmotionsText
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Mood picker grid
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            moods.forEach { mood ->
                val isSelected = selectedMood == mood.key
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { viewModel.selectMood(mood.key) }
                ) {
                    val tileScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "moodTileScale"
                    )
                    val tileColor by animateColorAsState(
                        targetValue = if (isSelected) mood.color else mood.color.copy(alpha = 0.7f),
                        label = "moodTileColor"
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(tileScale)
                            .clip(CircleShape)
                            .background(tileColor)
                            .then(
                                if (isSelected) Modifier.border(2.dp, EmotionsText, CircleShape)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(mood.emoji, fontSize = 22.sp)
                    }
                    Text(
                        mood.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = EmotionsText.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Note field
        TextField(
            value = note,
            onValueChange = { viewModel.setNote(it) },
            placeholder = { Text("Add a note (optional)...", style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.3f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.2f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = EmotionsText,
                unfocusedTextColor = EmotionsText
            ),
            textStyle = MaterialTheme.typography.labelSmall,
            minLines = 2
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Log button
        Button(
            onClick = { viewModel.logEmotion() },
            enabled = selectedMood != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.5f),
                contentColor = EmotionsText,
                disabledContainerColor = Color.White.copy(alpha = 0.2f),
                disabledContentColor = EmotionsText.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Log feeling")
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Day arc
        if (todayEntries.isNotEmpty()) {
            Text("Today's emotional flow", style = MaterialTheme.typography.labelSmall, color = EmotionsText.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(6.dp))
            DayArcBar(entries = todayEntries)
            Spacer(modifier = Modifier.height(16.dp))

            // Entry list
            todayEntries.forEach { entry ->
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
                val mood = moods.find { it.key == entry.emotion }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(timeStr, style = MaterialTheme.typography.labelSmall, color = EmotionsText.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(mood?.emoji ?: "", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        mood?.label ?: entry.emotion,
                        style = MaterialTheme.typography.bodySmall,
                        color = EmotionsText
                    )
                    if (entry.note != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            entry.note,
                            style = MaterialTheme.typography.labelSmall,
                            color = EmotionsText.copy(alpha = 0.5f),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Weekly trend
        CategoryWeeklyTrend(category = Category.EMOTIONS)

        Spacer(modifier = Modifier.height(60.dp))
    }
}
