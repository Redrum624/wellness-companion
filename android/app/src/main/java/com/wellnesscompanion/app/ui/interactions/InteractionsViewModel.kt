package com.wellnesscompanion.app.ui.interactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.wellnesscompanion.app.data.local.entity.PersonEntity
import com.wellnesscompanion.app.data.model.InteractionData
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

data class InteractionEntry(
    val timestamp: Long,
    val people: List<String>,
    val qualityRating: Int,
    val journalText: String,
    val promptUsed: String?
)

val reflectionPrompts = listOf(
    "Who made you smile today?",
    "What conversation stuck with you?",
    "Did you feel heard today?",
    "What would you tell your best friend right now?",
    "Who do you want to reach out to?"
)

@HiltViewModel
class InteractionsViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val gson: Gson
) : ViewModel() {

    private val _selectedPeople = MutableStateFlow<Set<String>>(emptySet())
    val selectedPeople = _selectedPeople.asStateFlow()

    private val _rating = MutableStateFlow(0)
    val rating = _rating.asStateFlow()

    private val _journalText = MutableStateFlow("")
    val journalText = _journalText.asStateFlow()

    private val _currentPrompt = MutableStateFlow<String?>(null)
    val currentPrompt = _currentPrompt.asStateFlow()

    val savedPeople = repository.getPeople().map { entities ->
        entities.map { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayEntries = repository.getTodayEntries("interactions").map { entries ->
        entries.mapNotNull { e ->
            val data = gson.fromJsonSafe<InteractionData>(e.data) ?: return@mapNotNull null
            InteractionEntry(e.timestamp, data.people, data.qualityRating, data.journalText, data.promptUsed)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun togglePerson(name: String) {
        val current = _selectedPeople.value.toMutableSet()
        if (name in current) current.remove(name) else current.add(name)
        _selectedPeople.value = current
    }

    fun setRating(stars: Int) { _rating.value = stars }
    fun setJournalText(text: String) { _journalText.value = text }

    fun newPrompt() {
        _currentPrompt.value = reflectionPrompts.random()
    }

    fun addPerson(name: String) {
        viewModelScope.launch {
            repository.addPerson(PersonEntity(UUID.randomUUID().toString(), name, nowMillis()))
        }
    }

    fun logInteraction() {
        if (_journalText.value.isBlank() && _selectedPeople.value.isEmpty()) return
        viewModelScope.launch {
            repository.addEntry("interactions", InteractionData(
                people = _selectedPeople.value.toList(),
                qualityRating = _rating.value,
                journalText = _journalText.value,
                promptUsed = _currentPrompt.value
            ))
            _selectedPeople.value = emptySet()
            _rating.value = 0
            _journalText.value = ""
            _currentPrompt.value = null
        }
    }
}
