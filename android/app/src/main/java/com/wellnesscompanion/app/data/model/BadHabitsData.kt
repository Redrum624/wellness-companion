package com.wellnesscompanion.app.data.model

data class BadHabitsData(
    val substance: String, // "alcohol", "weed", "tobacco"
    val count: Int = 1,
    val level: Int? = null, // 0-10, alcohol/weed only
    val note: String? = null
)
