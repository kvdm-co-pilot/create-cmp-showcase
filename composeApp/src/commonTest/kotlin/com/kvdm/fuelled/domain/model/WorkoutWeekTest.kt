package com.kvdm.fuelled.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The training week as a model (WORK-01/WORK-02/WORK-05) — the grid, and the four states a
 * day reads as.
 */
class WorkoutWeekTest {

    private val monday = LocalDate(2026, 8, 3)
    private val tuesday = LocalDate(2026, 8, 4)
    private val sunday = LocalDate(2026, 8, 9)

    // SPEC: WORK-02
    @Test
    fun `the week is always seven days - a day nobody stored reads as rest`() {
        val sparse = WorkoutWeek(mapOf(DayOfWeek.MONDAY to WorkoutDayPlan("Upper body")))

        assertTrue(sparse[DayOfWeek.MONDAY].isTraining)
        // A day absent from storage and a day deliberately left blank are the same thing, so
        // the grid comes from the enum and both answer rest — there is no third state.
        assertFalse(sparse[DayOfWeek.SATURDAY].isTraining)
        assertEquals(1, sparse.trainingDays)
    }

    // SPEC: WORK-02
    @Test
    fun `a null label IS the rest day - no separate flag to keep honest`() {
        assertFalse(WorkoutDayPlan(label = null).isTraining)
        assertTrue(WorkoutDayPlan(label = "Cardio").isTraining)
    }

    // SPEC: WORK-08
    @Test
    fun `the seeded split is the classic programme, with no times set`() {
        val seeded = WorkoutWeek.DEFAULT

        assertEquals(6, seeded.trainingDays, "six sessions, Sunday free")
        assertFalse(seeded[DayOfWeek.SUNDAY].isTraining)
        assertTrue(
            DayOfWeek.entries.all { seeded[it].remindAt == null },
            "a seeded week is a real starting point; a seeded ALARM is one nobody asked for",
        )
    }

    // SPEC: WORK-05
    @Test
    fun `the four day states separate a rest day from today from a genuine miss`() {
        val plan = WorkoutDayPlan("Upper body")
        val rest = WorkoutDayPlan()

        // A rest day asked nothing of anyone — never a miss, whatever the date.
        assertEquals(WorkoutDayState.REST, WorkoutDay(monday, rest, done = false).state(tuesday))
        assertEquals(WorkoutDayState.DONE, WorkoutDay(monday, plan, done = true).state(tuesday))
        // Only a PAST training day with no mark is a miss...
        assertEquals(WorkoutDayState.MISSED, WorkoutDay(monday, plan, done = false).state(tuesday))
        // ...and today is never one: a day still in progress has not been failed.
        assertEquals(WorkoutDayState.PENDING, WorkoutDay(tuesday, plan, done = false).state(tuesday))
        assertEquals(WorkoutDayState.PENDING, WorkoutDay(sunday, plan, done = false).state(tuesday))
    }

    // SPEC: WORK-03
    @Test
    fun `a day's plan is read by DATE, so the card follows the weekday`() {
        val week = WorkoutWeek(
            mapOf(
                DayOfWeek.MONDAY to WorkoutDayPlan("Upper body"),
                DayOfWeek.TUESDAY to WorkoutDayPlan("Cardio"),
            ),
        )

        assertEquals("Upper body", week.on(monday).label)
        assertEquals("Cardio", week.on(tuesday).label)
        assertEquals(null, week.on(sunday).label, "and an unset weekday is rest, derived not stored")
    }
}
