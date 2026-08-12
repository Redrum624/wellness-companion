package com.wellnesscompanion.app.data.model

data class WaterData(
    val ml: Int,
    val type: String, // "drink" or "refill"
    val bottleCapacity: Int = 900
)
