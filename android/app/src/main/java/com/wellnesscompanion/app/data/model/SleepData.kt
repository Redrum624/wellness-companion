package com.wellnesscompanion.app.data.model

data class SleepData(
    val bedtime: String, // "HH:mm"
    val wakeTime: String, // "HH:mm"
    val wakeUps: List<String> = emptyList(), // List of "HH:mm"
    val totalHours: Float = 0f,
    val qualityScore: Int = 0
)
