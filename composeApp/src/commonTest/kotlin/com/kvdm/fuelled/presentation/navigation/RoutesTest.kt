package com.kvdm.fuelled.presentation.navigation

import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.presentation.meal.MealTrayInitialTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

/**
 * The tray's route encoding — the wire the tap's target travels on (TODAY-07/TODAY-08).
 *
 * These are the two halves of one contract: what a tap writes into the route, and what the
 * destination reads back out of it. They are tested together because the failure that matters
 * is them disagreeing — a screen test would still pass while the tray opened on a default.
 */
class RoutesTest {

    private val date = LocalDate(2026, 7, 22)

    // SPEC: TODAY-07
    @Test
    fun `the tray route carries the ISO logical date and the slot's enum name`() {
        assertEquals("meal/2026-07-22/DINNER", Routes.mealTray(date, MealSlot.DINNER))
    }

    // SPEC: TODAY-07
    @Test
    fun `a route the tap built reads back as the same target`() {
        val parsed = Routes.mealTrayTarget(date = "2026-07-22", slot = "DINNER")

        assertEquals(MealTrayInitialTarget(date = date, slot = MealSlot.DINNER), parsed)
    }

    // SPEC: TODAY-07
    @Test
    fun `a malformed or absent argument yields no target instead of throwing`() {
        // No target is the honest answer: the tray then opens on its own clock-derived default
        // (MEAL-04) rather than on a guess — and, crucially, rather than crashing the graph.
        assertNull(Routes.mealTrayTarget(date = "not-a-date", slot = "DINNER"))
        assertNull(Routes.mealTrayTarget(date = "2026-07-22", slot = "BRUNCH"), "not a MealSlot")
        assertNull(Routes.mealTrayTarget(date = "2026-07-22", slot = "dinner"), "the enum NAME, exactly")
        assertNull(Routes.mealTrayTarget(date = null, slot = null))
    }
}
