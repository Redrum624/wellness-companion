package com.wellnesscompanion.app.ui.chores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.wellnesscompanion.app.data.local.entity.ChoreTemplateEntity
import com.wellnesscompanion.app.data.model.ChoreData
import com.wellnesscompanion.app.data.model.ChoreTask
import com.wellnesscompanion.app.data.repository.EntryRepository
import com.wellnesscompanion.app.util.fromJsonSafe
import com.wellnesscompanion.app.util.nowMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChoresViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val gson: Gson
) : ViewModel() {

    // Today's tasks from the single chore entry for today
    // We store one entry per day with all tasks inside
    val todayChoreData = repository.getTodayEntries("chores").map { entries ->
        entries.firstOrNull()?.let { e ->
            gson.fromJsonSafe<ChoreData>(e.data)
        } ?: ChoreData()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChoreData())

    private val _todayEntryId = MutableStateFlow<String?>(null)

    val tasks = todayChoreData.map { it.tasks }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedCount = tasks.map { list -> list.count { it.completed } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalCount = tasks.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val templates = repository.getChoreTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active timer
    private val _timerTask = MutableStateFlow<String?>(null)
    val timerTask = _timerTask.asStateFlow()

    private val _timerStartTime = MutableStateFlow(0L)
    val timerStartTime = _timerStartTime.asStateFlow()

    init {
        // Track the entry ID for updates
        viewModelScope.launch {
            repository.getTodayEntries("chores").collect { entries ->
                _todayEntryId.value = entries.firstOrNull()?.id
            }
        }
    }

    fun addTask(name: String) {
        viewModelScope.launch {
            val current = todayChoreData.value
            val updated = current.copy(tasks = current.tasks + ChoreTask(name = name))
            saveTasks(updated)
        }
    }

    fun toggleTask(index: Int) {
        viewModelScope.launch {
            val current = todayChoreData.value
            val updatedTasks = current.tasks.toMutableList()
            if (index in updatedTasks.indices) {
                val task = updatedTasks[index]
                updatedTasks[index] = task.copy(
                    completed = !task.completed,
                    completedAt = if (!task.completed) nowMillis() else null
                )
                saveTasks(current.copy(tasks = updatedTasks))
            }
        }
    }

    fun startTimer(taskName: String) {
        _timerTask.value = taskName
        _timerStartTime.value = nowMillis()
    }

    fun stopTimer() {
        val taskName = _timerTask.value ?: return
        val elapsed = ((nowMillis() - _timerStartTime.value) / 60000).toInt().coerceAtLeast(1)

        viewModelScope.launch {
            val current = todayChoreData.value
            val updatedTasks = current.tasks.toMutableList()
            val idx = updatedTasks.indexOfFirst { it.name == taskName }
            if (idx >= 0) {
                val task = updatedTasks[idx]
                updatedTasks[idx] = task.copy(timeSpentMin = (task.timeSpentMin ?: 0) + elapsed)
                saveTasks(current.copy(tasks = updatedTasks))
            }
        }
        _timerTask.value = null
        _timerStartTime.value = 0L
    }

    fun addTemplate(name: String) {
        viewModelScope.launch {
            repository.addChoreTemplate(ChoreTemplateEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                createdAt = nowMillis()
            ))
        }
    }

    private suspend fun saveTasks(data: ChoreData) {
        val existingId = _todayEntryId.value
        if (existingId != null) {
            // A one-shot read, NOT collect{}. `return@collect` only returns from
            // the lambda, so the Flow stayed subscribed — and because updateEntry
            // writes to the same table, Room re-emitted and the collector wrote
            // again, forever. Each call also leaked a suspended coroutine.
            val entry = repository.getTodayEntries("chores").first().firstOrNull()
            entry?.let { repository.updateEntry(it.copy(data = gson.toJson(data))) }
        } else {
            // Create new entry
            repository.addEntry("chores", data)
        }
    }
}
