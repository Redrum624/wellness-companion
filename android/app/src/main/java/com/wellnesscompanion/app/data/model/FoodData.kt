package com.wellnesscompanion.app.data.model

data class FoodData(
    val mealType: String, // "breakfast", "lunch", "dinner", "snacks"
    val description: String,
    val photoPath: String? = null
)
