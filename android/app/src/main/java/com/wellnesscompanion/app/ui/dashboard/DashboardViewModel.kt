package com.wellnesscompanion.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.wellnesscompanion.app.data.model.BadHabitsData
import com.wellnesscompanion.app.data.model.Category
import com.wellnesscompanion.app.data.model.DailyGoals
import com.wellnesscompanion.app.data.model.EmotionData
import com.wellnesscompanion.app.data.model.FoodData
import com.wellnesscompanion.app.data.model.SleepData
import com.wellnesscompanion.app.data.model.CycleData
import com.wellnesscompanion.app.data.model.WaterData
import com.wellnesscompanion.app.data.repository.EntryRepository
import com.wellnesscompanion.app.util.StreakTracker
import com.wellnesscompanion.app.util.formatMlCompact
import com.wellnesscompanion.app.util.fromJsonSafe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val gson: Gson
) : ViewModel() {

    val summaries = repository.getAllTodayEntries().map { entries ->
        val grouped = entries.groupBy { it.category }
        Category.entries.associate { cat ->
            val catEntries = grouped[cat.key] ?: emptyList()
            cat to when (cat) {
                Category.WATER -> {
                    val totalMl = catEntries.sumOf { e ->
                        val data = gson.fromJsonSafe<WaterData>(e.data)
                        if (data?.type == "drink") data.ml else 0
                    }
                    formatMlCompact(totalMl)
                }
                Category.FOOD -> {
                    val logged = catEntries.mapNotNull { e ->
                        gson.fromJsonSafe<FoodData>(e.data)?.mealType
                    }.distinct().size
                    "$logged/${DailyGoals.FOOD_MEALS} meals"
                }
                Category.BATHROOM -> "${catEntries.size} breaks"
                Category.HEALTH -> {
                    val count = catEntries.size
                    if (count == 0) "0/${DailyGoals.HEALTH_ENERGY_CHECKINS} logs"
                    else "$count/${DailyGoals.HEALTH_ENERGY_CHECKINS} logs"
                }
                Category.SLEEP -> {
                    val sleeps = catEntries.mapNotNull { e -> gson.fromJsonSafe<SleepData>(e.data) }
                    val completed = sleeps.firstOrNull { it.wakeTime != null }
                    val partial = sleeps.firstOrNull { it.wakeTime == null }
                    when {
                        completed != null -> {
                            val h = completed.totalHours.toInt()
                            val m = ((completed.totalHours - h) * 60).toInt()
                            "${h}h ${m}m"
                        }
                        partial != null -> "Bedtime ${partial.bedtime}"
                        else -> "Goal: ${DailyGoals.SLEEP_IDEAL_HOURS.toInt()}h"
                    }
                }
                Category.EMOTIONS -> {
                    val count = catEntries.size
                    val last = catEntries.firstOrNull()?.let { e ->
                        gson.fromJsonSafe<EmotionData>(e.data)?.emotion
                    }
                    if (last != null) {
                        "${last.replaceFirstChar { it.uppercase() }} ($count/${DailyGoals.EMOTIONS_CHECKINS})"
                    } else "0/${DailyGoals.EMOTIONS_CHECKINS} check-ins"
                }
                Category.INTERACTIONS -> {
                    val count = catEntries.size
                    "$count/${DailyGoals.INTERACTIONS_MIN} entries"
                }
                Category.CHORES -> {
                    val count = catEntries.size
                    "$count/${DailyGoals.CHORES_TASKS_MIN} done"
                }
                Category.HOBBIES -> {
                    if (catEntries.isEmpty()) "0/${DailyGoals.HOBBIES_DAILY_MIN} min"
                    else "${catEntries.size} sessions"
                }
                Category.IDEAS -> {
                    val count = catEntries.size
                    "$count idea${if (count != 1) "s" else ""}"
                }
                Category.CYCLE -> {
                    val last = catEntries.lastOrNull()?.let { e ->
                        gson.fromJsonSafe<CycleData>(e.data)?.flow
                    }
                    last?.replaceFirstChar { it.uppercase() } ?: "No log"
                }
                Category.BADHABITS -> {
                    if (catEntries.isEmpty()) "Clean day"
                    else {
                        var alcohol = 0; var weed = 0; var tobacco = 0; var selfharm = 0
                        catEntries.forEach { e ->
                            val d = gson.fromJsonSafe<BadHabitsData>(e.data) ?: return@forEach
                            when (d.substance) {
                                "alcohol" -> alcohol += d.count
                                "weed" -> weed += d.count
                                "tobacco" -> tobacco += d.count
                                "selfharm" -> selfharm += d.count
                            }
                        }
                        val parts = mutableListOf<String>()
                        if (alcohol > 0) parts += "\uD83C\uDF77$alcohol"
                        if (weed > 0) parts += "\uD83C\uDF3F$weed"
                        if (tobacco > 0) parts += "\uD83D\uDEAC$tobacco"
                        if (selfharm > 0) parts += "\uD83E\uDE79$selfharm"
                        if (parts.isEmpty()) "Clean day" else parts.joinToString(" ")
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val streaks = combine(
        Category.entries.map { cat ->
            repository.getLoggedDates(cat.key).map { dates ->
                cat to StreakTracker.currentStreak(dates)
            }
        }
    ) { results ->
        results.associate { it }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
}
