package com.kvdm.fuelled.domain.repository

import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.result.AppResult

// Domain-facing contract for the Foods catalog. Presentation depends on THIS, never on the
// concrete Room-backed source. One-shot operations return AppResult — they never throw
// (ARCH-06): failures cross the boundary as typed DomainError values, translated inside the
// data implementation.
interface FoodRepository {
    /** The whole catalog. */
    suspend fun getFoods(): AppResult<List<Food>>

    /** The catalog filtered by [query] (name or brand, case-insensitive); blank returns all. */
    suspend fun searchFoods(query: String): AppResult<List<Food>>

    /** A single catalog entry by id — [AppResult.Failure] with `DomainError.NotFound` if absent. */
    suspend fun getFood(id: String): AppResult<Food>
}
