package com.wellnesscompanion.app.ui.hobbies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.wellnesscompanion.app.data.local.entity.HobbyEntity
import com.wellnesscompanion.app.data.model.DailyGoals
import com.wellnesscompanion.app.data.model.HobbyData
import com.wellnesscompanion.app.data.repository.EntryRepository
import com.wellnesscompanion.app.util.fromJsonSafe
import com.wellnesscompanion.app.util.nowMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class HobbySession(
    val timestamp: Long,
    val hobbyName: String,
    val durationMin: Int,
    val color: String
)

@HiltViewModel
class HobbiesViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val gson: Gson
) : ViewModel() {

    val savedHobbies = repository.getHobbies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todaySessions = repository.getTodayEntries("hobbies").map { entries ->
        entries.mapNotNull { e ->
            val data = gson.fromJsonSafe<HobbyData>(e.data) ?: return@mapNotNull null
            HobbySession(e.timestamp, data.hobbyName, data.durationMin, data.color)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalMinutesToday = todaySessions.map { sessions ->
        sessions.sumOf { it.durationMin }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Total cranes = totalMinutes / 5 */
    val craneCount = totalMinutesToday.map { it / 5 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Crane colors — one per 5-min block, colored by the hobby that earned it */
    val craneColors = todaySessions.map { sessions ->
        val colors = mutableListOf<String>()
        sessions.sortedBy { it.timestamp }.forEach { session ->
            repeat(session.durationMin / 5) {
                colors.add(session.color)
            }
        }
        colors
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _lastAddedCraneIndex = MutableStateFlow(-1)
    val lastAddedCraneIndex = _lastAddedCraneIndex.asStateFlow()

    fun addHobby(name: String, color: String) {
        viewModelScope.launch {
            repository.addHobby(HobbyEntity(UUID.randomUUID().toString(), name, color, nowMillis()))
        }
    }

    fun logTime(hobby: HobbyEntity, minutes: Int) {
        viewModelScope.launch {
            val prevCount = craneCount.value
            repository.addEntry("hobbies", HobbyData(hobby.name, minutes, hobby.color))
            // Trigger animation for new cranes
            _lastAddedCraneIndex.value = prevCount
        }
    }
}
