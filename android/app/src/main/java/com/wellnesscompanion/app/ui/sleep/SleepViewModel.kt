package com.wellnesscompanion.app.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.wellnesscompanion.app.data.model.SleepData
import com.wellnesscompanion.app.data.repository.EntryRepository
import com.wellnesscompanion.app.util.fromJsonSafe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val gson: Gson
) : ViewModel() {

    private val _bedtime = MutableStateFlow("23:00")
    val bedtime = _bedtime.asStateFlow()

    private val _wakeTime = MutableStateFlow("07:00")
    val wakeTime = _wakeTime.asStateFlow()

    private val _wakeUps = MutableStateFlow<List<String>>(emptyList())
    val wakeUps = _wakeUps.asStateFlow()

    private val _totalHours = MutableStateFlow(0f)
    val totalHours = _totalHours.asStateFlow()

    private val _qualityScore = MutableStateFlow(0)
    val qualityScore = _qualityScore.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()

    val todayEntry = repository.getTodayEntries("sleep").map { entries ->
        entries.firstOrNull()?.let { e -> gson.fromJsonSafe<SleepData>(e.data) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        recalculate()
        viewModelScope.launch {
            todayEntry.collect { existing ->
                if (existing != null) {
                    _bedtime.value = existing.bedtime
                    _wakeTime.value = existing.wakeTime
                    _wakeUps.value = existing.wakeUps
                    _saved.value = true
                    recalculate()
                }
            }
        }
    }

    fun setBedtime(time: String) {
        _bedtime.value = time
        recalculate()
    }

    fun setWakeTime(time: String) {
        _wakeTime.value = time
        recalculate()
    }

    fun addWakeUp(time: String) {
        _wakeUps.value = _wakeUps.value + time
        recalculate()
    }

    fun removeWakeUp(index: Int) {
        _wakeUps.value = _wakeUps.value.toMutableList().apply { removeAt(index) }
        recalculate()
    }

    private fun recalculate() {
        val hours = computeTotalHours(_bedtime.value, _wakeTime.value)
        _totalHours.value = hours
        _qualityScore.value = computeQualityScore(hours, _wakeUps.value.size)
    }

    fun saveSleep() {
        viewModelScope.launch {
            val data = SleepData(
                bedtime = _bedtime.value,
                wakeTime = _wakeTime.value,
                wakeUps = _wakeUps.value,
                totalHours = _totalHours.value,
                qualityScore = _qualityScore.value
            )
            repository.addEntry("sleep", data)
            _saved.value = true
        }
    }

    companion object {
        fun computeTotalHours(bedtime: String, wakeTime: String): Float {
            val (bh, bm) = bedtime.split(":").map { it.toIntOrNull() ?: 0 }
            val (wh, wm) = wakeTime.split(":").map { it.toIntOrNull() ?: 0 }
            val bedMinutes = bh * 60 + bm
            val wakeMinutes = wh * 60 + wm
            val diff = if (wakeMinutes > bedMinutes) {
                wakeMinutes - bedMinutes
            } else {
                (24 * 60 - bedMinutes) + wakeMinutes
            }
            return diff / 60f
        }

        /** Quality score: start at 10, -1 per hour under 7, -0.5 per extra wake-up, floor 1, cap 10 */
        fun computeQualityScore(totalHours: Float, wakeUpCount: Int): Int {
            var score = 10f
            if (totalHours < 7f) {
                score -= (7f - totalHours)
            }
            if (wakeUpCount > 1) {
                score -= (wakeUpCount - 1) * 0.5f
            }
            return score.coerceIn(1f, 10f).toInt()
        }
    }
}
