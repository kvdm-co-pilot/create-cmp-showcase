package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.repository.FoodRepository
import com.kvdm.fuelled.domain.result.AppResult

// Foods is a searchable catalog, so search is its own business action: the filter runs at the
// source (the repository/DAO), never in the composable. A blank query is a valid input that
// yields the whole catalog — the repository owns that rule, not the caller.
class SearchFoodsUseCase(
    private val repository: FoodRepository,
) {
    suspend operator fun invoke(query: String): AppResult<List<Food>> = repository.searchFoods(query)
}
