package com.wellnesscompanion.app.data.model

/** Default daily goals from DAILY_GOALS.md — evidence-based starting points */
object DailyGoals {
    // Water: 2.7L total fluid, ~2.2L from drinks, minimum 1.5L plain water
    // App default: 3 full bottles of 900ml = 2700ml
    const val WATER_ML = 2700
    const val WATER_BOTTLES = 3
    const val WATER_BOTTLE_DEFAULT_ML = 900

    // Food: 3 meals + 1-2 snacks
    const val FOOD_MEALS = 3

    // Bathroom: 6-8 urinations per day (just tracking, no hard goal)
    const val BATHROOM_NORMAL_MIN = 4
    const val BATHROOM_NORMAL_MAX = 10

    // Health: 2-3 energy check-ins per day, target average 6+
    const val HEALTH_ENERGY_CHECKINS = 2
    const val HEALTH_TARGET_ENERGY = 6

    // Sleep: 7-9 hours, ideal 8h
    const val SLEEP_MIN_HOURS = 7f
    const val SLEEP_MAX_HOURS = 9f
    const val SLEEP_IDEAL_HOURS = 8f

    // Emotions: 2-3 mood check-ins per day
    const val EMOTIONS_CHECKINS = 2

    // Interactions: 1+ meaningful interaction per day
    const val INTERACTIONS_MIN = 1

    // Chores: 3-5 tasks completed per day, 60-70% completion rate
    const val CHORES_TASKS_MIN = 3
    const val CHORES_COMPLETION_TARGET = 0.65f

    // Hobbies: 20-30 min/day minimum, 120 min/week
    const val HOBBIES_DAILY_MIN = 20
    const val HOBBIES_WEEKLY_MIN = 120
}
