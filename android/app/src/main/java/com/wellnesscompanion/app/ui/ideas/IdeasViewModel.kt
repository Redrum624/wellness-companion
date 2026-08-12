package com.wellnesscompanion.app.ui.ideas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.wellnesscompanion.app.data.local.entity.EntryEntity
import com.wellnesscompanion.app.data.model.IdeaData
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
import javax.inject.Inject

data class IdeaEntry(
    val id: String,
    val timestamp: Long,
    val title: String,
    val body: String,
    val tags: List<String>
)

@HiltViewModel
class IdeasViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val gson: Gson
) : ViewModel() {

    private val _title = MutableStateFlow("")
    val title = _title.asStateFlow()

    private val _body = MutableStateFlow("")
    val body = _body.asStateFlow()

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags = _tags.asStateFlow()

    private val _editingEntry = MutableStateFlow<EntryEntity?>(null)
    val editingEntry = _editingEntry.asStateFlow()

    private val _tagInput = MutableStateFlow("")
    val tagInput = _tagInput.asStateFlow()

    val todayEntries = repository.getTodayEntries("ideas").map { entries ->
        entries.mapNotNull { e ->
            val data = gson.fromJsonSafe<IdeaData>(e.data) ?: return@mapNotNull null
            IdeaEntry(e.id, e.timestamp, data.title, data.body, data.tags)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Keep raw entries for edit/delete operations
    private val rawEntries = repository.getTodayEntries("ideas")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTitle(t: String) { _title.value = t }
    fun setBody(b: String) { _body.value = b }
    fun setTagInput(t: String) { _tagInput.value = t }

    fun addTag() {
        val tag = _tagInput.value.trim()
        if (tag.isNotEmpty() && tag !in _tags.value) {
            _tags.value = _tags.value + tag
        }
        _tagInput.value = ""
    }

    fun removeTag(tag: String) {
        _tags.value = _tags.value - tag
    }

    fun save() {
        val t = _title.value.trim()
        val b = _body.value.trim()
        if (t.isEmpty() && b.isEmpty()) return
        viewModelScope.launch {
            val data = IdeaData(t, b, _tags.value)
            val editing = _editingEntry.value
            if (editing != null) {
                repository.updateEntry(editing.copy(data = gson.toJson(data)))
                _editingEntry.value = null
            } else {
                repository.addEntry("ideas", data)
            }
            _title.value = ""
            _body.value = ""
            _tags.value = emptyList()
        }
    }

    fun startEdit(ideaEntry: IdeaEntry) {
        val raw = rawEntries.value.find { it.id == ideaEntry.id } ?: return
        _editingEntry.value = raw
        _title.value = ideaEntry.title
        _body.value = ideaEntry.body
        _tags.value = ideaEntry.tags
    }

    fun cancelEdit() {
        _editingEntry.value = null
        _title.value = ""
        _body.value = ""
        _tags.value = emptyList()
    }

    fun delete(ideaEntry: IdeaEntry) {
        val raw = rawEntries.value.find { it.id == ideaEntry.id } ?: return
        viewModelScope.launch {
            repository.deleteEntry(raw)
            if (_editingEntry.value?.id == ideaEntry.id) cancelEdit()
        }
    }
}
