package com.wellnesscompanion.app.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.wellnesscompanion.app.data.local.entity.EntryEntity
import com.wellnesscompanion.app.data.model.SleepData
import com.wellnesscompanion.app.data.repository.EntryRepository
import com.wellnesscompanion.app.util.fromJsonSafe
import com.wellnesscompanion.app.util.nowMillis
import com.wellnesscompanion.app.util.todayDateString
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SleepLogState {
    data object None : SleepLogState
    data class BedtimeSaved(val entry: EntryEntity, val data: SleepData) : SleepLogState
    data class Complete(val entry: EntryEntity, val data: SleepData) : SleepLogState
}

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

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    /**
     * The save flow is an upsert against the latest sleep entry:
     *  - wakeTime == null and recent  -> a bedtime waiting for its wake-up
     *  - wakeTime != null and today   -> tonight is logged
     *  - anything else                -> fresh slate (old nights stay in history)
     */
    private fun resolveState(entry: EntryEntity?): SleepLogState {
        if (entry == null) return SleepLogState.None
        val data = gson.fromJsonSafe<SleepData>(entry.data) ?: return SleepLogState.None
        return when {
            data.wakeTime == null && nowMillis() - entry.modifiedAt <= OPEN_ENTRY_MAX_AGE_MS ->
                SleepLogState.BedtimeSaved(entry, data)
            data.wakeTime != null && entry.date == todayDateString() ->
                SleepLogState.Complete(entry, data)
            else -> SleepLogState.None
        }
    }

    val logState: StateFlow<SleepLogState> = repository.getLatestEntry("sleep")
        .map { resolveState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SleepLogState.None)

    init {
        recalculate()
        viewModelScope.launch {
            logState.collect { state ->
                when (state) {
                    is SleepLogState.BedtimeSaved -> {
                        _bedtime.value = state.data.bedtime
                        _wakeUps.value = state.data.wakeUps
                        recalculate()
                    }
                    is SleepLogState.Complete -> {
                        _bedtime.value = state.data.bedtime
                        state.data.wakeTime?.let { _wakeTime.value = it }
                        _wakeUps.value = state.data.wakeUps
                        recalculate()
                    }
                    SleepLogState.None -> Unit
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

    fun saveBedtime() {
        viewModelScope.launch {
            if (_saving.value) return@launch
            _saving.value = true
            try {
                when (val state = resolveState(repository.getLatestEntry("sleep").first())) {
                    is SleepLogState.BedtimeSaved -> updateData(
                        state.entry,
                        state.data.copy(bedtime = _bedtime.value, wakeUps = _wakeUps.value)
                    )
                    is SleepLogState.Complete ->
                        // Tonight is a new night; the completed row stays as-is.
                        repository.addEntry("sleep", SleepData(bedtime = _bedtime.value))
                    SleepLogState.None -> repository.addEntry(
                        "sleep",
                        SleepData(bedtime = _bedtime.value, wakeTime = null, wakeUps = _wakeUps.value)
                    )
                }
            } finally {
                _saving.value = false
            }
        }
    }

    fun saveWakeUp() {
        viewModelScope.launch {
            if (_saving.value) return@launch
            _saving.value = true
            try {
                when (val state = resolveState(repository.getLatestEntry("sleep").first())) {
                    is SleepLogState.BedtimeSaved -> repository.updateEntry(
                        // The night belongs to the day you woke up on, matching how a
                        // one-shot morning save has always been dated.
                        state.entry.copy(date = todayDateString(), data = gson.toJson(completedData(state.data)))
                    )
                    is SleepLogState.Complete -> updateData(state.entry, completedData(state.data))
                    SleepLogState.None -> repository.addEntry("sleep", completedData(SleepData(bedtime = _bedtime.value)))
                }
            } finally {
                _saving.value = false
            }
        }
    }

    private fun completedData(base: SleepData): SleepData {
        val hours = computeTotalHours(_bedtime.value, _wakeTime.value)
        return base.copy(
            bedtime = _bedtime.value,
            wakeTime = _wakeTime.value,
            wakeUps = _wakeUps.value,
            totalHours = hours,
            qualityScore = computeQualityScore(hours, _wakeUps.value.size)
        )
    }

    private suspend fun updateData(entry: EntryEntity, data: SleepData) {
        repository.updateEntry(entry.copy(data = gson.toJson(data)))
    }

    companion object {
        /** A bedtime older than this is an abandoned night, not one awaiting its wake-up. */
        private const val OPEN_ENTRY_MAX_AGE_MS = 36L * 60 * 60 * 1000

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
