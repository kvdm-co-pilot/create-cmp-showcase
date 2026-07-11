package com.kvdm.cmpshowcase.testing.fakes

import com.kvdm.cmpshowcase.domain.model.Favorite
import com.kvdm.cmpshowcase.domain.repository.FavoriteRepository

/**
 * Hand-written fake — the template's testing convention (no mocking frameworks: they are
 * JVM-only in KMP, and interface-driven fakes keep the architecture honest).
 *
 * The pattern every fake follows:
 *  - configurable behavior (`items`, `shouldFail`) so a test arranges its scenario,
 *  - recorded interactions (`getFavoritesCallCount`) so a test can assert usage,
 *  - implements the DOMAIN interface, never a concrete data source.
 */
class FakeFavoriteRepository : FavoriteRepository {

    var items: List<Favorite> = emptyList()
    var shouldFail: Boolean = false
    var failureMessage: String = "fake failure"

    var getFavoritesCallCount: Int = 0
        private set

    override suspend fun getFavorites(): List<Favorite> {
        getFavoritesCallCount++
        if (shouldFail) throw IllegalStateException(failureMessage)
        return items
    }
}
