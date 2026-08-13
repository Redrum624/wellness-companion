package com.wellnesscompanion.app.ui.interactions

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.wellnesscompanion.app.ui.components.CategoryWeeklyTrend
import com.wellnesscompanion.app.ui.theme.InteractionsText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractionsScreen(
    viewModel: InteractionsViewModel = hiltViewModel()
) {
    val selectedPeople by viewModel.selectedPeople.collectAsState()
    val savedPeople by viewModel.savedPeople.collectAsState()
    val rating by viewModel.rating.collectAsState()
    val journalText by viewModel.journalText.collectAsState()
    val currentPrompt by viewModel.currentPrompt.collectAsState()
    val todayEntries by viewModel.todayEntries.collectAsState()
    var showAddPerson by remember { mutableStateOf(false) }
    var newPersonName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        // People tags
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.4f))
                .padding(14.dp)
        ) {
            Column {
                Text("Who?", style = MaterialTheme.typography.labelMedium, color = InteractionsText.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    savedPeople.forEach { name ->
                        val isSelected = name in selectedPeople
                        Text(
                            text = "\uD83D\uDC64 $name",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) InteractionsText else InteractionsText.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) Color.White.copy(alpha = 0.6f)
                                    else Color.White.copy(alpha = 0.25f)
                                )
                                .then(
                                    if (isSelected) Modifier.border(1.dp, InteractionsText.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                    else Modifier
                                )
                                .clickable { viewModel.togglePerson(name) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = "+ Add",
                        style = MaterialTheme.typography.bodySmall,
                        color = InteractionsText.copy(alpha = 0.5f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                            .clickable { showAddPerson = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Star rating
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.4f))
                .padding(14.dp)
        ) {
            Column {
                Text("How did it go?", style = MaterialTheme.typography.labelMedium, color = InteractionsText.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { star ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (star <= rating) Color(0xFFF0997B).copy(alpha = 0.5f)
                                    else Color.White.copy(alpha = 0.3f)
                                )
                                .clickable { viewModel.setRating(star) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$star",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (star <= rating) InteractionsText else InteractionsText.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Reflection prompt
        if (currentPrompt != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.25f))
                    .padding(12.dp)
            ) {
                Text(
                    currentPrompt!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = InteractionsText.copy(alpha = 0.6f),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        Text(
            "Get a prompt",
            style = MaterialTheme.typography.labelSmall,
            color = InteractionsText.copy(alpha = 0.4f),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { viewModel.newPrompt() }
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Journal text
        TextField(
            value = journalText,
            onValueChange = { viewModel.setJournalText(it) },
            placeholder = { Text("Write about your interactions today...", style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.3f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.2f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = InteractionsText,
                unfocusedTextColor = InteractionsText
            ),
            textStyle = MaterialTheme.typography.labelSmall,
            minLines = 4
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { viewModel.logInteraction() },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.5f),
                contentColor = InteractionsText
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Log interaction")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Today's entries
        if (todayEntries.isNotEmpty()) {
            Text("${todayEntries.size} entries today", style = MaterialTheme.typography.labelSmall, color = InteractionsText.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(6.dp))
            todayEntries.forEach { entry ->
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                        .padding(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(timeStr, style = MaterialTheme.typography.labelSmall, color = InteractionsText.copy(alpha = 0.5f))
                        if (entry.qualityRating > 0) {
                            Row {
                                repeat(5) { i ->
                                    Icon(
                                        imageVector = if (i < entry.qualityRating) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = null,
                                        tint = Color(0xFFF0997B),
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (entry.people.isNotEmpty()) {
                        Text(entry.people.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = InteractionsText.copy(alpha = 0.7f))
                    }
                    if (entry.journalText.isNotBlank()) {
                        Text(entry.journalText, style = MaterialTheme.typography.labelSmall, color = InteractionsText, maxLines = 3)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Weekly trend
        CategoryWeeklyTrend(category = Category.INTERACTIONS)

        Spacer(modifier = Modifier.height(60.dp))
    }

    // Add person dialog
    if (showAddPerson) {
        AlertDialog(
            onDismissRequest = { showAddPerson = false },
            title = { Text("Add person", color = InteractionsText) },
            text = {
                TextField(
                    value = newPersonName,
                    onValueChange = { newPersonName = it },
                    placeholder = { Text("Name") },
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
                    if (newPersonName.isNotBlank()) {
                        viewModel.addPerson(newPersonName.trim())
                        newPersonName = ""
                    }
                    showAddPerson = false
                }) { Text("Add", color = InteractionsText) }
            },
            dismissButton = {
                TextButton(onClick = { showAddPerson = false }) { Text("Cancel", color = InteractionsText.copy(alpha = 0.5f)) }
            },
            containerColor = Color(0xFFEDD0C2)
        )
    }
}
