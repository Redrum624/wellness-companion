package com.wellnesscompanion.app.ui.badhabits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.wellnesscompanion.app.data.model.BadHabitsData
import com.wellnesscompanion.app.data.repository.EntryRepository
import com.wellnesscompanion.app.util.fromJsonSafe
import com.wellnesscompanion.app.util.todayDateString
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BadHabitsEntry(
    val id: String,
    val timestamp: Long,
    val substance: String,
    val count: Int,
    val level: Int?
)

data class BadHabitsCounts(val alcohol: Int = 0, val weed: Int = 0, val tobacco: Int = 0, val selfharm: Int = 0) {
    val total: Int get() = alcohol + weed + tobacco + selfharm
}

@HiltViewModel
class BadHabitsViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val gson: Gson
) : ViewModel() {

    private val date: String = todayDateString()

    val todayEntries = repository.getTodayEntries("badhabits").map { entries ->
        entries.mapNotNull { e ->
            val d = gson.fromJsonSafe<BadHabitsData>(e.data) ?: return@mapNotNull null
            BadHabitsEntry(e.id, e.timestamp, d.substance, d.count, d.level)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val counts: StateFlow<BadHabitsCounts> = todayEntries.map { list ->
        var a = 0; var w = 0; var t = 0; var s = 0
        list.forEach {
            when (it.substance) {
                "alcohol" -> a += it.count
                "weed" -> w += it.count
                "tobacco" -> t += it.count
                "selfharm" -> s += it.count
            }
        }
        BadHabitsCounts(a, w, t, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BadHabitsCounts())

    private val _alcoholLevel = MutableStateFlow(0)
    val alcoholLevel: StateFlow<Int> = _alcoholLevel.asStateFlow()

    private val _weedLevel = MutableStateFlow(0)
    val weedLevel: StateFlow<Int> = _weedLevel.asStateFlow()

    init {
        viewModelScope.launch {
            _alcoholLevel.value = repository.getSetting(levelKey("alcohol"))?.toIntOrNull() ?: 0
            _weedLevel.value = repository.getSetting(levelKey("weed"))?.toIntOrNull() ?: 0
        }
    }

    fun logConsumption(substance: String) {
        viewModelScope.launch {
            val level = when (substance) {
                "alcohol" -> _alcoholLevel.value
                "weed" -> _weedLevel.value
                else -> null
            }
            repository.addEntry("badhabits", BadHabitsData(substance = substance, count = 1, level = level))
        }
    }

    fun undoLast(substance: String) {
        viewModelScope.launch {
            val match = todayEntries.value.firstOrNull { it.substance == substance } ?: return@launch
            repository.deleteEntryById(match.id)
        }
    }

    fun setLevel(substance: String, value: Int) {
        val v = value.coerceIn(0, 10)
        when (substance) {
            "alcohol" -> _alcoholLevel.value = v
            "weed" -> _weedLevel.value = v
        }
        viewModelScope.launch {
            repository.setSetting(levelKey(substance), v.toString())
        }
    }

    private fun levelKey(substance: String) = "badhabits:$date:$substance:level"
}
