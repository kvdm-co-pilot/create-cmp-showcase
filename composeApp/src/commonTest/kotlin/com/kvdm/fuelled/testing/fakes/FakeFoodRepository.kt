package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.repository.FoodRepository
import com.kvdm.fuelled.domain.result.AppResult

/**
 * Hand-written fake — the template's testing convention (no mocking frameworks: they are
 * JVM-only in KMP, and interface-driven fakes keep the architecture honest).
 *
 * The pattern every fake follows:
 *  - configurable behavior (`foods`, `failure`) so a test arranges its scenario,
 *  - recorded interactions (`getFoodsCallCount`, `lastQuery`) so a test can assert usage,
 *  - implements the DOMAIN interface, never a concrete data source.
 *
 * Failures are arranged as typed [DomainError] KINDS, mirroring the real contract — the fake
 * returns [AppResult.Failure]; it never throws (repositories don't, per ARCH-06). Search
 * filters in-memory the same way the Room DAO's LIKE query does (name or brand, ignoring case).
 */
class FakeFoodRepository : FoodRepository {

    var foods: List<Food> = emptyList()
    var failure: DomainError? = null

    var getFoodsCallCount: Int = 0
        private set
    var lastQuery: String? = null
        private set

    override suspend fun getFoods(): AppResult<List<Food>> {
        getFoodsCallCount++
        failure?.let { return AppResult.Failure(it) }
        return AppResult.Success(foods)
    }

    override suspend fun searchFoods(query: String): AppResult<List<Food>> {
        lastQuery = query
        failure?.let { return AppResult.Failure(it) }
        val trimmed = query.trim()
        val result =
            if (trimmed.isEmpty()) foods
            else foods.filter {
                it.name.contains(trimmed, ignoreCase = true) || it.brand.contains(trimmed, ignoreCase = true)
            }
        return AppResult.Success(result)
    }

    override suspend fun getFood(id: String): AppResult<Food> {
        failure?.let { return AppResult.Failure(it) }
        val match = foods.firstOrNull { it.id == id }
        return if (match != null) AppResult.Success(match) else AppResult.Failure(DomainError.NotFound)
    }
}
