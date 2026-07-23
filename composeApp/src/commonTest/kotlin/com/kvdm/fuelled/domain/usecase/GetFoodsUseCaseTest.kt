package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.fakes.FakeFoodRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * The exemplar use-case test. Use cases are pure business actions with no framework
 * dependencies, so their tests are the simplest in the pyramid: fake in, behavior out.
 * Results stay typed end-to-end — nothing here throws or catches.
 */
class GetFoodsUseCaseTest {

    private val repository = FakeFoodRepository()
    private val getFoods = GetFoodsUseCase(repository)

    // SPEC: FOODS-01
    @Test
    fun `returns the repository's catalog as Success`() = runTest {
        val expected = listOf(
            Food("1", "Chicken breast", "Raw", "100 g", 165, 31, 0, 4),
            Food("2", "Rolled oats", "Quaker", "80 g", 303, 11, 54, 6),
        )
        repository.foods = expected

        assertEquals(AppResult.Success(expected), getFoods())
        assertEquals(1, repository.getFoodsCallCount)
    }

    // SPEC: FOODS-01
    @Test
    fun `passes a typed failure through untouched`() = runTest {
        repository.failure = DomainError.Network

        assertEquals(AppResult.Failure(DomainError.Network), getFoods())
    }
}
