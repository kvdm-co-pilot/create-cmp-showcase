package com.kvdm.fuelled.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * START-02 — the install's first day. The derivation is pure, so the whole framing question
 * ("did you skip breakfast, or did this app simply not exist yet?") is testable at a chosen
 * minute with no clock, no database and no device.
 */
class FirstDayTest {

    private val date = LocalDate(2026, 7, 22)

    private fun day(now: LocalTime, startedAt: LocalTime?, done: Set<MealSlot> = emptySet()) =
        buildPlanDay(
            date = date,
            isCurrentDay = true,
            now = now,
            times = MealTimes(),
            entriesBySlot = emptyMap(),
            doneSlots = done,
            waterTicks = emptySet(),
            startedAt = startedAt,
        )

    // SPEC: START-02
    @Test
    fun `slots before the first open read before-you-started, never missed`() {
        // Installed at 13:00. Breakfast (07:00) and the morning snack (09:30) happened before
        // this app existed; lunch (12:00) also predates it.
        val plan = day(now = LocalTime(13, 0), startedAt = LocalTime(13, 0))

        val breakfast = plan.slots.single { it.slot == MealSlot.BREAKFAST }
        assertTrue(breakfast.beforeStart, "the app was not installed at 07:00")
        assertFalse(breakfast.missed, "so it cannot claim you missed it")
        assertFalse(breakfast.focused)

        val afternoon = plan.slots.single { it.slot == MealSlot.AFTERNOON_SNACK }
        assertFalse(afternoon.beforeStart, "14:30 is still ahead — an ordinary slot")
    }

    // SPEC: START-02
    @Test
    fun `focus lands on the first slot the app can actually help with`() {
        val plan = day(now = LocalTime(13, 0), startedAt = LocalTime(13, 0))

        assertEquals(
            MealSlot.AFTERNOON_SNACK,
            plan.focusedSlot?.slot,
            "a mid-day first open aims at the next meal you can still eat",
        )
    }

    // SPEC: START-02
    @Test
    fun `a pre-start slot stays back-fillable - ticking it is an ordinary completion`() {
        // The user logs the breakfast they DID eat before installing. It is done, and
        // done-ness outranks the framing: nothing calls it before-start any more.
        val plan = day(now = LocalTime(13, 0), startedAt = LocalTime(13, 0), done = setOf(MealSlot.BREAKFAST))

        val breakfast = plan.slots.single { it.slot == MealSlot.BREAKFAST }
        assertTrue(breakfast.done)
        assertFalse(breakfast.beforeStart, "a slot you filled in is yours, not the app's blind spot")
    }

    // SPEC: START-02
    @Test
    fun `every later day is judged normally - this is not a permanent amnesty`() {
        // startedAt is null on any day but the install's first (the repository passes it only
        // for that date), so missed-ness returns exactly as PLAN-19 specifies.
        val plan = day(now = LocalTime(13, 0), startedAt = null)

        val breakfast = plan.slots.single { it.slot == MealSlot.BREAKFAST }
        assertFalse(breakfast.beforeStart)
        assertTrue(breakfast.missed, "on an ordinary day, 07:00 unticked at 13:00 is missed")
    }
}
