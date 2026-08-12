package com.wellnesscompanion.app.ui.bathroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.wellnesscompanion.app.data.model.BathroomData
import com.wellnesscompanion.app.data.repository.EntryRepository
import com.wellnesscompanion.app.util.fromJsonSafe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BathroomEntry(val timestamp: Long, val type: String?, val note: String?)

@HiltViewModel
class BathroomViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val gson: Gson
) : ViewModel() {

    val todayEntries = repository.getTodayEntries("bathroom").map { entries ->
        entries.mapNotNull { e ->
            val data = gson.fromJsonSafe<BathroomData>(e.data) ?: return@mapNotNull null
            BathroomEntry(e.timestamp, data.type, data.note)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayCount = todayEntries.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun logNow(type: String? = null, note: String? = null) {
        viewModelScope.launch {
            repository.addEntry("bathroom", BathroomData(type, note?.ifBlank { null }))
        }
    }
}
