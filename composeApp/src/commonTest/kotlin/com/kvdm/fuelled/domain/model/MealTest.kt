package com.kvdm.fuelled.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalTime

/**
 * The meal-log data model: the closed slot enum a day groups by, and the time windows the
 * add-to-meal tray preselects from. Pure values in, slot out — no clock is read here, so the
 * caller decides what "now" means and these assertions stay deterministic.
 */
class MealTest {

    private fun at(hour: Int, minute: Int, second: Int = 0) = LocalTime(hour, minute, second)

    // SPEC: MEAL-03
    @Test
    fun `the meal slot is a closed enum ordered breakfast, lunch, dinner, snack`() {
        // Declaration order is the order a day's entries group and render in.
        assertEquals(
            listOf(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.DINNER, MealSlot.SNACK),
            MealSlot.entries,
        )
        // A day sorted by slot lands in that same order, whatever order it was logged in.
        val loggedOutOfOrder = listOf(MealSlot.SNACK, MealSlot.DINNER, MealSlot.BREAKFAST, MealSlot.LUNCH)
        assertEquals(MealSlot.entries.toList(), loggedOutOfOrder.sorted())
    }

    // SPEC: MEAL-04
    @Test
    fun `the tray preselects breakfast from 04-00 until 10-30`() {
        assertEquals(MealSlot.BREAKFAST, slotForLocalTime(at(4, 0)))
        assertEquals(MealSlot.BREAKFAST, slotForLocalTime(at(7, 15)))
        assertEquals(MealSlot.BREAKFAST, slotForLocalTime(at(10, 29, 59)))
    }

    // SPEC: MEAL-04
    @Test
    fun `the tray preselects lunch from 10-30 until 15-00`() {
        assertEquals(MealSlot.LUNCH, slotForLocalTime(at(10, 30))) // the boundary belongs to lunch
        assertEquals(MealSlot.LUNCH, slotForLocalTime(at(13, 0)))
        assertEquals(MealSlot.LUNCH, slotForLocalTime(at(14, 59, 59)))
    }

    // SPEC: MEAL-04
    @Test
    fun `the tray preselects dinner from 15-00 until 21-00`() {
        assertEquals(MealSlot.DINNER, slotForLocalTime(at(15, 0)))
        assertEquals(MealSlot.DINNER, slotForLocalTime(at(18, 45)))
        assertEquals(MealSlot.DINNER, slotForLocalTime(at(20, 59, 59)))
    }

    // SPEC: MEAL-04
    @Test
    fun `the tray preselects snack outside the meal windows - late evening and the small hours`() {
        // Both sides of the day: from 21:00 sharp through to 03:59, snack is the arm.
        assertEquals(MealSlot.SNACK, slotForLocalTime(at(21, 0)))
        assertEquals(MealSlot.SNACK, slotForLocalTime(at(23, 59, 59)))
        assertEquals(MealSlot.SNACK, slotForLocalTime(at(0, 0)))
        assertEquals(MealSlot.SNACK, slotForLocalTime(at(3, 59)))
    }
}
