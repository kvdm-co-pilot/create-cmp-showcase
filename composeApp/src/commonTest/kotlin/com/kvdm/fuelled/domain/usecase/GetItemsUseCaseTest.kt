package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Item
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.fakes.FakeItemRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * The exemplar use-case test. Use cases are pure business actions with no framework
 * dependencies, so their tests are the simplest in the pyramid: fake in, behavior out.
 * Results stay typed end-to-end — nothing here throws or catches.
 */
class GetItemsUseCaseTest {

    private val repository = FakeItemRepository()
    private val getItems = GetItemsUseCase(repository)

    @Test
    fun `returns the repository's items as Success`() = runTest {
        val expected = listOf(
            Item(id = "1", title = "First", subtitle = "a"),
            Item(id = "2", title = "Second", subtitle = "b"),
        )
        repository.items = expected

        assertEquals(AppResult.Success(expected), getItems())
        assertEquals(1, repository.getItemsCallCount)
    }

    @Test
    fun `passes a typed failure through untouched`() = runTest {
        repository.failure = DomainError.Network

        assertEquals(AppResult.Failure(DomainError.Network), getItems())
    }
}
