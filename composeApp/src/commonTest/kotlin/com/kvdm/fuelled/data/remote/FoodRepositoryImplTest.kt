package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.fakes.FakeFoodDao
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first

/**
 * The exemplar data-layer test. [FoodRepositoryImpl] is Room-backed via [FoodDao]; here it
 * runs against a hand-written in-memory DAO fake so the repository is exercised through its
 * DOMAIN contract (AppResult in, never an exception out) with no real database. When you swap
 * the DAO for a Firestore/Ktor + Room implementation, keep this shape.
 */
class FoodRepositoryImplTest {

    private fun repository() = FoodRepositoryImpl(FakeFoodDao())

    // SPEC: FOODS-01
    @Test
    fun `seeds the catalog on first read and returns it as Success`() = runTest {
        val foods = when (val result = repository().getFoods()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> fail("seeded source should succeed, got $result")
        }

        assertTrue(foods.isNotEmpty(), "the source should seed the catalog on first run")
        assertEquals(foods.size, foods.map { it.id }.toSet().size, "food ids must be unique")
        assertTrue(foods.all { it.name.isNotBlank() }, "every food needs a name")
    }

    // SPEC: FOODS-02
    @Test
    fun `search filters the seeded catalog by name`() = runTest {
        val result = repository().searchFoods("chicken")

        val foods = (result as AppResult.Success).value
        assertTrue(foods.isNotEmpty(), "search should match the seeded chicken breast entry")
        assertTrue(foods.all { it.name.contains("chicken", ignoreCase = true) })
    }

    // SPEC: FOODS-06
    @Test
    fun `getFood resolves a seeded id as Success`() = runTest {
        val repo = repository()
        val target = (repo.getFoods() as AppResult.Success).value.first()

        assertEquals(AppResult.Success(target), repo.getFood(target.id))
    }

    // SPEC: FOODS-07
    @Test
    fun `getFood returns typed NotFound for an unknown id`() = runTest {
        assertEquals(AppResult.Failure(DomainError.NotFound), repository().getFood("no-such-id"))
    }
}
