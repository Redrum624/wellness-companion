package com.wellnesscompanion.app.ui.ideas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wellnesscompanion.app.ui.theme.IdeasText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdeasScreen(
    viewModel: IdeasViewModel = hiltViewModel()
) {
    val title by viewModel.title.collectAsState()
    val body by viewModel.body.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val tagInput by viewModel.tagInput.collectAsState()
    val editingEntry by viewModel.editingEntry.collectAsState()
    val todayEntries by viewModel.todayEntries.collectAsState()
    val historyDays by viewModel.historyDays.collectAsState()

    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.White.copy(alpha = 0.4f),
        unfocusedContainerColor = Color.White.copy(alpha = 0.3f),
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        focusedTextColor = IdeasText,
        unfocusedTextColor = IdeasText,
        cursorColor = IdeasText
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Input form
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.35f))
                .padding(16.dp)
        ) {
            TextField(
                value = title,
                onValueChange = { viewModel.setTitle(it) },
                placeholder = { Text("Idea title", color = IdeasText.copy(alpha = 0.4f)) },
                colors = fieldColors,
                shape = RoundedCornerShape(10.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = IdeasText),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            TextField(
                value = body,
                onValueChange = { viewModel.setBody(it) },
                placeholder = { Text("Describe your idea...", color = IdeasText.copy(alpha = 0.4f)) },
                colors = fieldColors,
                shape = RoundedCornerShape(10.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = IdeasText),
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Tags
            Text("Tags", style = MaterialTheme.typography.labelSmall, color = IdeasText.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(4.dp))

            if (tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    tags.forEach { tag ->
                        Text(
                            text = "$tag  \u00D7",
                            fontSize = 12.sp,
                            color = IdeasText.copy(alpha = 0.8f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.4f))
                                .clickable { viewModel.removeTag(tag) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = tagInput,
                    onValueChange = { viewModel.setTagInput(it) },
                    placeholder = { Text("Add tag...", color = IdeasText.copy(alpha = 0.4f)) },
                    colors = fieldColors,
                    shape = RoundedCornerShape(10.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = IdeasText),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { viewModel.addTag() }) {
                    Text("+", color = IdeasText)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.save() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.5f),
                        contentColor = IdeasText
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (editingEntry != null) "Update idea" else "Save idea")
                }
                if (editingEntry != null) {
                    TextButton(onClick = { viewModel.cancelEdit() }) {
                        Text("Cancel", color = IdeasText.copy(alpha = 0.5f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Today's entries
        if (todayEntries.isNotEmpty()) {
            Text(
                "${todayEntries.size} idea${if (todayEntries.size != 1) "s" else ""} today",
                style = MaterialTheme.typography.labelSmall,
                color = IdeasText.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            todayEntries.forEach { entry ->
                IdeaCard(
                    entry = entry,
                    showActions = true,
                    onEdit = { viewModel.startEdit(it) },
                    onDelete = { viewModel.delete(it) }
                )
            }
        }

        // Earlier ideas — everything the desktop shows via its date picker
        if (historyDays.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "Earlier ideas",
                style = MaterialTheme.typography.labelSmall,
                color = IdeasText.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            historyDays.forEach { day ->
                Text(
                    day.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = IdeasText.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp)
                )
                day.ideas.forEach { entry ->
                    IdeaCard(entry = entry, showActions = false, onEdit = {}, onDelete = {})
                }
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdeaCard(
    entry: IdeaEntry,
    showActions: Boolean,
    onEdit: (IdeaEntry) -> Unit,
    onDelete: (IdeaEntry) -> Unit
) {
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.3f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (entry.title.isNotEmpty()) {
                    Text(entry.title, style = MaterialTheme.typography.bodyLarge, color = IdeasText)
                }
                Text(timeStr, fontSize = 11.sp, color = IdeasText.copy(alpha = 0.4f))
            }
            if (showActions) {
                Row {
                    TextButton(onClick = { onEdit(entry) }) {
                        Text("edit", fontSize = 12.sp, color = IdeasText.copy(alpha = 0.5f))
                    }
                    TextButton(onClick = { onDelete(entry) }) {
                        Text("delete", fontSize = 12.sp, color = IdeasText.copy(alpha = 0.35f))
                    }
                }
            }
        }
        if (entry.body.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(entry.body, style = MaterialTheme.typography.bodyMedium, color = IdeasText, lineHeight = 20.sp)
        }
        if (entry.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                entry.tags.forEach { tag ->
                    Text(
                        text = tag,
                        fontSize = 11.sp,
                        color = IdeasText.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.4f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
