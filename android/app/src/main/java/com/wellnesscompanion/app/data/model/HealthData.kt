package com.wellnesscompanion.app.data.model

data class HealthData(
    val energyLevel: Int? = null, // 1-10
    val dailyRating: Int? = null, // 1-10
    val symptoms: List<String> = emptyList(),
    val note: String? = null
)
