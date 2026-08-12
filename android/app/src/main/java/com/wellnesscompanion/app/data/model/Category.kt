package com.wellnesscompanion.app.data.model

import com.wellnesscompanion.app.ui.theme.*

enum class Category(
    val key: String,
    val displayName: String,
    val icon: String,
    val colors: com.wellnesscompanion.app.ui.theme.CategoryColors
) {
    WATER("water", "Water", "\uD83D\uDCA7", CategoryColors(WaterCardBg, WaterScreenBg, WaterText)),
    FOOD("food", "Food", "\uD83E\uDD6A", CategoryColors(FoodCardBg, FoodScreenBg, FoodText)),
    BATHROOM("bathroom", "Bathroom", "\uD83D\uDEBD", CategoryColors(BathroomCardBg, BathroomScreenBg, BathroomText)),
    HEALTH("health", "Health", "\uD83D\uDC9A", CategoryColors(HealthCardBg, HealthScreenBg, HealthText)),
    SLEEP("sleep", "Sleep", "\uD83C\uDF19", CategoryColors(SleepCardBg, SleepScreenBg, SleepText)),
    EMOTIONS("emotions", "Emotions", "\uD83C\uDF3B", CategoryColors(EmotionsCardBg, EmotionsScreenBg, EmotionsText)),
    INTERACTIONS("interactions", "Journal", "\uD83D\uDCAC", CategoryColors(InteractionsCardBg, InteractionsScreenBg, InteractionsText)),
    CHORES("chores", "Chores", "\u2705", CategoryColors(ChoresCardBg, ChoresScreenBg, ChoresText)),
    HOBBIES("hobbies", "Hobbies", "\uD83C\uDFA8", CategoryColors(HobbiesCardBg, HobbiesScreenBg, HobbiesText)),
    IDEAS("ideas", "Ideas", "\uD83D\uDCA1", CategoryColors(IdeasCardBg, IdeasScreenBg, IdeasText)),
    CYCLE("cycle", "Cycle", "\uD83E\uDE78", CategoryColors(CycleCardBg, CycleScreenBg, CycleText)),
    BADHABITS("badhabits", "Bad Habits", "\u26A0\uFE0F", CategoryColors(BadHabitsCardBg, BadHabitsScreenBg, BadHabitsText));

    companion object {
        fun fromKey(key: String): Category? = entries.find { it.key == key }
    }
}
