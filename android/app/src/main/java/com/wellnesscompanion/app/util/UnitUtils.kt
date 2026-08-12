package com.wellnesscompanion.app.util

import kotlin.math.roundToInt

/** Convert ml to fluid ounces (1 ml = 0.033814 fl oz) */
fun mlToFlOz(ml: Int): Float = ml * 0.033814f

/** Format ml with imperial equivalent: "450 ml (15 fl oz)" */
fun formatMl(ml: Int): String {
    val oz = mlToFlOz(ml).roundToInt()
    return "$ml ml ($oz fl oz)"
}

/** Format ml for a compact display: "450ml / 15oz" */
fun formatMlCompact(ml: Int): String {
    val oz = mlToFlOz(ml).roundToInt()
    return "${ml}ml / ${oz}oz"
}

/** Format hours for sleep: "7h 20m" */
fun formatSleepHours(totalHours: Float): String {
    val h = totalHours.toInt()
    val m = ((totalHours - h) * 60).toInt()
    return "${h}h ${m}m"
}

/** Format minutes with hours when needed: "45 min" or "2h 15m" */
fun formatMinutes(minutes: Int): String {
    return if (minutes >= 60) {
        val h = minutes / 60
        val m = minutes % 60
        if (m > 0) "${h}h ${m}m" else "${h}h"
    } else {
        "$minutes min"
    }
}
