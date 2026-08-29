package com.silentalarm.scheduling

import java.time.DayOfWeek

/**
 * Bit layout for an alarm's day mask: bit 0 = Monday ... bit 6 = Sunday (SPEC.md #9).
 * Valid range is 1..127; 0 (no days selected) is rejected at save time, not here.
 */
object Weekday {
    const val MONDAY = 1 shl 0
    const val TUESDAY = 1 shl 1
    const val WEDNESDAY = 1 shl 2
    const val THURSDAY = 1 shl 3
    const val FRIDAY = 1 shl 4
    const val SATURDAY = 1 shl 5
    const val SUNDAY = 1 shl 6

    const val EVERY_DAY = MONDAY or TUESDAY or WEDNESDAY or THURSDAY or FRIDAY or SATURDAY or SUNDAY
    const val WEEKDAYS = MONDAY or TUESDAY or WEDNESDAY or THURSDAY or FRIDAY
    const val WEEKENDS = SATURDAY or SUNDAY

    fun bitFor(dayOfWeek: DayOfWeek): Int = 1 shl (dayOfWeek.value - 1)

    fun contains(daysMask: Int, dayOfWeek: DayOfWeek): Boolean =
        (daysMask and bitFor(dayOfWeek)) != 0
}
