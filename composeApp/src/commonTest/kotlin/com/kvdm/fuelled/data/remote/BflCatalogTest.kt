package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.domain.model.BflCategory
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.model.Macros100g
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.fakes.FakeFoodDao
import com.kvdm.fuelled.testing.fakes.FakeTodayDao
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runTest

/**
 * The seeded catalog (BFL-01..03) — the guard on the data itself.
 *
 * These assertions exist because the catalog's whole claim is that its numbers are real. A
 * food that lost a macro, a portion, a category or its provenance would still render fine and
 * would quietly make every total that touches it wrong.
 */
class BflCatalogTest {

    private fun repository() = FoodRepositoryImpl(FakeFoodDao(), FakeTodayDao())

    private suspend fun catalog(): List<Food> =
        when (val r = repository().getFoods()) {
            is AppResult.Success -> r.value
            is AppResult.Failure -> fail("expected the seeded catalog, got $r")
        }

    // SPEC: BFL-01
    // SPEC: BFL-03
    @Test
    fun `the catalog seeds offline and every food carries real USDA values`() = runTest {
        val foods = catalog()

        // BFL-03: seeded from data compiled into the app — no network was available to this
        // test, and none was needed.
        assertTrue(foods.size >= 50, "a catalog worth having, seeded on first run: ${foods.size}")

        foods.forEach { food ->
            assertNotNull(food.fdcId, "${food.name} has no USDA provenance")
            assertTrue(food.portionGrams > 0, "${food.name} has no portion")
            assertTrue(food.serving.isNotBlank(), "${food.name} has no portion label")
            // Energy is the one macro every food must have: a zero would silently subtract
            // that food from every day's total it appears in.
            assertTrue(food.per100g.kcal > 0.0, "${food.name} has no energy per 100 g")
            assertTrue(food.per100g.proteinG >= 0.0 && food.per100g.carbsG >= 0.0 && food.per100g.fatG >= 0.0)
            assertTrue(food.kcal > 0, "${food.name}'s portion has no calories")
        }

        // All four Body-for-LIFE roles are populated — a builder with an empty column cannot
        // compose the method's meal (BFL-05).
        BflCategory.entries.forEach { category ->
            assertTrue(foods.any { it.category == category }, "no $category foods in the catalog")
        }
        // And vegetables are flagged as such, so the veg-with-two-meals rule (PLAN-22) can
        // ever be satisfied.
        assertTrue(foods.filter { it.category == BflCategory.VEGETABLE }.all { it.veg })
    }

    // SPEC: BFL-01
    @Test
    fun `known foods match their published USDA values exactly`() = runTest {
        val byId = catalog().associateBy { it.id }

        // Spot-checks against USDA SR Legacy, by FDC id. If a regeneration ever silently
        // changed the numbers, these are the rows that would say so.
        val chicken = assertNotNull(byId["chicken-breast"])
        assertEquals(171477, chicken.fdcId)
        assertEquals(165.0, chicken.per100g.kcal)
        assertEquals(31.02, chicken.per100g.proteinG)
        assertEquals(0.0, chicken.per100g.carbsG)
        assertEquals(3.57, chicken.per100g.fatG)

        val banana = assertNotNull(byId["banana"])
        assertEquals(173944, banana.fdcId)
        assertEquals(89.0, banana.per100g.kcal)
        assertEquals(22.84, banana.per100g.carbsG)

        val rice = assertNotNull(byId["brown-rice"])
        assertEquals(169704, rice.fdcId)
        assertEquals(123.0, rice.per100g.kcal)
    }

    // SPEC: BFL-02
    @Test
    fun `a portion is per-100g scaled, and the stored portion agrees with that scaling`() =
        runTest {
            val foods = catalog()

            // The stored display macros are DERIVED from per-100 g at the food's portion. If
            // these ever diverge, a food's card and any total containing it disagree — the
            // defect that rendered a builder "0 kcal" under three chosen foods (2026-08-02).
            foods.forEach { food ->
                val scaled = food.per100g.at(food.portionGrams)
                assertEquals(scaled.kcal, food.kcal, "${food.name}: portion kcal disagrees with per-100 g")
                assertEquals(scaled.proteinG, food.proteinG, "${food.name}: portion protein disagrees")
                assertEquals(scaled.carbsG, food.carbsG, "${food.name}: portion carbs disagree")
                assertEquals(scaled.fatG, food.fatG, "${food.name}: portion fat disagrees")
            }

            // And the rule itself: half the quantity is half the macros, rounded once.
            val per100g = Macros100g(kcal = 165.0, proteinG = 31.02, carbsG = 0.0, fatG = 3.57)
            assertEquals(165, per100g.at(100).kcal)
            assertEquals(83, per100g.at(50).kcal, "half a portion, rounded half-up")
            assertEquals(198, per100g.at(120).kcal)
            assertEquals(0, per100g.at(0).kcal, "no quantity is no macros, never a negative")
        }
}
