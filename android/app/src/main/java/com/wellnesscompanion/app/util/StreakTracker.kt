package com.wellnesscompanion.app.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object StreakTracker {

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * Calculate the current streak (consecutive days with at least one entry)
     * ending today or yesterday.
     * [loggedDates] must be a list of "yyyy-MM-dd" strings.
     */
    fun currentStreak(loggedDates: List<String>): Int {
        if (loggedDates.isEmpty()) return 0

        val dates = loggedDates.mapNotNull { runCatching { LocalDate.parse(it, fmt) }.getOrNull() }
            .toSortedSet()

        val today = LocalDate.now()
        // Start from today if logged, otherwise yesterday
        var current = if (dates.contains(today)) today else today.minusDays(1)

        if (!dates.contains(current)) return 0

        var streak = 0
        while (dates.contains(current)) {
            streak++
            current = current.minusDays(1)
        }
        return streak
    }

    /** Get the start and end dates (yyyy-MM-dd) of the last 7 days including today */
    fun last7DaysRange(): Pair<String, String> {
        val today = LocalDate.now()
        val start = today.minusDays(6)
        return start.format(fmt) to today.format(fmt)
    }

    /** Get labels for the last 7 days (e.g., "Mon", "Tue"...) */
    fun last7DayLabels(): List<String> {
        val today = LocalDate.now()
        return (6 downTo 0).map { daysAgo ->
            today.minusDays(daysAgo.toLong()).dayOfWeek.name.take(3)
                .lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    /** Get date strings for the last 7 days */
    fun last7DateStrings(): List<String> {
        val today = LocalDate.now()
        return (6 downTo 0).map { daysAgo ->
            today.minusDays(daysAgo.toLong()).format(fmt)
        }
    }
}
