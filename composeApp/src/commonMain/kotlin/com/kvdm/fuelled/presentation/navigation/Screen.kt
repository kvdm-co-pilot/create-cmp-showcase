package com.kvdm.fuelled.presentation.navigation

import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.presentation.meal.MealTrayInitialTarget
import kotlinx.datetime.LocalDate

// Type-safe route registry. Add a Screen object + a Routes constant per destination.
sealed class Screen(val route: String) {
    data object Shell : Screen(Routes.SHELL)
    data object FoodDetail : Screen(Routes.FOOD_DETAIL)
    data object MealTray : Screen(Routes.MEAL_TRAY)
    data object MealPlan : Screen(Routes.MEAL_PLAN)
    data object MealTimes : Screen(Routes.MEAL_TIMES)
    // cmp:anchor screen-objects
}

object Routes {
    const val SHELL       = "shell"
    const val FOOD_DETAIL = "food/{foodId}"
    const val MEAL_TRAY   = "meal/{date}/{slot}"
    const val MEAL_PLAN   = "plan/{date}"
    const val MEAL_TIMES  = "plan/times"
    // cmp:anchor route-consts
    fun foodDetail(foodId: String) = "food/$foodId"

    /**
     * The structured day (PLAN-11). The date rides the route so a link into a specific day —
     * Today's "This week", or a reminder tap later — arrives already showing it, and so the back
     * stack remembers which day you were on.
     *
     * [MEAL_TIMES] is registered as its own literal route and is matched first by the nav graph;
     * "times" is not an ISO date either, so [mealPlanDate] would reject it regardless.
     */
    fun mealPlan(date: LocalDate) = "plan/$date"

    /** The plan route's date, or `null` when it is absent or not an ISO date. */
    fun mealPlanDate(date: String?): LocalDate? =
        date?.let { LocalDate.Formats.ISO.parseOrNull(it) }

    /**
     * The add-to-meal tray, aimed from the tap (TODAY-07/TODAY-08). The target travels in the
     * route itself — ISO logical date and the [MealSlot]'s enum NAME, never its display label —
     * so "add to Dinner" arrives at the tray already targeted and needs no retargeting (MEAL-10).
     */
    fun mealTray(date: LocalDate, slot: MealSlot) = "meal/$date/${slot.name}"

    /**
     * The inverse: the tray's target read back off a back-stack entry's arguments, or `null`
     * when either half is absent or malformed. `null` is not a crash and not a guess — MEAL-10
     * left the tray nothing to fall back to, so the destination pops back rather than opening a
     * tray aimed at a meal the user never picked.
     */
    fun mealTrayTarget(date: String?, slot: String?): MealTrayInitialTarget? {
        val parsedDate = date?.let { LocalDate.Formats.ISO.parseOrNull(it) } ?: return null
        val parsedSlot = MealSlot.entries.firstOrNull { it.name == slot } ?: return null
        return MealTrayInitialTarget(date = parsedDate, slot = parsedSlot)
    }
}
