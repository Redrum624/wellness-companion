package com.wellnesscompanion.app.data.model

data class ChoreTask(
    val name: String,
    val category: String? = null,
    val completed: Boolean = false,
    val timeSpentMin: Int? = null,
    val completedAt: Long? = null
)

data class ChoreData(
    val tasks: List<ChoreTask> = emptyList()
)
