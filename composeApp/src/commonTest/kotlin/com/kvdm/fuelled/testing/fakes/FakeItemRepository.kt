package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Item
import com.kvdm.fuelled.domain.repository.ItemRepository
import com.kvdm.fuelled.domain.result.AppResult

/**
 * Hand-written fake — the template's testing convention (no mocking frameworks: they are
 * JVM-only in KMP, and interface-driven fakes keep the architecture honest).
 *
 * The pattern every fake follows:
 *  - configurable behavior (`items`, `failure`) so a test arranges its scenario,
 *  - recorded interactions (`getItemsCallCount`) so a test can assert usage,
 *  - implements the DOMAIN interface, never a concrete data source.
 *
 * Failures are arranged as typed [DomainError] KINDS, mirroring the real contract — the
 * fake returns [AppResult.Failure]; it never throws (repositories don't, per ARCH-06).
 */
class FakeItemRepository : ItemRepository {

    var items: List<Item> = emptyList()
    var failure: DomainError? = null

    var getItemsCallCount: Int = 0
        private set

    override suspend fun getItems(): AppResult<List<Item>> {
        getItemsCallCount++
        failure?.let { return AppResult.Failure(it) }
        return AppResult.Success(items)
    }
}
