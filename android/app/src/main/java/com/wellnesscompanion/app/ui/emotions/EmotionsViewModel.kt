package com.wellnesscompanion.app.ui.emotions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.wellnesscompanion.app.data.model.EmotionData
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

data class EmotionEntry(
    val timestamp: Long,
    val emotion: String,
    val note: String?
)

@HiltViewModel
class EmotionsViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val gson: Gson
) : ViewModel() {

    private val _selectedMood = MutableStateFlow<String?>(null)
    val selectedMood = _selectedMood.asStateFlow()

    private val _note = MutableStateFlow("")
    val note = _note.asStateFlow()

    val todayEntries = repository.getTodayEntries("emotions").map { entries ->
        entries.mapNotNull { e ->
            val data = gson.fromJsonSafe<EmotionData>(e.data) ?: return@mapNotNull null
            EmotionEntry(e.timestamp, data.emotion, data.note)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectMood(emotion: String) {
        _selectedMood.value = emotion
    }

    fun setNote(text: String) {
        _note.value = text
    }

    fun logEmotion() {
        val emotion = _selectedMood.value ?: return
        viewModelScope.launch {
            repository.addEntry("emotions", EmotionData(emotion, _note.value.ifBlank { null }))
            _selectedMood.value = null
            _note.value = ""
        }
    }
}
