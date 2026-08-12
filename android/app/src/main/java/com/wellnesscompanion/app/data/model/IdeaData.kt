package com.wellnesscompanion.app.data.model

data class IdeaData(
    val title: String = "",
    val body: String = "",
    val tags: List<String> = emptyList()
)
