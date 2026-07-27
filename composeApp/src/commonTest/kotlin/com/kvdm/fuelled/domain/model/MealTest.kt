package com.kvdm.fuelled.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The meal-log data model: the closed slot enum a day groups by. The tray's time-of-day
 * preselect (MEAL-04) and its tests were withdrawn with the clause — every way into the tray
 * now opens it already aimed, so there is no slot left to guess. What replaced it — slot times,
 * derived water, focus and lateness — is [MealPlanTest].
 */
class MealTest {

    // SPEC: MEAL-03
    @Test
    fun `the meal slot is a closed six-value enum in day order`() {
        // Declaration order is the order a day's containers render in.
        assertEquals(
            listOf(
                MealSlot.BREAKFAST,
                MealSlot.MORNING_SNACK,
                MealSlot.LUNCH,
                MealSlot.AFTERNOON_SNACK,
                MealSlot.DINNER,
                MealSlot.EVENING_SNACK,
            ),
            MealSlot.entries,
        )
        // A day sorted by slot lands in that same order, whatever order it was logged in.
        val loggedOutOfOrder = listOf(
            MealSlot.EVENING_SNACK,
            MealSlot.LUNCH,
            MealSlot.BREAKFAST,
            MealSlot.DINNER,
            MealSlot.MORNING_SNACK,
            MealSlot.AFTERNOON_SNACK,
        )
        assertEquals(MealSlot.entries.toList(), loggedOutOfOrder.sorted())
    }

    // SPEC: MEAL-03
    @Test
    fun `the three snacks are distinct identities, not one generic snack`() {
        val snacks = listOf(MealSlot.MORNING_SNACK, MealSlot.AFTERNOON_SNACK, MealSlot.EVENING_SNACK)
        assertEquals(3, snacks.toSet().size)
        // Each sits between its neighbouring meals rather than collapsing to the end of the day.
        assertEquals(MealSlot.MORNING_SNACK, MealSlot.entries[MealSlot.BREAKFAST.ordinal + 1])
        assertEquals(MealSlot.AFTERNOON_SNACK, MealSlot.entries[MealSlot.LUNCH.ordinal + 1])
        assertEquals(MealSlot.EVENING_SNACK, MealSlot.entries[MealSlot.DINNER.ordinal + 1])
    }
}
