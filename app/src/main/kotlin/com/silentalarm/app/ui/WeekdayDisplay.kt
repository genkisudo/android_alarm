package com.silentalarm.app.ui

import com.silentalarm.scheduling.Weekday

private val ORDERED_DAYS = listOf(
    "Mon" to Weekday.MONDAY,
    "Tue" to Weekday.TUESDAY,
    "Wed" to Weekday.WEDNESDAY,
    "Thu" to Weekday.THURSDAY,
    "Fri" to Weekday.FRIDAY,
    "Sat" to Weekday.SATURDAY,
    "Sun" to Weekday.SUNDAY,
)

/** SPEC.md #4.1's weekday summary line: named special cases, else the day abbreviations. */
fun weekdaySummary(daysMask: Int): String = when (daysMask) {
    Weekday.EVERY_DAY -> "Every day"
    Weekday.WEEKDAYS -> "Weekdays"
    Weekday.WEEKENDS -> "Weekends"
    else -> ORDERED_DAYS.filter { (_, bit) -> daysMask and bit != 0 }.joinToString(" ") { it.first }
}
