package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.repository.FoodRepository
import com.kvdm.fuelled.domain.result.AppResult

// Resolve one catalog entry by id — the Food detail's business action. A missing id is a
// typed AppResult.Failure(DomainError.NotFound), never an exception (ARCH-06).
class GetFoodUseCase(
    private val repository: FoodRepository,
) {
    suspend operator fun invoke(id: String): AppResult<Food> = repository.getFood(id)
}
