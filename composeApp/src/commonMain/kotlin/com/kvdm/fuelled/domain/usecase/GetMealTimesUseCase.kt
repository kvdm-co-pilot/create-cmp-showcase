package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.MealTimes
import com.kvdm.fuelled.domain.repository.MealPlanRepository
import com.kvdm.fuelled.domain.result.AppResult

/**
 * The six slot times as currently stored (PLAN-05) — Body-for-LIFE defaults for any slot the
 * user has never changed, so every container always has a time and the app never has to prompt
 * for one.
 */
class GetMealTimesUseCase(
    private val repository: MealPlanRepository,
) {
    suspend operator fun invoke(): AppResult<MealTimes> = repository.mealTimes()
}
