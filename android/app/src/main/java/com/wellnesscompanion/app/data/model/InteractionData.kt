package com.wellnesscompanion.app.data.model

data class InteractionData(
    val people: List<String> = emptyList(),
    val qualityRating: Int = 0, // 1-5 stars
    val journalText: String = "",
    val promptUsed: String? = null
)
