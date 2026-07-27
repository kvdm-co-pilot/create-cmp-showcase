package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.repository.MealPlanRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Copy a day's planned meals onto the days after it (PLAN-20).
 *
 * This is the clause that makes the feature usable at Body-for-LIFE pace. The method runs on
 * batch prep and heavy repetition — the same five things, most days — so planning a week
 * honestly means building one day and repeating it. Without this, a planned week is six
 * containers × seven days of hand entry, and nobody does it twice.
 *
 * The source day is never modified, and each copy gets its own identity, so editing Thursday
 * does not reach back into Monday.
 */
class CopyDayForwardUseCase(
    private val repository: MealPlanRepository,
) {
    /**
     * @param days how many days after [from] to copy onto, 1..N. Consecutive by construction:
     *   "the rest of my week" is the actual ask, and an arbitrary set of dates would need a
     *   date-picking surface the feature deliberately does not have (the day strip is the only
     *   date selector, PLAN-11).
     */
    suspend operator fun invoke(from: LocalDate, days: Int): AppResult<Unit> {
        require(days >= 1) { "copy-forward needs at least one target day, was $days" }
        return repository.copyDayForward(
            from = from,
            to = (1..days).map { from.plus(it, DateTimeUnit.DAY) },
        )
    }
}
