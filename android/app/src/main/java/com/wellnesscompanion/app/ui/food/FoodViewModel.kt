package com.wellnesscompanion.app.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.wellnesscompanion.app.data.model.FoodData
import com.wellnesscompanion.app.data.repository.EntryRepository
import com.wellnesscompanion.app.util.fromJsonSafe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MealSlot(
    val type: String,
    val icon: String,
    val label: String,
    val description: String? = null
)

@HiltViewModel
class FoodViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val gson: Gson
) : ViewModel() {

    val meals = repository.getTodayEntries("food").map { entries ->
        val logged = entries.mapNotNull { e ->
            gson.fromJsonSafe<FoodData>(e.data)
        }.associateBy { it.mealType }

        listOf(
            MealSlot("breakfast", "☀\uFE0F", "Breakfast", logged["breakfast"]?.description),
            MealSlot("lunch", "\uD83C\uDF24\uFE0F", "Lunch", logged["lunch"]?.description),
            MealSlot("dinner", "\uD83C\uDF19", "Dinner", logged["dinner"]?.description),
            MealSlot("snacks", "\uD83C\uDF6A", "Snacks", logged["snacks"]?.description)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(
        MealSlot("breakfast", "☀\uFE0F", "Breakfast"),
        MealSlot("lunch", "\uD83C\uDF24\uFE0F", "Lunch"),
        MealSlot("dinner", "\uD83C\uDF19", "Dinner"),
        MealSlot("snacks", "\uD83C\uDF6A", "Snacks")
    ))

    val mealsLoggedCount = meals.map { slots ->
        slots.count { it.description != null }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun logMeal(mealType: String, description: String) {
        viewModelScope.launch {
            repository.addEntry("food", FoodData(mealType, description))
        }
    }
}
