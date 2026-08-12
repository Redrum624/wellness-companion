package com.wellnesscompanion.app.ui.health

import androidx.compose.foundation.background
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wellnesscompanion.app.data.model.Category
import com.wellnesscompanion.app.ui.components.CategoryWeeklyTrend
import com.wellnesscompanion.app.ui.theme.AccentTeal
import com.wellnesscompanion.app.ui.theme.HealthText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val defaultSymptoms = listOf("headache", "fatigue", "nausea", "cramps", "dizziness")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthScreen(
    viewModel: HealthViewModel = hiltViewModel()
) {
    val energyLevel by viewModel.energyLevel.collectAsState()
    val dailyRating by viewModel.dailyRating.collectAsState()
    val symptoms by viewModel.symptoms.collectAsState()
    val note by viewModel.note.collectAsState()
    val todayEntries by viewModel.todayEntries.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        // Energy level
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.4f))
                .padding(14.dp)
        ) {
            Column {
                Text("Energy level", style = MaterialTheme.typography.labelMedium, color = HealthText.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$energyLevel",
                        style = MaterialTheme.typography.titleLarge,
                        color = HealthText,
                        fontSize = 28.sp
                    )
                    Text("/10", style = MaterialTheme.typography.bodyMedium, color = HealthText.copy(alpha = 0.5f))
                }
                Slider(
                    value = energyLevel.toFloat(),
                    onValueChange = { viewModel.setEnergyLevel(it.toInt()) },
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentTeal,
                        activeTrackColor = AccentTeal,
                        inactiveTrackColor = Color.White.copy(alpha = 0.5f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Daily rating
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.4f))
                .padding(14.dp)
        ) {
            Column {
                Text("Daily rating", style = MaterialTheme.typography.labelMedium, color = HealthText.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..10).forEach { rating ->
                        val isSelected = dailyRating == rating
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) AccentTeal.copy(alpha = 0.6f)
                                    else Color.White.copy(alpha = 0.35f)
                                )
                                .clickable { viewModel.setDailyRating(rating) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$rating",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) HealthText else HealthText.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Symptoms
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.4f))
                .padding(14.dp)
        ) {
            Column {
                Text("Symptoms", style = MaterialTheme.typography.labelMedium, color = HealthText.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    defaultSymptoms.forEach { symptom ->
                        val isActive = symptom in symptoms
                        Text(
                            text = symptom,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isActive) Color(0xFF712B13) else HealthText.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isActive) Color(0xFFF0997B).copy(alpha = 0.5f)
                                    else Color.White.copy(alpha = 0.35f)
                                )
                                .clickable { viewModel.toggleSymptom(symptom) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Note
        TextField(
            value = note,
            onValueChange = { viewModel.setNote(it) },
            placeholder = { Text("Health notes (optional)...", style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.3f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.2f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = HealthText,
                unfocusedTextColor = HealthText
            ),
            textStyle = MaterialTheme.typography.labelSmall,
            minLines = 2
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { viewModel.logHealth() },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.5f),
                contentColor = HealthText
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Log health check")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Today's entries
        if (todayEntries.isNotEmpty()) {
            Text("Today's logs", style = MaterialTheme.typography.labelSmall, color = HealthText.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(6.dp))
            todayEntries.forEach { entry ->
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(timeStr, style = MaterialTheme.typography.labelSmall, color = HealthText.copy(alpha = 0.5f))
                    Row {
                        if (entry.energyLevel != null) {
                            Text("Energy: ${entry.energyLevel}", style = MaterialTheme.typography.labelSmall, color = HealthText)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        if (entry.symptoms.isNotEmpty()) {
                            Text(entry.symptoms.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = Color(0xFFF0997B))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Weekly trend
        CategoryWeeklyTrend(category = Category.HEALTH)

        Spacer(modifier = Modifier.height(60.dp))
    }
}
