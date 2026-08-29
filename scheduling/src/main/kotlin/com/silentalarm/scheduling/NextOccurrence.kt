package com.silentalarm.scheduling

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * The single source of truth for "when does this alarm next fire" (SPEC.md #6).
 *
 * Searches an eight-day window (today plus the next seven days) so that a DST shift moving a
 * candidate instant across [now] still yields a hit for any non-zero [daysMask]. Strictly
 * greater-than comparison means an alarm whose time is exactly [now] rolls to next week rather
 * than firing twice.
 *
 * @param daysMask bitmask per [Weekday]; must be non-zero (validated at save time, see
 *   Assumption A11 in SPEC.md) — a zero mask returns null here rather than looping forever.
 */
fun nextOccurrence(
    hour: Int,
    minute: Int,
    daysMask: Int,
    now: ZonedDateTime,
): ZonedDateTime? {
    if (daysMask == 0) return null

    val zone = now.zone
    for (offset in 0..7L) {
        val date = now.toLocalDate().plusDays(offset)
        if (!Weekday.contains(daysMask, date.dayOfWeek)) continue

        val candidate = LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone)
        if (candidate.isAfter(now)) return candidate
    }
    // Unreachable when daysMask != 0: some day in an 8-day window always matches.
    return null
}
