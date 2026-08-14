package com.wellnesscompanion.app.ui.sleep

import android.app.TimePickerDialog
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wellnesscompanion.app.data.model.Category
import com.wellnesscompanion.app.data.model.DailyGoals
import com.wellnesscompanion.app.ui.components.CategoryWeeklyTrend
import com.wellnesscompanion.app.ui.components.CelebrationOverlay
import com.wellnesscompanion.app.ui.theme.SleepText

@Composable
fun SleepScreen(
    viewModel: SleepViewModel = hiltViewModel()
) {
    val bedtime by viewModel.bedtime.collectAsState()
    val wakeTime by viewModel.wakeTime.collectAsState()
    val wakeUps by viewModel.wakeUps.collectAsState()
    val totalHours by viewModel.totalHours.collectAsState()
    val qualityScore by viewModel.qualityScore.collectAsState()
    val logState by viewModel.logState.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val context = LocalContext.current

    // Goal celebration — a saved sleep entry meeting the daily goal
    var showCelebration by remember { mutableStateOf(false) }
    var goalWasReached by remember { mutableStateOf(false) }

    LaunchedEffect(logState) {
        val complete = logState as? SleepLogState.Complete
        if (complete != null && complete.data.totalHours >= DailyGoals.SLEEP_MIN_HOURS && !goalWasReached) {
            goalWasReached = true
            showCelebration = true
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Total sleep display
        val h = totalHours.toInt()
        val m = ((totalHours - h) * 60).toInt()
        Text(
            text = "${h}h ${m}m",
            style = MaterialTheme.typography.displayLarge,
            color = SleepText
        )
        Text(
            "total sleep",
            style = MaterialTheme.typography.bodyMedium,
            color = SleepText.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Bedtime / Wake time pickers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TimeCard(
                label = "Bedtime",
                time = bedtime,
                onClick = {
                    val parts = bedtime.split(":")
                    TimePickerDialog(context, { _, hour, minute ->
                        viewModel.setBedtime("%02d:%02d".format(hour, minute))
                    }, parts[0].toInt(), parts[1].toInt(), true).show()
                }
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = SleepText.copy(alpha = 0.4f),
                modifier = Modifier
                    .padding(top = 20.dp)
                    .size(16.dp)
            )
            TimeCard(
                label = "Wake up",
                time = wakeTime,
                onClick = {
                    val parts = wakeTime.split(":")
                    TimePickerDialog(context, { _, hour, minute ->
                        viewModel.setWakeTime("%02d:%02d".format(hour, minute))
                    }, parts[0].toInt(), parts[1].toInt(), true).show()
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Sleep quality bar
        SleepQualityBar(
            bedtime = bedtime,
            wakeTime = wakeTime,
            wakeUps = wakeUps,
            totalHours = totalHours,
            modifier = Modifier.padding(horizontal = 0.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(bedtime, style = MaterialTheme.typography.labelSmall, color = SleepText.copy(alpha = 0.5f))
            Text(
                "${wakeUps.size} wake-ups",
                style = MaterialTheme.typography.labelSmall,
                color = if (wakeUps.isNotEmpty()) Color(0xFFF0997B) else SleepText.copy(alpha = 0.4f)
            )
            Text(wakeTime, style = MaterialTheme.typography.labelSmall, color = SleepText.copy(alpha = 0.5f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quality score
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.4f))
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Column {
                Text("Quality score", style = MaterialTheme.typography.labelMedium, color = SleepText.copy(alpha = 0.6f))
                Text(
                    "$qualityScore/10",
                    style = MaterialTheme.typography.titleLarge,
                    color = SleepText,
                    fontSize = 28.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Wake-ups section
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.4f))
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Wake-ups", style = MaterialTheme.typography.labelMedium, color = SleepText.copy(alpha = 0.6f))
                    Text(
                        "+ Add",
                        style = MaterialTheme.typography.labelMedium,
                        color = SleepText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.5f))
                            .clickable {
                                TimePickerDialog(context, { _, hour, minute ->
                                    viewModel.addWakeUp("%02d:%02d".format(hour, minute))
                                }, 3, 0, true).show()
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                if (wakeUps.isEmpty()) {
                    Text("No wake-ups logged", style = MaterialTheme.typography.labelSmall, color = SleepText.copy(alpha = 0.4f))
                } else {
                    wakeUps.forEachIndexed { index, time ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(time, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFF0997B))
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Remove wake-up",
                                tint = SleepText.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { viewModel.removeWakeUp(index) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save buttons — bedtime can be saved on its own and completed in the morning
        if (logState is SleepLogState.Complete) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Sleep logged",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SleepText.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = SleepText.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { viewModel.saveBedtime() },
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.4f),
                    contentColor = SleepText
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save tonight's bedtime")
            }
        } else {
            val bedtimeSaved = logState is SleepLogState.BedtimeSaved
            if (bedtimeSaved) {
                Text(
                    "Bedtime saved — log your wake-up when you get up",
                    style = MaterialTheme.typography.labelSmall,
                    color = SleepText.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.saveBedtime() },
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.4f),
                        contentColor = SleepText
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (bedtimeSaved) "Update bedtime" else "Save bedtime")
                }
                Button(
                    onClick = { viewModel.saveWakeUp() },
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.5f),
                        contentColor = SleepText
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (bedtimeSaved) "Save wake-up" else "Save full night")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Weekly trend
        CategoryWeeklyTrend(category = Category.SLEEP)

        Spacer(modifier = Modifier.height(60.dp))
    }

    // Celebration overlay when a saved sleep entry meets the daily goal
    CelebrationOverlay(
        visible = showCelebration,
        goalName = "nightly sleep",
        onDismiss = { showCelebration = false }
    )
}

@Composable
private fun TimeCard(label: String, time: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.4f))
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = SleepText.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(4.dp))
        Text(time, style = MaterialTheme.typography.titleLarge, color = SleepText, fontSize = 24.sp)
    }
}
