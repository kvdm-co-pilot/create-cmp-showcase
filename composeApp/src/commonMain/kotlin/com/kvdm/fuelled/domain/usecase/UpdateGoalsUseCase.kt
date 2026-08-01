package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.repository.TodayRepository
import com.kvdm.fuelled.domain.result.AppResult

/**
 * PERS-01/PERS-02: update the daily calorie target and protein goal in the one goal store.
 * Positivity is guarded at the presentation edge (the ViewModel refuses before calling);
 * this use case is a pure pass-through to the single write path.
 */
class UpdateGoalsUseCase(
    private val repository: TodayRepository,
) {
    suspend operator fun invoke(targetKcal: Int, proteinTargetG: Int): AppResult<Unit> =
        repository.updateGoals(targetKcal, proteinTargetG)
}
