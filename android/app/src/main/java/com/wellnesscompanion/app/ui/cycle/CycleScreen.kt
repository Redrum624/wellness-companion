package com.wellnesscompanion.app.ui.cycle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wellnesscompanion.app.ui.theme.CycleText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val PeriodColor = Color(0xFFE84672)
private val OvulationColor = Color(0xFF8B5CF6)

@Composable
fun CycleScreen(
    viewModel: CycleViewModel = hiltViewModel()
) {
    val todayFlow by viewModel.todayFlow.collectAsState()
    val flowDates by viewModel.flowDates.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val calMonth by viewModel.calMonth.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Flow toggle for today
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.35f))
                .padding(14.dp)
        ) {
            Text("Log flow for today", style = MaterialTheme.typography.labelSmall, color = CycleText.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("light" to "\uD83E\uDE78", "medium" to "\uD83E\uDE78\uD83E\uDE78", "heavy" to "\uD83E\uDE78\uD83E\uDE78\uD83E\uDE78").forEach { (level, icon) ->
                    val active = todayFlow?.flow == level
                    Button(
                        onClick = { viewModel.toggleFlow(level) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (active) PeriodColor else Color.White.copy(alpha = 0.4f),
                            contentColor = if (active) Color.White else CycleText
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(icon, fontSize = 14.sp)
                            Text(level.replaceFirstChar { it.uppercase() }, fontSize = 12.sp)
                        }
                    }
                }
            }
            if (todayFlow != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Tap again to remove", fontSize = 11.sp, color = CycleText.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats cards
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("Cycle length", if (stats.avgCycleLength != null) "${stats.avgCycleLength}d" else "--",
                if (stats.cycleCount > 0) "${stats.cycleCount} tracked" else "Log more data",
                Modifier.weight(1f))
            StatCard("Period length", if (stats.avgPeriodLength != null) "${stats.avgPeriodLength}d" else "--",
                null, Modifier.weight(1f))
            StatCard("Regularity", if (stats.regularity != null) "${stats.regularity}%" else "--",
                regularityLabel(stats.regularity), Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Predictions
        if (stats.nextPeriod != null || stats.nextOvulation != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.35f))
                    .padding(14.dp)
            ) {
                Text("Predictions", style = MaterialTheme.typography.labelSmall, color = CycleText.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(8.dp))
                stats.nextOvulation?.let { ov ->
                    PredictionRow(OvulationColor, "Ovulation", ov)
                }
                stats.nextPeriod?.let { np ->
                    PredictionRow(PeriodColor, "Next period", np)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Calendar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.35f))
                .padding(14.dp)
        ) {
            // Month navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { viewModel.prevMonth() }) {
                    Text("<", color = CycleText, fontSize = 18.sp)
                }
                Text(
                    calMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.titleSmall,
                    color = CycleText,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { viewModel.nextMonth() }) {
                    Text(">", color = CycleText, fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            CycleCalendar(
                month = calMonth,
                flowDates = flowDates,
                predictedPeriod = stats.nextPeriod,
                predictedOvulation = stats.nextOvulation,
                avgPeriodLength = stats.avgPeriodLength,
                onDateClick = { date -> viewModel.toggleDateFlow(date) }
            )

            // Legend
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendItem(PeriodColor, "Period")
                LegendItem(PeriodColor.copy(alpha = 0.25f), "Predicted")
                LegendItem(OvulationColor, "Ovulation")
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, sub: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.3f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = CycleText)
        Text(label, fontSize = 11.sp, color = CycleText.copy(alpha = 0.6f))
        if (sub != null) {
            Text(sub, fontSize = 10.sp, color = CycleText.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun PredictionRow(color: Color, label: String, date: LocalDate) {
    val daysAway = ChronoUnit.DAYS.between(LocalDate.now(), date)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(modifier = Modifier.width(8.dp))
        Text("$label: ", fontSize = 13.sp, color = CycleText)
        Text(date.format(DateTimeFormatter.ofPattern("MMM d")), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CycleText)
        if (daysAway >= 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Text("(${daysAway}d away)", fontSize = 11.sp, color = CycleText.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = CycleText.copy(alpha = 0.7f))
    }
}

@Composable
private fun CycleCalendar(
    month: LocalDate,
    flowDates: Set<String>,
    predictedPeriod: LocalDate?,
    predictedOvulation: LocalDate?,
    avgPeriodLength: Int?,
    onDateClick: (LocalDate) -> Unit
) {
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val today = LocalDate.now()
    val monthStart = month.withDayOfMonth(1)
    val monthEnd = month.withDayOfMonth(month.lengthOfMonth())

    // Predicted period date set
    val predictedDates = remember(predictedPeriod, avgPeriodLength) {
        val set = mutableSetOf<LocalDate>()
        if (predictedPeriod != null && avgPeriodLength != null) {
            for (i in 0 until avgPeriodLength) {
                set.add(predictedPeriod.plusDays(i.toLong()))
            }
        }
        set
    }

    // Build weeks
    val calStart = monthStart.with(DayOfWeek.MONDAY).let {
        if (it.isAfter(monthStart)) it.minusWeeks(1) else it
    }

    val weeks = mutableListOf<List<LocalDate>>()
    var day = calStart
    while (day <= monthEnd || weeks.size < 5) {
        val week = (0 until 7).map { day.plusDays(it.toLong()) }
        weeks.add(week)
        day = day.plusWeeks(1)
        if (day.isAfter(monthEnd) && weeks.size >= 5) break
    }

    // Day headers
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { d ->
            Text(
                text = d,
                fontSize = 10.sp,
                color = CycleText.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    // Calendar grid
    weeks.forEach { week ->
        Row(modifier = Modifier.fillMaxWidth()) {
            week.forEach { d ->
                val ds = d.format(fmt)
                val inMonth = d.month == month.month
                val isFlow = ds in flowDates
                val isPredicted = d in predictedDates
                val isOvulation = predictedOvulation != null && d == predictedOvulation
                val isToday = d == today

                val bg = when {
                    isFlow -> PeriodColor
                    isOvulation -> OvulationColor
                    isPredicted -> PeriodColor.copy(alpha = 0.25f)
                    else -> Color.White.copy(alpha = 0.2f)
                }
                val textColor = when {
                    isFlow || isOvulation -> Color.White
                    !inMonth -> CycleText.copy(alpha = 0.2f)
                    else -> CycleText
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .padding(1.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(bg)
                        .clickable(enabled = inMonth) { onDateClick(d) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${d.dayOfMonth}",
                        fontSize = 12.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = textColor
                    )
                }
            }
        }
    }
}

private fun regularityLabel(score: Int?): String? = when {
    score == null -> null
    score >= 80 -> "Very regular"
    score >= 60 -> "Regular"
    score >= 40 -> "Somewhat irregular"
    else -> "Irregular"
}
