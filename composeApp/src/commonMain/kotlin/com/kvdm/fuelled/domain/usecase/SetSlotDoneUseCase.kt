package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.repository.MealPlanRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.datetime.LocalDate

/**
 * Tick a meal container done, or un-tick it (PLAN-13/PLAN-14).
 *
 * Ticking is the plan→eaten transition: whatever was `PLANNED` in that container becomes
 * `LOGGED` and starts counting toward the day, in one transaction with the tick itself. An
 * empty container is a valid tick and fabricates nothing (PLAN-14) — "ate something off-plan"
 * and "skipped it and moved on" are both completions, and inventing a food to represent either
 * would corrupt the day's totals to keep the data model tidy.
 *
 * Explicitly [done] rather than a toggle: a toggle's outcome depends on state the caller did
 * not read, so two quick taps can land on either answer.
 */
class SetSlotDoneUseCase(
    private val repository: MealPlanRepository,
) {
    suspend operator fun invoke(date: LocalDate, slot: MealSlot, done: Boolean): AppResult<Unit> =
        repository.setSlotDone(date, slot, done)
}
