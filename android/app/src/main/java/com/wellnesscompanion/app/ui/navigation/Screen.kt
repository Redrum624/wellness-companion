package com.wellnesscompanion.app.ui.navigation

import com.wellnesscompanion.app.data.model.Category

sealed class Screen(val route: String, val category: Category? = null) {
    data object Dashboard : Screen("dashboard")
    data object Water : Screen("water", Category.WATER)
    data object Food : Screen("food", Category.FOOD)
    data object Bathroom : Screen("bathroom", Category.BATHROOM)
    data object Health : Screen("health", Category.HEALTH)
    data object Sleep : Screen("sleep", Category.SLEEP)
    data object Emotions : Screen("emotions", Category.EMOTIONS)
    data object Interactions : Screen("interactions", Category.INTERACTIONS)
    data object Chores : Screen("chores", Category.CHORES)
    data object Hobbies : Screen("hobbies", Category.HOBBIES)
    data object Ideas : Screen("ideas", Category.IDEAS)
    data object Cycle : Screen("cycle", Category.CYCLE)
    data object BadHabits : Screen("badhabits", Category.BADHABITS)

    companion object {
        /** Ordered list of category screens for swipe navigation */
        val categoryScreens = listOf(
            Water, Food, Bathroom, Health, Sleep,
            Emotions, Interactions, Chores, Hobbies, Ideas, Cycle, BadHabits
        )

        fun forCategory(category: Category): Screen = categoryScreens.first { it.category == category }

        fun indexOf(route: String): Int = categoryScreens.indexOfFirst { it.route == route }
    }
}
