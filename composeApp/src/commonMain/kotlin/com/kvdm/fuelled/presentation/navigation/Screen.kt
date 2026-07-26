package com.kvdm.fuelled.presentation.navigation

import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.presentation.meal.MealTrayInitialTarget
import kotlinx.datetime.LocalDate

// Type-safe route registry. Add a Screen object + a Routes constant per destination.
sealed class Screen(val route: String) {
    data object Shell : Screen(Routes.SHELL)
    data object FoodDetail : Screen(Routes.FOOD_DETAIL)
    data object MealTray : Screen(Routes.MEAL_TRAY)
    // cmp:anchor screen-objects
}

object Routes {
    const val SHELL       = "shell"
    const val FOOD_DETAIL = "food/{foodId}"
    const val MEAL_TRAY   = "meal/{date}/{slot}"
    // cmp:anchor route-consts
    fun foodDetail(foodId: String) = "food/$foodId"

    /**
     * The add-to-meal tray, aimed from the tap (TODAY-07/TODAY-08). The target travels in the
     * route itself — ISO logical date and the [MealSlot]'s enum NAME, never its display label —
     * so "add to Dinner" arrives at the tray already targeted and needs no retargeting (MEAL-10).
     */
    fun mealTray(date: LocalDate, slot: MealSlot) = "meal/$date/${slot.name}"

    /**
     * The inverse: the tray's target read back off a back-stack entry's arguments, or `null`
     * when either half is absent or malformed. `null` is not a crash and not a guess — it hands
     * [com.kvdm.fuelled.presentation.meal.MealTrayViewModel] its own clock-derived opening
     * target (current logical day + `slotForLocalTime`), which is the same default the tray has
     * always had.
     */
    fun mealTrayTarget(date: String?, slot: String?): MealTrayInitialTarget? {
        val parsedDate = date?.let { LocalDate.Formats.ISO.parseOrNull(it) } ?: return null
        val parsedSlot = MealSlot.entries.firstOrNull { it.name == slot } ?: return null
        return MealTrayInitialTarget(date = parsedDate, slot = parsedSlot)
    }
}
