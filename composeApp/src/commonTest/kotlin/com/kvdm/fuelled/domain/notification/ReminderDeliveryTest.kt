package com.kvdm.fuelled.domain.notification

import com.kvdm.fuelled.domain.model.DEFAULT_MEAL_TIMES
import com.kvdm.fuelled.domain.model.MealSlot
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The delivery-time relevance check (PLAN-26). Pure values in, a boolean out — no alarm
 * manager, no clock read, so every case here is the one the device actually hit.
 */
class ReminderDeliveryTest {

    /** Thursday's breakfast alarm, armed for 07:00. */
    private val armedFor = Instant.parse("2026-07-30T07:00:00Z")

    // SPEC: PLAN-26
    @Test
    fun `a reminder delivered near its time is posted - Doze's own window is not staleness`() {
        assertFalse(isStaleDelivery(armedFor, armedFor), "on the dot")
        assertFalse(
            isStaleDelivery(armedFor, armedFor + 15.minutes),
            "the inexact window the scheduler itself asks for is ordinary, not stale",
        )
        assertFalse(
            isStaleDelivery(armedFor, armedFor + 2.hours),
            "the boundary is inclusive — exactly two hours late still posts",
        )
    }

    // SPEC: PLAN-26
    @Test
    fun `the avalanche is dropped - yesterday evening does not ring on top of this morning`() {
        // The observed case: the clock crossed un-fired slots and the OS handed back everything
        // it owed at once. Wednesday's dinner (17:00) and evening snack (19:30) arrived stacked
        // on Thursday's 06:55 breakfast — 13 and 11 hours late respectively.
        val thursdayMorning = Instant.parse("2026-07-30T06:55:00Z")
        val wednesday = "2026-07-29T"
        listOf(MealSlot.DINNER, MealSlot.EVENING_SNACK).forEach { slot ->
            val time = DEFAULT_MEAL_TIMES.getValue(slot)
            val armed = Instant.parse("$wednesday${time.hour.toString().padStart(2, '0')}:00:00Z")
            assertTrue(
                isStaleDelivery(armed, thursdayMorning),
                "$slot armed for yesterday must not announce itself this morning",
            )
        }
    }

    // SPEC: PLAN-26
    @Test
    fun `a clock that moved BACKWARDS still posts - early is not a reason to swallow a reminder`() {
        // Negative lateness is not "extremely fresh"; it is a device whose clock jumped back.
        // Dropping the reminder there would silently cost the user the meal they were waiting
        // for, which is a worse failure than one redundant notification.
        assertFalse(isStaleDelivery(armedFor, armedFor - 3.hours))
    }

    // SPEC: PLAN-26
    @Test
    fun `the window is the meal rhythm - past it, the NEXT meal's reminder is the relevant one`() {
        // Two hours is not a magic number: it is shorter than the gap the method puts between
        // meals, so a dropped reminder is always already superseded by a live one.
        val gaps = MealSlot.entries.zipWithNext { a, b ->
            DEFAULT_MEAL_TIMES.getValue(b).toSecondOfDay() - DEFAULT_MEAL_TIMES.getValue(a).toSecondOfDay()
        }
        assertTrue(
            gaps.all { it >= REMINDER_STALE_AFTER.inWholeSeconds },
            "the staleness window must not outlast the gap to the next meal, or a dropped " +
                "reminder would leave a silent hole in the day",
        )
    }
}
