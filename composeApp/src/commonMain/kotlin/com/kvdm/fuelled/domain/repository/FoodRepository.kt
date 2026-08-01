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

    /**
     * CAT-01: create or replace ONE catalog food. The caller owns the id — a new custom food
     * mints one, an edit reuses it — so this is idempotent, like the log's write path.
     */
    suspend fun saveFood(food: Food): AppResult<Unit>

    /** CAT-01: remove a custom food. Log rows snapshot their own macros, so history survives. */
    suspend fun deleteFood(id: String): AppResult<Unit>

    /** CAT-02: pin or unpin. Favourites lead every list the catalog feeds. */
    suspend fun setFavourite(id: String, favourite: Boolean): AppResult<Unit>

    /** CAT-03: the foods logged most recently, newest first — the tray's shortcut. */
    suspend fun recentFoods(limit: Int): AppResult<List<Food>>
}
