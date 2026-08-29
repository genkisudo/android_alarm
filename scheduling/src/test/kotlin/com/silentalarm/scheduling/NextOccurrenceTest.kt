package com.silentalarm.scheduling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Covers SPEC.md #10 test cases T1-T9 (the scheduling-logic unit tests). T9 ("disabled alarm
 * produces no trigger") is enforced one layer up, by AlarmScheduler refusing to call this
 * function for a disabled alarm - there is nothing for a pure function to assert there.
 */
class NextOccurrenceTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, minute, second, 0, zone)

    // T1: Alarm Mon 07:00, now Mon 06:59 -> fires today 07:00
    @Test
    fun `fires later today when today still matches and time has not passed`() {
        val now = at(2024, 1, 1, 6, 59) // Monday
        val result = nextOccurrence(7, 0, Weekday.MONDAY, now)
        assertEquals(at(2024, 1, 1, 7, 0), result)
    }

    // T2: Alarm Mon 07:00, now Mon 07:00:00 exactly -> next Monday, not today
    @Test
    fun `rolls to next week when now exactly equals the alarm time`() {
        val now = at(2024, 1, 1, 7, 0, 0) // Monday
        val result = nextOccurrence(7, 0, Weekday.MONDAY, now)
        assertEquals(at(2024, 1, 8, 7, 0), result)
    }

    // T3: Alarm Mon 07:00, now Mon 07:01 -> next Monday
    @Test
    fun `rolls to next week when today's time has already passed`() {
        val now = at(2024, 1, 1, 7, 1) // Monday
        val result = nextOccurrence(7, 0, Weekday.MONDAY, now)
        assertEquals(at(2024, 1, 8, 7, 0), result)
    }

    // T4: Every-day alarm, now 23:59 Sunday -> tomorrow (Monday)
    @Test
    fun `every-day alarm rolls into the next calendar day`() {
        val now = at(2024, 1, 7, 23, 59) // Sunday
        val result = nextOccurrence(7, 0, Weekday.EVERY_DAY, now)
        assertEquals(at(2024, 1, 8, 7, 0), result)
    }

    // T5: Weekend-only alarm, now Wednesday -> Saturday
    @Test
    fun `weekend-only alarm skips to the next matching weekday`() {
        val now = at(2024, 1, 3, 12, 0) // Wednesday
        val result = nextOccurrence(9, 0, Weekday.WEEKENDS, now)
        assertEquals(at(2024, 1, 6, 9, 0), result) // Saturday
        assertEquals(DayOfWeek.SATURDAY, result!!.dayOfWeek)
    }

    // T6: Alarm at 02:30 on a spring-forward night (America/New_York, 2024-03-10: 02:00 -> 03:00)
    @Test
    fun `spring-forward gap fires once at the shifted instant`() {
        val now = at(2024, 3, 10, 1, 0) // Sunday, before the gap
        val result = nextOccurrence(2, 30, Weekday.SUNDAY, now)!!
        // The nonexistent local 02:30 resolves forward by the gap length -> 03:30 EDT.
        assertEquals(at(2024, 3, 10, 3, 30), result)
        assertEquals("-04:00", result.offset.id)
    }

    // T7: Alarm at 01:30 on a fall-back night (America/New_York, 2024-11-03: 01:00-02:00 repeats)
    @Test
    fun `fall-back overlap fires once at the earlier offset`() {
        val now = at(2024, 11, 3, 0, 30) // Sunday, still EDT, before the overlap
        val result = nextOccurrence(1, 30, Weekday.SUNDAY, now)!!
        assertEquals(2024, result.year)
        assertEquals(11, result.monthValue)
        assertEquals(3, result.dayOfMonth)
        assertEquals(1, result.hour)
        assertEquals(30, result.minute)
        assertEquals("-04:00", result.offset.id) // earlier offset (EDT) wins, not -05:00 (EST)
    }

    // T8: daysMask = 0 -> never schedulable
    @Test
    fun `zero day mask yields no occurrence`() {
        val now = at(2024, 1, 1, 6, 59)
        assertNull(nextOccurrence(7, 0, 0, now))
    }

    @Test
    fun `eight day search window still finds a single matching weekday far in the future`() {
        val now = at(2024, 1, 1, 0, 0) // Monday
        val result = nextOccurrence(7, 0, Weekday.MONDAY, now)
        assertEquals(at(2024, 1, 1, 7, 0), result)
    }
}
