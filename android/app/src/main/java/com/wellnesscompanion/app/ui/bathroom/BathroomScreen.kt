package com.wellnesscompanion.app.ui.bathroom

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.wellnesscompanion.app.data.model.DailyGoals
import com.wellnesscompanion.app.ui.components.CategoryWeeklyTrend
import com.wellnesscompanion.app.ui.theme.BathroomText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BathroomScreen(
    viewModel: BathroomViewModel = hiltViewModel()
) {
    val todayEntries by viewModel.todayEntries.collectAsState()
    val todayCount by viewModel.todayCount.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Count display
        Text(
            text = "$todayCount",
            style = MaterialTheme.typography.displayLarge,
            color = BathroomText
        )
        Text(
            "breaks today (normal: ${DailyGoals.BATHROOM_NORMAL_MIN}–${DailyGoals.BATHROOM_NORMAL_MAX})",
            style = MaterialTheme.typography.bodyMedium,
            color = BathroomText.copy(alpha = 0.6f)
        )


        // Poop count
        val poopCount = todayEntries.count { it.type == "poop" }
        if (poopCount > 0) {
            Text(
                "\uD83D\uDCA9 $poopCount poop${if (poopCount != 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = BathroomText.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Log buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { viewModel.logNow() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.5f),
                    contentColor = BathroomText
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Text("\uD83D\uDEBD Log break", style = MaterialTheme.typography.titleMedium)
            }
            Button(
                onClick = { viewModel.logNow(type = "poop") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.5f),
                    contentColor = BathroomText
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Text("\uD83D\uDCA9 Log poop", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Vertical timeline
        if (todayEntries.isNotEmpty()) {
            Text(
                "Today's timeline",
                style = MaterialTheme.typography.labelSmall,
                color = BathroomText.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            todayEntries.forEach { entry ->
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Timeline dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFED93B1).copy(alpha = 0.6f))
                    )
                    Spacer(modifier = Modifier.width(12.dp))

                    // Time + details
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "$timeStr${if (entry.type == "poop") " \uD83D\uDCA9" else ""}",
                            style = MaterialTheme.typography.bodyMedium, color = BathroomText
                        )
                        if (entry.note != null) {
                            Text(
                                entry.note,
                                style = MaterialTheme.typography.labelSmall,
                                color = BathroomText.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Weekly trend
        CategoryWeeklyTrend(category = Category.BATHROOM)

        Spacer(modifier = Modifier.height(60.dp))
    }
}
