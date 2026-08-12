package com.wellnesscompanion.app.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val displayDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)

fun todayDateString(): String = LocalDate.now().format(dateFormatter)

fun todayDisplayString(): String = LocalDate.now().format(displayDateFormatter)

fun greetingForHour(hour: Int = LocalTime.now().hour): String = when {
    hour < 12 -> "Good morning"
    hour < 17 -> "Good afternoon"
    else -> "Good evening"
}

fun nowMillis(): Long = System.currentTimeMillis()
