package com.kvdm.cmpshowcase.domain.usecase

import com.kvdm.cmpshowcase.domain.model.Item
import com.kvdm.cmpshowcase.testing.fakes.FakeItemRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

/**
 * The exemplar use-case test. Use cases are pure business actions with no framework
 * dependencies, so their tests are the simplest in the pyramid: fake in, behavior out.
 */
class GetItemsUseCaseTest {

    private val repository = FakeItemRepository()
    private val getItems = GetItemsUseCase(repository)

    @Test
    fun `returns the repository's items`() = runTest {
        val expected = listOf(
            Item(id = "1", title = "First", subtitle = "a"),
            Item(id = "2", title = "Second", subtitle = "b"),
        )
        repository.items = expected

        assertEquals(expected, getItems())
        assertEquals(1, repository.getItemsCallCount)
    }

    @Test
    fun `propagates repository failures to the caller`() = runTest {
        repository.shouldFail = true

        assertFailsWith<IllegalStateException> { getItems() }
    }
}
