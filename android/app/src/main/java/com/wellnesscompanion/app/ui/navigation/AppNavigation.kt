package com.wellnesscompanion.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.navigation.NavBackStackEntry
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

private const val MotionDurationMillis = 300

/** Gentle fade + scale used for dashboard <-> category transitions. */
private val FadeScaleEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(tween(MotionDurationMillis, easing = FastOutSlowInEasing)) +
        scaleIn(tween(MotionDurationMillis, easing = FastOutSlowInEasing), initialScale = 0.96f)
}

private val FadeScaleExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(tween(MotionDurationMillis, easing = FastOutSlowInEasing)) +
        scaleOut(tween(MotionDurationMillis, easing = FastOutSlowInEasing), targetScale = 0.96f)
}

/**
 * Direction-aware slide+fade between category screens, using the existing category route
 * order (Screen.categoryScreens) to decide which way to slide. Falls back to the gentle
 * fade+scale whenever the other end of the transition is the dashboard.
 */
private val CategoryEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (initialState.destination.route == Screen.Dashboard.route) {
        FadeScaleEnter()
    } else {
        val fromIndex = Screen.indexOf(initialState.destination.route.orEmpty())
        val toIndex = Screen.indexOf(targetState.destination.route.orEmpty())
        val forward = toIndex >= fromIndex
        slideInHorizontally(tween(MotionDurationMillis, easing = FastOutSlowInEasing)) { fullWidth ->
            if (forward) fullWidth else -fullWidth
        } + fadeIn(tween(MotionDurationMillis, easing = FastOutSlowInEasing))
    }
}

private val CategoryExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (targetState.destination.route == Screen.Dashboard.route) {
        FadeScaleExit()
    } else {
        val fromIndex = Screen.indexOf(initialState.destination.route.orEmpty())
        val toIndex = Screen.indexOf(targetState.destination.route.orEmpty())
        val forward = toIndex >= fromIndex
        slideOutHorizontally(tween(MotionDurationMillis, easing = FastOutSlowInEasing)) { fullWidth ->
            if (forward) -fullWidth else fullWidth
        } + fadeOut(tween(MotionDurationMillis, easing = FastOutSlowInEasing))
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        enterTransition = FadeScaleEnter,
        exitTransition = FadeScaleExit
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
        composable(
            Screen.Water.route,
            enterTransition = CategoryEnterTransition,
            exitTransition = CategoryExitTransition
        ) {
            CategoryScreenWrapper(navController, Screen.Water) {
                WaterScreen()
            }
        }
        composable(
            Screen.Food.route,
            enterTransition = CategoryEnterTransition,
            exitTransition = CategoryExitTransition
        ) {
            CategoryScreenWrapper(navController, Screen.Food) {
                FoodScreen()
            }
        }
        composable(
            Screen.Sleep.route,
            enterTransition = CategoryEnterTransition,
            exitTransition = CategoryExitTransition
        ) {
            CategoryScreenWrapper(navController, Screen.Sleep) {
                SleepScreen()
            }
        }
        composable(
            Screen.Emotions.route,
            enterTransition = CategoryEnterTransition,
            exitTransition = CategoryExitTransition
        ) {
            CategoryScreenWrapper(navController, Screen.Emotions) {
                EmotionsScreen()
            }
        }

        // Phase 2 screens
        composable(
            Screen.Bathroom.route,
            enterTransition = CategoryEnterTransition,
            exitTransition = CategoryExitTransition
        ) {
            CategoryScreenWrapper(navController, Screen.Bathroom) { BathroomScreen() }
        }
        composable(
            Screen.Health.route,
            enterTransition = CategoryEnterTransition,
            exitTransition = CategoryExitTransition
        ) {
            CategoryScreenWrapper(navController, Screen.Health) { HealthScreen() }
        }
        composable(
            Screen.Interactions.route,
            enterTransition = CategoryEnterTransition,
            exitTransition = CategoryExitTransition
        ) {
            CategoryScreenWrapper(navController, Screen.Interactions) { InteractionsScreen() }
        }
        composable(
            Screen.Chores.route,
            enterTransition = CategoryEnterTransition,
            exitTransition = CategoryExitTransition
        ) {
            CategoryScreenWrapper(navController, Screen.Chores) { ChoresScreen() }
        }
        composable(
            Screen.Hobbies.route,
            enterTransition = CategoryEnterTransition,
            exitTransition = CategoryExitTransition
        ) {
            CategoryScreenWrapper(navController, Screen.Hobbies) { HobbiesScreen() }
        }
        composable(
            Screen.Ideas.route,
            enterTransition = CategoryEnterTransition,
            exitTransition = CategoryExitTransition
        ) {
            CategoryScreenWrapper(navController, Screen.Ideas) { IdeasScreen() }
        }
        composable(
            Screen.Cycle.route,
            enterTransition = CategoryEnterTransition,
            exitTransition = CategoryExitTransition
        ) {
            CategoryScreenWrapper(navController, Screen.Cycle) { CycleScreen() }
        }
        composable(
            Screen.BadHabits.route,
            enterTransition = CategoryEnterTransition,
            exitTransition = CategoryExitTransition
        ) {
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

