package com.wellnesscompanion.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wellnesscompanion.app.data.model.Category
import com.wellnesscompanion.app.ui.components.ScreenScaffold
import com.wellnesscompanion.app.ui.bathroom.BathroomScreen
import com.wellnesscompanion.app.ui.chores.ChoresScreen
import com.wellnesscompanion.app.ui.dashboard.DashboardScreen
import com.wellnesscompanion.app.ui.emotions.EmotionsScreen
import com.wellnesscompanion.app.ui.food.FoodScreen
import com.wellnesscompanion.app.ui.health.HealthScreen
import com.wellnesscompanion.app.ui.hobbies.HobbiesScreen
import com.wellnesscompanion.app.ui.interactions.InteractionsScreen
import com.wellnesscompanion.app.ui.sleep.SleepScreen
import com.wellnesscompanion.app.ui.water.WaterScreen
import com.wellnesscompanion.app.ui.ideas.IdeasScreen
import com.wellnesscompanion.app.ui.cycle.CycleScreen
import com.wellnesscompanion.app.ui.badhabits.BadHabitsScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onCategoryClick = { category ->
                    val screen = Screen.forCategory(category)
                    navController.navigate(screen.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // Phase 1 screens
        composable(Screen.Water.route) {
            CategoryScreenWrapper(navController, Screen.Water) {
                WaterScreen()
            }
        }
        composable(Screen.Food.route) {
            CategoryScreenWrapper(navController, Screen.Food) {
                FoodScreen()
            }
        }
        composable(Screen.Sleep.route) {
            CategoryScreenWrapper(navController, Screen.Sleep) {
                SleepScreen()
            }
        }
        composable(Screen.Emotions.route) {
            CategoryScreenWrapper(navController, Screen.Emotions) {
                EmotionsScreen()
            }
        }

        // Phase 2 screens
        composable(Screen.Bathroom.route) {
            CategoryScreenWrapper(navController, Screen.Bathroom) { BathroomScreen() }
        }
        composable(Screen.Health.route) {
            CategoryScreenWrapper(navController, Screen.Health) { HealthScreen() }
        }
        composable(Screen.Interactions.route) {
            CategoryScreenWrapper(navController, Screen.Interactions) { InteractionsScreen() }
        }
        composable(Screen.Chores.route) {
            CategoryScreenWrapper(navController, Screen.Chores) { ChoresScreen() }
        }
        composable(Screen.Hobbies.route) {
            CategoryScreenWrapper(navController, Screen.Hobbies) { HobbiesScreen() }
        }
        composable(Screen.Ideas.route) {
            CategoryScreenWrapper(navController, Screen.Ideas) { IdeasScreen() }
        }
        composable(Screen.Cycle.route) {
            CategoryScreenWrapper(navController, Screen.Cycle) { CycleScreen() }
        }
        composable(Screen.BadHabits.route) {
            CategoryScreenWrapper(navController, Screen.BadHabits) { BadHabitsScreen() }
        }
    }
}

@Composable
private fun CategoryScreenWrapper(
    navController: NavHostController,
    screen: Screen,
    content: @Composable () -> Unit
) {
    val category = screen.category ?: return
    val currentIndex = Screen.indexOf(screen.route)
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(screen.route) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onDragEnd = {
                        val screens = Screen.categoryScreens
                        if (dragAccumulator > 80f && currentIndex > 0) {
                            navController.navigate(screens[currentIndex - 1].route) {
                                popUpTo(Screen.Dashboard.route)
                                launchSingleTop = true
                            }
                        } else if (dragAccumulator < -80f && currentIndex < screens.size - 1) {
                            navController.navigate(screens[currentIndex + 1].route) {
                                popUpTo(Screen.Dashboard.route)
                                launchSingleTop = true
                            }
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccumulator += dragAmount
                    }
                )
            }
    ) {
        ScreenScaffold(
            category = category,
            currentIndex = currentIndex,
            onBack = {
                navController.popBackStack(Screen.Dashboard.route, inclusive = false)
            },
            onHomeTap = {
                navController.popBackStack(Screen.Dashboard.route, inclusive = false)
            },
            onDotTap = { index ->
                val target = Screen.categoryScreens[index]
                navController.navigate(target.route) {
                    popUpTo(Screen.Dashboard.route)
                    launchSingleTop = true
                }
            },
            content = content
        )
    }
}

@Composable
private fun PlaceholderContent(category: Category) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 100.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = category.icon,
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                text = "Coming in Phase 2",
                style = MaterialTheme.typography.bodyLarge,
                color = category.colors.textColor.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
