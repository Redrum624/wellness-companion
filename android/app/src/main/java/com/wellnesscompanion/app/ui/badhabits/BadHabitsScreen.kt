package com.wellnesscompanion.app.ui.badhabits

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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wellnesscompanion.app.data.model.Category
import com.wellnesscompanion.app.ui.components.CategoryWeeklyTrend
import com.wellnesscompanion.app.ui.theme.BadHabitsText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class SubstanceDef(
    val key: String,
    val icon: String,
    val label: String,
    val color: Color,
    val hasLevel: Boolean,
    val levelWords: List<String>
)

private val substances = listOf(
    SubstanceDef(
        key = "alcohol",
        icon = "\uD83C\uDF77",
        label = "Alcohol",
        color = Color(0xFF7A2040),
        hasLevel = true,
        levelWords = listOf(
            "Sober", "Regular", "Warm", "Buzzed", "Tipsy",
            "Drunk", "Very drunk", "Sloppy", "Puke", "Blackout", "Blackout"
        )
    ),
    SubstanceDef(
        key = "weed",
        icon = "\uD83C\uDF3F",
        label = "Weed",
        color = Color(0xFF27500A),
        hasLevel = true,
        levelWords = listOf(
            "Sober", "Regular", "Mellow", "Buzzed", "High",
            "Very high", "Stoned", "Couch-locked", "Spinning", "Greened out", "Greened out"
        )
    ),
    SubstanceDef(
        key = "tobacco",
        icon = "\uD83D\uDEAC",
        label = "Tobacco / Vape",
        color = Color(0xFF6B5562),
        hasLevel = false,
        levelWords = emptyList()
    ),
    SubstanceDef(
        key = "selfharm",
        icon = "\uD83E\uDE79",
        label = "Self-harm",
        color = Color(0xFF8B4A5A),
        hasLevel = false,
        levelWords = emptyList()
    )
)

@Composable
fun BadHabitsScreen(
    viewModel: BadHabitsViewModel = hiltViewModel()
) {
    val counts by viewModel.counts.collectAsState()
    val alcoholLevel by viewModel.alcoholLevel.collectAsState()
    val weedLevel by viewModel.weedLevel.collectAsState()
    val entries by viewModel.todayEntries.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        // Summary
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${counts.total}",
                style = MaterialTheme.typography.displayLarge,
                color = BadHabitsText,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (counts.total == 0) "No slips today — nice"
                else "${counts.total} consumption${if (counts.total == 1) "" else "s"} logged",
                style = MaterialTheme.typography.bodySmall,
                color = BadHabitsText.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        substances.forEach { s ->
            val count = when (s.key) {
                "alcohol" -> counts.alcohol
                "weed" -> counts.weed
                "tobacco" -> counts.tobacco
                "selfharm" -> counts.selfharm
                else -> 0
            }
            val level = when (s.key) {
                "alcohol" -> alcoholLevel
                "weed" -> weedLevel
                else -> 0
            }

            SubstanceCard(
                def = s,
                count = count,
                level = level,
                onAdd = { viewModel.logConsumption(s.key) },
                onUndo = { viewModel.undoLast(s.key) },
                onLevelChange = { viewModel.setLevel(s.key, it) }
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Today's log
        if (entries.isNotEmpty()) {
            Text(
                "Today's log",
                style = MaterialTheme.typography.labelSmall,
                color = BadHabitsText.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            entries.forEach { e ->
                val def = substances.firstOrNull { it.key == e.substance }
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(e.timestamp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(def?.icon ?: "⚠️", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        def?.label ?: e.substance,
                        style = MaterialTheme.typography.bodySmall,
                        color = BadHabitsText,
                        modifier = Modifier.weight(1f)
                    )
                    if (e.level != null && def?.hasLevel == true) {
                        Text(
                            "lvl ${e.level}",
                            style = MaterialTheme.typography.labelSmall,
                            color = BadHabitsText.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = BadHabitsText.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        CategoryWeeklyTrend(category = Category.BADHABITS)

        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
private fun SubstanceCard(
    def: SubstanceDef,
    count: Int,
    level: Int,
    onAdd: () -> Unit,
    onUndo: () -> Unit,
    onLevelChange: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.35f))
            .padding(14.dp)
    ) {
        Column {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(def.color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(def.icon, fontSize = 22.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        def.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = BadHabitsText,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (count == 0) "none today" else "$count time${if (count == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = BadHabitsText.copy(alpha = 0.55f)
                    )
                }
                Text(
                    "$count",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = def.color
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Counter buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onUndo,
                    enabled = count > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.5f),
                        contentColor = BadHabitsText,
                        disabledContainerColor = Color.White.copy(alpha = 0.2f),
                        disabledContentColor = BadHabitsText.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text("−1", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onAdd,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = def.color,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(2f)
                        .height(44.dp)
                ) {
                    Text("+1 ${def.label.substringBefore(' ').lowercase()}", fontWeight = FontWeight.SemiBold)
                }
            }

            // Level slider for alcohol/weed
            if (def.hasLevel) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        "Peak level",
                        style = MaterialTheme.typography.labelSmall,
                        color = BadHabitsText.copy(alpha = 0.6f)
                    )
                    Text(
                        "$level/10 · ${def.levelWords.getOrNull(level) ?: ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = def.color,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Slider(
                    value = level.toFloat(),
                    onValueChange = { onLevelChange(it.toInt()) },
                    valueRange = 0f..10f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = def.color,
                        activeTrackColor = def.color,
                        inactiveTrackColor = Color.White.copy(alpha = 0.5f)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("sober", style = MaterialTheme.typography.labelSmall, color = BadHabitsText.copy(alpha = 0.4f))
                    Text("buzzed", style = MaterialTheme.typography.labelSmall, color = BadHabitsText.copy(alpha = 0.4f))
                    Text("blackout", style = MaterialTheme.typography.labelSmall, color = BadHabitsText.copy(alpha = 0.4f))
                }
            }
        }
    }
}
