package com.kvdm.fuelled.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The schedule policy (SUPP-08) — pure, so every case that only shows up on a Tuesday in
 * three weeks is a unit test rather than a thing discovered on a device.
 *
 * Dates are fixed and named: 2026-08-03 is a Monday, so the whole file reads as a real week.
 */
class SupplementScheduleTest {

    private val monday = LocalDate(2026, 8, 3)
    private val tuesday = LocalDate(2026, 8, 4)
    private val wednesday = LocalDate(2026, 8, 5)
    private val thursday = LocalDate(2026, 8, 6)
    private val nextMonday = LocalDate(2026, 8, 10)

    // SPEC: SUPP-08
    @Test
    fun `daily is due every day`() {
        listOf(monday, tuesday, wednesday, thursday).forEach {
            assertTrue(SupplementSchedule.Daily.isDueOn(it), "daily is due on $it")
        }
    }

    // SPEC: SUPP-08
    @Test
    fun `fixed weekdays are due on exactly those days`() {
        val monThu = SupplementSchedule.OnDays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))

        assertTrue(monThu.isDueOn(monday))
        assertTrue(monThu.isDueOn(thursday))
        assertFalse(monThu.isDueOn(tuesday), "Tuesday is not a dose day — and must never read as a missed one")
        assertFalse(monThu.isDueOn(wednesday))
        assertTrue(monThu.isDueOn(nextMonday), "and it repeats next week without anything being stored")
    }

    // SPEC: SUPP-08
    @Test
    fun `every N days counts from the anchor, forwards and backwards`() {
        val everyTwo = SupplementSchedule.EveryNDays(2, anchor = tuesday)

        assertTrue(everyTwo.isDueOn(tuesday), "the anchor itself is due")
        assertFalse(everyTwo.isDueOn(wednesday))
        assertTrue(everyTwo.isDueOn(thursday))
        // Dates BEFORE the anchor give a negative difference, and Kotlin's % keeps the sign —
        // so a naive modulo reads every past day as not-due at a cadence off by one, which the
        // history screen would show as a month of blanks.
        assertTrue(everyTwo.isDueOn(LocalDate(2026, 8, 2)), "two days before the anchor is due")
        assertFalse(everyTwo.isDueOn(monday), "one day before is not")
    }

    // SPEC: SUPP-08
    @Test
    fun `a missed dose does not move the cadence - the anchor is a constant, not a cursor`() {
        val everyTwo = SupplementSchedule.EveryNDays(2, anchor = tuesday)
        // Tuesday's dose is skipped entirely. Nothing about the schedule changes: Thursday is
        // still the next due day, because due-ness is derived from the definition and the
        // definition did not move.
        assertEquals(thursday, everyTwo.nextDueOnOrAfter(wednesday))
    }

    // SPEC: SUPP-09
    @Test
    fun `the next due date is the one the resting row shows`() {
        val monThu = SupplementSchedule.OnDays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))

        assertEquals(thursday, monThu.nextDueOnOrAfter(tuesday), "from Tuesday, the next is Thursday")
        assertEquals(monday, monThu.nextDueOnOrAfter(monday), "on-or-after includes the day itself")
        assertEquals(nextMonday, monThu.nextDueOnOrAfter(LocalDate(2026, 8, 7)), "and wraps into next week")
    }

    // SPEC: SUPP-08
    @Test
    fun `a weekday set with nothing in it is never due and has no next date`() {
        val none = SupplementSchedule.OnDays(emptySet())

        assertFalse(none.isDueOn(monday))
        // The editor can hold this state mid-edit. The row says "No days set" rather than
        // inventing a date, and the lookahead terminates instead of spinning.
        assertNull(none.nextDueOnOrAfter(monday))
    }

    // SPEC: SUPP-11
    @Test
    fun `a cadence outside the offered range is clamped rather than dividing by zero`() {
        // Only reachable from a hand-edited database or a future build. Reading it as a
        // clamped cadence is louder — and survivable — where a modulo by zero crashes the tab.
        val corrupt = SupplementSchedule.EveryNDays(0, anchor = tuesday)
        assertTrue(corrupt.isDueOn(tuesday))
        assertFalse(corrupt.isDueOn(wednesday), "clamped to the minimum cadence of 2")
    }

    // SPEC: SUPP-09
    @Test
    fun `the schedule states itself in the words both surfaces use`() {
        assertEquals("Daily", SupplementSchedule.Daily.label)
        assertEquals(
            "Mon & Thu",
            SupplementSchedule.OnDays(setOf(DayOfWeek.THURSDAY, DayOfWeek.MONDAY)).label,
            "in weekday order, whatever order the set was built in",
        )
        assertEquals("Every 2 days", SupplementSchedule.EveryNDays(2, tuesday).label)
        assertEquals("No days set", SupplementSchedule.OnDays(emptySet()).label)
        assertEquals(
            "Daily",
            SupplementSchedule.OnDays(DayOfWeek.entries.toSet()).label,
            "all seven days IS daily — saying so beats listing them",
        )
    }
}
