package com.kvdm.fuelled.core.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/**
 * The logical-day boundary — the day a log entry belongs to, which is not the calendar date.
 * Pure function in, date out: no clock, no repository, no stored boundary anywhere in here,
 * which is itself the point of MEAL-02.
 */
class LogicalDayTest {

    private val zone = TimeZone.UTC

    private fun instantAt(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        LocalDateTime(year, month, day, hour, minute).toInstant(zone)

    // SPEC: MEAL-01
    @Test
    fun `an instant before the day start belongs to the previous logical date`() {
        val lateSnack = instantAt(2026, 7, 26, 3, 59)

        val date = logicalDate(lateSnack, dayStartHour = 4, zone = zone)

        assertEquals(LocalDate(2026, 7, 25), date)
    }

    // SPEC: MEAL-01
    @Test
    fun `an instant at the day start begins the new logical date`() {
        val breakfast = instantAt(2026, 7, 26, 4, 0)

        val date = logicalDate(breakfast, dayStartHour = 4, zone = zone)

        assertEquals(LocalDate(2026, 7, 26), date)
    }

    // SPEC: MEAL-01
    @Test
    fun `an instant in the middle of the day maps to its own calendar date`() {
        val lunch = instantAt(2026, 7, 26, 12, 30)

        val date = logicalDate(lunch, dayStartHour = 4, zone = zone)

        assertEquals(LocalDate(2026, 7, 26), date)
    }

    // SPEC: MEAL-01
    @Test
    fun `a dayStartHour of 0 makes the logical date the calendar date across midnight`() {
        // Midnight is the special case of the setting, not a separate rule.
        val beforeMidnight = instantAt(2026, 7, 25, 23, 59)
        val afterMidnight = instantAt(2026, 7, 26, 0, 1)

        assertEquals(LocalDate(2026, 7, 25), logicalDate(beforeMidnight, dayStartHour = 0, zone = zone))
        assertEquals(LocalDate(2026, 7, 26), logicalDate(afterMidnight, dayStartHour = 0, zone = zone))
    }

    // SPEC: MEAL-02
    @Test
    fun `the day rolls over by re-deriving from the current instant - no stored state, no scheduled job`() {
        // The SAME stored inputs (the profile's setting and the zone) on both sides of the
        // boundary: nothing is written between the two calls, and nothing needs to be.
        val dayStartHour = 4
        val beforeBoundary = instantAt(2026, 7, 26, 3, 59)
        val afterBoundary = instantAt(2026, 7, 26, 4, 0)

        val dayInView = logicalDate(beforeBoundary, dayStartHour, zone)
        val dayInViewLater = logicalDate(afterBoundary, dayStartHour, zone)

        assertEquals(LocalDate(2026, 7, 25), dayInView)
        assertEquals(dayInView.plus(1, DateTimeUnit.DAY), dayInViewLater)
        // Re-deriving the earlier instant still yields the earlier day — the rollover mutated
        // nothing, so a re-read of an old instant is unchanged by it.
        assertEquals(dayInView, logicalDate(beforeBoundary, dayStartHour, zone))
    }

    @Test
    fun `a dayStartHour outside 0 to 23 is rejected as a programming error`() {
        val instant = instantAt(2026, 7, 26, 12, 0)

        assertFailsWith<IllegalArgumentException> { logicalDate(instant, dayStartHour = 24, zone = zone) }
        assertFailsWith<IllegalArgumentException> { logicalDate(instant, dayStartHour = -1, zone = zone) }
    }
}
