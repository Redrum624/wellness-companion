package com.wellnesscompanion.app.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.wellnesscompanion.app.data.model.HealthData
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

data class HealthEntry(
    val timestamp: Long,
    val energyLevel: Int?,
    val dailyRating: Int?,
    val symptoms: List<String>,
    val note: String?
)

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val gson: Gson
) : ViewModel() {

    private val _energyLevel = MutableStateFlow(5)
    val energyLevel = _energyLevel.asStateFlow()

    private val _dailyRating = MutableStateFlow<Int?>(null)
    val dailyRating = _dailyRating.asStateFlow()

    private val _symptoms = MutableStateFlow<Set<String>>(emptySet())
    val symptoms = _symptoms.asStateFlow()

    private val _note = MutableStateFlow("")
    val note = _note.asStateFlow()

    val todayEntries = repository.getTodayEntries("health").map { entries ->
        entries.mapNotNull { e ->
            val data = gson.fromJsonSafe<HealthData>(e.data) ?: return@mapNotNull null
            HealthEntry(e.timestamp, data.energyLevel, data.dailyRating, data.symptoms, data.note)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setEnergyLevel(level: Int) { _energyLevel.value = level }
    fun setDailyRating(rating: Int) { _dailyRating.value = rating }
    fun setNote(text: String) { _note.value = text }

    fun toggleSymptom(symptom: String) {
        val current = _symptoms.value.toMutableSet()
        if (symptom in current) current.remove(symptom) else current.add(symptom)
        _symptoms.value = current
    }

    fun logHealth() {
        viewModelScope.launch {
            repository.addEntry("health", HealthData(
                energyLevel = _energyLevel.value,
                dailyRating = _dailyRating.value,
                symptoms = _symptoms.value.toList(),
                note = _note.value.ifBlank { null }
            ))
            // Reset form
            _dailyRating.value = null
            _symptoms.value = emptySet()
            _note.value = ""
        }
    }
}
