package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.repository.MealPlanRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.datetime.LocalDate

/**
 * Tick one 500 ml water container, or un-tick it (PLAN-10). The day's litres are counted from
 * the ticks on read, so there is no running total to keep in step — and a new logical day has
 * no ticks, which is why it starts at 0.0 L with nothing to reset.
 */
class SetWaterDoneUseCase(
    private val repository: MealPlanRepository,
) {
    suspend operator fun invoke(date: LocalDate, index: Int, done: Boolean): AppResult<Unit> =
        repository.setWaterDone(date, index, done)
}
