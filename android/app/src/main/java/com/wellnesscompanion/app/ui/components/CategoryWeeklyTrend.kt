package com.wellnesscompanion.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.wellnesscompanion.app.data.model.Category
import com.wellnesscompanion.app.data.model.WaterData
import com.wellnesscompanion.app.data.model.DailyGoals
import com.wellnesscompanion.app.data.repository.EntryRepository
import com.wellnesscompanion.app.util.StreakTracker
import com.wellnesscompanion.app.util.fromJsonSafe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Drop-in weekly trend + streak section for any category screen.
 * Self-contained: fetches its own data from the repository.
 */
@Composable
fun CategoryWeeklyTrend(
    category: Category,
    modifier: Modifier = Modifier,
    viewModel: TrendViewModel = hiltViewModel()
) {
    val dateStrings = remember { StreakTracker.last7DateStrings() }
    val (startDate, endDate) = remember { StreakTracker.last7DaysRange() }

    val weekEntries by viewModel.getWeekEntries(category.key, startDate, endDate)
        .collectAsState(initial = emptyList())

    val streak by viewModel.getStreak(category.key)
        .collectAsState(initial = 0)

    // Compute per-day counts
    val dailyValues = remember(weekEntries, dateStrings) {
        val grouped = weekEntries.groupBy { it.date }
        dateStrings.map { date ->
            grouped[date]?.size?.toFloat() ?: 0f
        }
    }

    val maxVal = when (category) {
        Category.WATER -> DailyGoals.WATER_BOTTLES.toFloat()
        Category.FOOD -> DailyGoals.FOOD_MEALS.toFloat()
        Category.BATHROOM -> DailyGoals.BATHROOM_NORMAL_MAX.toFloat()
        Category.HEALTH -> DailyGoals.HEALTH_ENERGY_CHECKINS.toFloat()
        Category.SLEEP -> 1f
        Category.EMOTIONS -> DailyGoals.EMOTIONS_CHECKINS.toFloat()
        Category.INTERACTIONS -> DailyGoals.INTERACTIONS_MIN.toFloat() * 3
        Category.CHORES -> DailyGoals.CHORES_TASKS_MIN.toFloat() * 2
        Category.HOBBIES -> 6f
        Category.IDEAS -> 5f
        Category.CYCLE -> 1f
        Category.BADHABITS -> 8f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.25f))
            .padding(12.dp)
    ) {
        val streakText = if (streak > 0) " \u00b7 \uD83D\uDD25 $streak day streak" else ""
        Text(
            "7-day trend$streakText",
            style = MaterialTheme.typography.labelSmall,
            color = category.colors.textColor.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(6.dp))

        WeeklyTrendChart(
            values = dailyValues,
            maxValue = maxVal,
            barColor = category.colors.textColor,
            textColor = category.colors.textColor
        )
    }
}

@HiltViewModel
class TrendViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val gson: Gson
) : ViewModel() {

    fun getWeekEntries(categoryKey: String, startDate: String, endDate: String) =
        repository.getEntriesByDateRange(startDate, endDate, categoryKey)

    fun getStreak(categoryKey: String) =
        repository.getLoggedDates(categoryKey).map { dates ->
            StreakTracker.currentStreak(dates)
        }
}
