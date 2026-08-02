package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.ComposedMeal
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.NewLogEntry
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.datetime.LocalDate

/**
 * BFL-06: write one composed meal into one slot across several days.
 *
 * The reason the builder exists. Body-for-LIFE is six meals a day for seven days — 42 meals —
 * and copy-forward (PLAN-20) only repeats a day you have already built by hand. This writes
 * the same meal to the same slot on every day you pick.
 *
 * Composed from [AddLogEntriesUseCase] rather than reaching for the repository: MEAL-05's
 * transaction and MEAL-08's planned-vs-logged status are that use case's contract, and a
 * second writer would have to reimplement both and eventually disagree (TODAY-13).
 *
 * A failure on any day stops and returns it. Partial is the honest outcome: the days already
 * written are real, and the alternative — unwinding writes that succeeded — is a second
 * transaction to get wrong. The caller states what landed.
 */
class PlanMealUseCase(
    private val addLogEntries: AddLogEntriesUseCase,
) {
    suspend operator fun invoke(
        meal: ComposedMeal,
        slot: MealSlot,
        days: List<LocalDate>,
    ): AppResult<Int> {
        if (meal.isEmpty || days.isEmpty()) return AppResult.Success(0)

        var written = 0
        for (date in days) {
            val entries = meal.foods.map { food ->
                NewLogEntry(
                    // The same deterministic id the tray mints (MEAL-05): a retry after a
                    // dropped write replaces the row instead of duplicating the meal, and
                    // planning the same meal into the same slot twice corrects it.
                    id = "${date}_${slot.name}_${food.id}",
                    foodId = food.id,
                    name = food.name,
                    serving = food.serving,
                    kcal = food.kcal,
                    proteinG = food.proteinG,
                    carbsG = food.carbsG,
                    fatG = food.fatG,
                    veg = food.veg,
                )
            }
            when (val result = addLogEntries(entries, date, slot)) {
                is AppResult.Failure -> return result
                is AppResult.Success -> written++
            }
        }
        return AppResult.Success(written)
    }
}
