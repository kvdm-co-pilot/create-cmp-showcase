package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.repository.FoodRepository
import com.kvdm.fuelled.domain.result.AppResult

/** CAT-01: create or edit one catalog food. */
class SaveFoodUseCase(private val repository: FoodRepository) {
    suspend operator fun invoke(food: Food): AppResult<Unit> = repository.saveFood(food)
}

/** CAT-01: remove a custom food from the catalog. */
class DeleteFoodUseCase(private val repository: FoodRepository) {
    suspend operator fun invoke(id: String): AppResult<Unit> = repository.deleteFood(id)
}

/** CAT-02: pin or unpin a food. */
class SetFavouriteUseCase(private val repository: FoodRepository) {
    suspend operator fun invoke(id: String, favourite: Boolean): AppResult<Unit> =
        repository.setFavourite(id, favourite)
}

/** CAT-03: the foods logged most recently — the tray's shortcut past searching. */
class GetRecentFoodsUseCase(private val repository: FoodRepository) {
    suspend operator fun invoke(limit: Int = DEFAULT_LIMIT): AppResult<List<Food>> =
        repository.recentFoods(limit)

    companion object {
        /** Enough to cover a normal week's staples without becoming a second catalog. */
        const val DEFAULT_LIMIT = 8
    }
}
