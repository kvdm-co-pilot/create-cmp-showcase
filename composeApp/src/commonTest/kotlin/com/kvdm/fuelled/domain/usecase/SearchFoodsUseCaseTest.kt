package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.fakes.FakeFoodRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** Search is its own business action — the filter runs at the source, a blank query is valid. */
class SearchFoodsUseCaseTest {

    private val repository = FakeFoodRepository()
    private val searchFoods = SearchFoodsUseCase(repository)

    private val chicken = Food("1", "Chicken breast", "Raw", "100 g", 165, 31, 0, 4)
    private val oats = Food("2", "Rolled oats", "Quaker", "80 g", 303, 11, 54, 6)

    // SPEC: FOODS-02
    @Test
    fun `filters by name or brand, case-insensitively`() = runTest {
        repository.foods = listOf(chicken, oats)

        assertEquals(AppResult.Success(listOf(oats)), searchFoods("QUAKER"))
    }

    // SPEC: FOODS-02
    @Test
    fun `a blank query returns the whole catalog`() = runTest {
        val all = listOf(chicken, oats)
        repository.foods = all

        assertEquals(AppResult.Success(all), searchFoods("   "))
    }
}
