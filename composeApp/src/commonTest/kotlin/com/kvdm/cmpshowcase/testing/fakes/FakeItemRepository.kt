package com.kvdm.cmpshowcase.testing.fakes

import com.kvdm.cmpshowcase.domain.model.Item
import com.kvdm.cmpshowcase.domain.repository.ItemRepository

/**
 * Hand-written fake — the template's testing convention (no mocking frameworks: they are
 * JVM-only in KMP, and interface-driven fakes keep the architecture honest).
 *
 * The pattern every fake follows:
 *  - configurable behavior (`items`, `shouldFail`) so a test arranges its scenario,
 *  - recorded interactions (`getItemsCallCount`) so a test can assert usage,
 *  - implements the DOMAIN interface, never a concrete data source.
 */
class FakeItemRepository : ItemRepository {

    var items: List<Item> = emptyList()
    var shouldFail: Boolean = false
    var failureMessage: String = "fake failure"

    var getItemsCallCount: Int = 0
        private set

    override suspend fun getItems(): List<Item> {
        getItemsCallCount++
        if (shouldFail) throw IllegalStateException(failureMessage)
        return items
    }
}
