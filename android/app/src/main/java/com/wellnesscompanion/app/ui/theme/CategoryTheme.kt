package com.wellnesscompanion.app.ui.theme

import androidx.compose.ui.graphics.Color

data class CategoryColors(
    val cardBg: Color,
    val screenBg: Color,
    val textColor: Color
)

val categoryColorMap = mapOf(
    "water" to CategoryColors(WaterCardBg, WaterScreenBg, WaterText),
    "food" to CategoryColors(FoodCardBg, FoodScreenBg, FoodText),
    "bathroom" to CategoryColors(BathroomCardBg, BathroomScreenBg, BathroomText),
    "health" to CategoryColors(HealthCardBg, HealthScreenBg, HealthText),
    "sleep" to CategoryColors(SleepCardBg, SleepScreenBg, SleepText),
    "emotions" to CategoryColors(EmotionsCardBg, EmotionsScreenBg, EmotionsText),
    "interactions" to CategoryColors(InteractionsCardBg, InteractionsScreenBg, InteractionsText),
    "chores" to CategoryColors(ChoresCardBg, ChoresScreenBg, ChoresText),
    "hobbies" to CategoryColors(HobbiesCardBg, HobbiesScreenBg, HobbiesText),
    "ideas" to CategoryColors(IdeasCardBg, IdeasScreenBg, IdeasText),
    "cycle" to CategoryColors(CycleCardBg, CycleScreenBg, CycleText),
    "badhabits" to CategoryColors(BadHabitsCardBg, BadHabitsScreenBg, BadHabitsText)
)
