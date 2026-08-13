package com.wellnesscompanion.app.ui.hobbies

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wellnesscompanion.app.data.model.Category
import com.wellnesscompanion.app.data.model.DailyGoals
import com.wellnesscompanion.app.ui.components.CategoryWeeklyTrend
import com.wellnesscompanion.app.ui.theme.HobbiesText
import com.wellnesscompanion.app.util.formatMinutes

val defaultHobbyColors = listOf("#AFA9EC", "#F0997B", "#9FE1CB", "#85B7EB", "#F5D6E3", "#F5E5C4", "#D6EDCC", "#E2E0D8")

@Composable
fun HobbiesScreen(
    viewModel: HobbiesViewModel = hiltViewModel()
) {
    val savedHobbies by viewModel.savedHobbies.collectAsState()
    val todaySessions by viewModel.todaySessions.collectAsState()
    val totalMinutes by viewModel.totalMinutesToday.collectAsState()
    val craneCount by viewModel.craneCount.collectAsState()
    val craneColors by viewModel.craneColors.collectAsState()
    val lastAddedCraneIndex by viewModel.lastAddedCraneIndex.collectAsState()
    var showAddHobby by remember { mutableStateOf(false) }
    var newHobbyName by remember { mutableStateOf("") }
    var selectedColorIdx by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Today's total
        Text(
            "${formatMinutes(totalMinutes)} today",
            style = MaterialTheme.typography.titleMedium,
            color = HobbiesText
        )
        Text(
            "Goal: ${DailyGoals.HOBBIES_DAILY_MIN}+ min · $craneCount cranes",
            style = MaterialTheme.typography.labelSmall,
            color = HobbiesText.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Crane bowl
        CraneBowlCanvas(
            craneCount = craneCount,
            craneColors = craneColors,
            lastAddedIndex = lastAddedCraneIndex,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Hobby list with +5 min buttons
        if (savedHobbies.isEmpty()) {
            Text(
                "Add a hobby to get started!",
                style = MaterialTheme.typography.bodySmall,
                color = HobbiesText.copy(alpha = 0.5f)
            )
        }

        savedHobbies.forEach { hobby ->
            val hobbyColor = parseHobbyColor(hobby.color)
            val todayMin = todaySessions.filter { it.hobbyName == hobby.name }.sumOf { it.durationMin }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.35f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color dot
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(hobbyColor)
                )
                Spacer(modifier = Modifier.width(10.dp))

                // Name + today's time
                Column(modifier = Modifier.weight(1f)) {
                    Text(hobby.name, style = MaterialTheme.typography.bodySmall, color = HobbiesText)
                    if (todayMin > 0) {
                        Text(
                            "${formatMinutes(todayMin)} today",
                            style = MaterialTheme.typography.labelSmall,
                            color = HobbiesText.copy(alpha = 0.5f)
                        )
                    }
                }

                // Quick add buttons
                listOf(5, 15, 30).forEach { min ->
                    Text(
                        text = "+${min}",
                        style = MaterialTheme.typography.labelSmall,
                        color = HobbiesText,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(hobbyColor.copy(alpha = 0.3f))
                            .clickable { viewModel.logTime(hobby, min) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { showAddHobby = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.4f),
                contentColor = HobbiesText
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Add hobby")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Weekly trend
        CategoryWeeklyTrend(category = Category.HOBBIES)

        Spacer(modifier = Modifier.height(60.dp))
    }

    // Add hobby dialog
    if (showAddHobby) {
        AlertDialog(
            onDismissRequest = { showAddHobby = false },
            title = { Text("Add hobby", color = HobbiesText) },
            text = {
                Column {
                    TextField(
                        value = newHobbyName,
                        onValueChange = { newHobbyName = it },
                        placeholder = { Text("Hobby name") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.3f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.2f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Color:", style = MaterialTheme.typography.labelSmall, color = HobbiesText.copy(alpha = 0.6f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        defaultHobbyColors.forEachIndexed { idx, hex ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(parseHobbyColor(hex))
                                    .then(
                                        if (idx == selectedColorIdx)
                                            Modifier.padding(2.dp).clip(CircleShape).background(parseHobbyColor(hex))
                                        else Modifier
                                    )
                                    .clickable { selectedColorIdx = idx }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newHobbyName.isNotBlank()) {
                        viewModel.addHobby(newHobbyName.trim(), defaultHobbyColors[selectedColorIdx])
                        newHobbyName = ""
                        selectedColorIdx = 0
                    }
                    showAddHobby = false
                }) { Text("Add", color = HobbiesText) }
            },
            dismissButton = {
                TextButton(onClick = { showAddHobby = false }) { Text("Cancel", color = HobbiesText.copy(alpha = 0.5f)) }
            },
            containerColor = Color(0xFFEACCE0)
        )
    }
}

private fun parseHobbyColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color(0xFFAFA9EC)
    }
}
