package com.wellnesscompanion.app.data.model

data class EmotionData(
    val emotion: String, // "happy", "calm", "sad", "angry", "anxious", "tired"
    val note: String? = null
)
