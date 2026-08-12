package com.wellnesscompanion.app.ui.water

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.wellnesscompanion.app.data.model.DailyGoals
import com.wellnesscompanion.app.data.model.WaterData
import com.wellnesscompanion.app.data.repository.EntryRepository
import com.wellnesscompanion.app.notification.RefillNotificationWorker
import com.wellnesscompanion.app.util.fromJsonSafe
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class WaterViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val gson: Gson,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _capacity = MutableStateFlow(DailyGoals.WATER_BOTTLE_DEFAULT_ML)
    val capacity = _capacity.asStateFlow()

    private val _lastDelta = MutableStateFlow<String?>(null)
    val lastDelta = _lastDelta.asStateFlow()

    val dailyGoalMl = DailyGoals.WATER_ML

    val todayEntries = repository.getTodayEntries("water").map { entries ->
        entries.mapNotNull { e ->
            val data = gson.fromJsonSafe<WaterData>(e.data) ?: return@mapNotNull null
            Pair(e.timestamp, data)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalConsumed = todayEntries.map { entries ->
        entries.filter { it.second.type == "drink" }.sumOf { it.second.ml }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Fraction of daily goal achieved */
    val dailyProgress = totalConsumed.map { consumed ->
        (consumed.toFloat() / dailyGoalMl).coerceAtMost(1f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    /**
     * Current bottle fill level (0.0 to 1.0). Starts full.
     * Replays today's entries to compute current level.
     */
    val bottleFill = combine(todayEntries, _capacity) { entries, cap ->
        if (cap <= 0) return@combine 1f
        var currentMl = cap
        entries.sortedBy { it.first }.forEach { (_, data) ->
            if (data.type == "drink") {
                currentMl = (currentMl - data.ml).coerceAtLeast(0)
            } else {
                currentMl = (currentMl + data.ml).coerceAtMost(cap)
            }
        }
        currentMl.toFloat() / cap.toFloat()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1f)

    init {
        viewModelScope.launch {
            repository.getSetting("water_capacity")?.toIntOrNull()?.let {
                _capacity.value = it
            }
        }

        // Watch bottle fill level — schedule refill reminder when near empty
        viewModelScope.launch {
            bottleFill.collect { fill ->
                if (fill <= 0.1f) {
                    scheduleRefillReminder()
                } else {
                    cancelRefillReminder()
                }
            }
        }
    }

    fun setCapacity(ml: Int) {
        _capacity.value = ml
        viewModelScope.launch {
            repository.setSetting("water_capacity", ml.toString())
        }
    }

    fun logDrink(ml: Int) {
        viewModelScope.launch {
            repository.addEntry("water", WaterData(ml, "drink", _capacity.value))
            _lastDelta.value = "-${ml} ml consumed"
        }
    }

    fun logRefill() {
        viewModelScope.launch {
            val cap = _capacity.value
            val currentFill = bottleFill.value
            val currentMl = (currentFill * cap).toInt()
            val refillAmount = cap - currentMl
            if (refillAmount > 0) {
                repository.addEntry("water", WaterData(refillAmount, "refill", cap))
                _lastDelta.value = "+${refillAmount} ml refilled"
                cancelRefillReminder()
            }
        }
    }

    fun clearDelta() {
        _lastDelta.value = null
    }

    private fun scheduleRefillReminder() {
        val request = OneTimeWorkRequestBuilder<RefillNotificationWorker>()
            .setInitialDelay(15, TimeUnit.MINUTES)
            .addTag(RefillNotificationWorker.WORK_TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                RefillNotificationWorker.WORK_TAG,
                ExistingWorkPolicy.KEEP, // Don't restart if already scheduled
                request
            )
    }

    private fun cancelRefillReminder() {
        WorkManager.getInstance(context)
            .cancelUniqueWork(RefillNotificationWorker.WORK_TAG)
    }
}
