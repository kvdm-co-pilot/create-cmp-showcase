package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.data.local.LogEntryEntity
import com.kvdm.fuelled.data.local.TodayDao
import com.kvdm.fuelled.data.local.TodayGoalEntity
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.fakes.FakeTodayDao
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runTest

/**
 * The Today data-layer test. [TodayRepositoryImpl] is Room-backed via [TodayDao]; here it runs
 * against a hand-written in-memory DAO fake, exercising the repository through its DOMAIN
 * contract (AppResult in, never an exception out) with no real database — and, crucially,
 * asserting the AGGREGATION the repository owns (mirrors FoodRepositoryImplTest's shape).
 */
class TodayRepositoryImplTest {

    private fun repository() = TodayRepositoryImpl(FakeTodayDao())

    // SPEC: TODAY-01
    @Test
    fun `seeds a realistic day on first read and returns it as Success`() = runTest {
        val model = when (val result = repository().getTodaySummary()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> fail("seeded source should succeed, got $result")
        }

        assertTrue(model.dateLabel.isNotBlank(), "the seeded day needs a date label")
        assertTrue(model.targetKcal > 0, "the seeded day needs a calorie target")
        assertTrue(model.meals.isNotEmpty(), "the source should seed a sample day's log on first run")
    }

    // SPEC: TODAY-03
    @Test
    fun `aggregates entries into meal groups in order with meal totals and a day total that is the sum of every entry`() =
        runTest {
            val model = (repository().getTodaySummary() as AppResult.Success).value

            // Grouped by meal, in the seeded meal order.
            assertEquals(listOf("Breakfast", "Lunch", "Snack"), model.meals.map { it.name })

            // Each meal's total is the sum of its own entries.
            model.meals.forEach { meal ->
                assertEquals(meal.entries.sumOf { it.kcal }, meal.kcal, "${meal.name} total must equal its entries")
            }

            // The day's consumed total equals the sum of EVERY entry's calories (TODAY-03).
            val everyEntry = model.meals.flatMap { it.entries }
            assertEquals(everyEntry.sumOf { it.kcal }, model.consumedKcal)
        }

    // SPEC: TODAY-02
    @Test
    fun `computes each macro's current as the sum across every entry against the goal target`() = runTest {
        val model = (repository().getTodaySummary() as AppResult.Success).value

        // The proteinG the screen shows per entry must sum to the aggregate protein current.
        val proteinFromEntries = model.meals.flatMap { it.entries }.sumOf { it.proteinG }
        assertEquals(proteinFromEntries, model.protein.current)
        assertTrue(model.protein.target > 0 && model.carbs.target > 0 && model.fat.target > 0)
    }

    // SPEC: TODAY-05
    @Test
    fun `translates a thrown source error into a typed Failure - never lets it escape`() = runTest {
        val result = TodayRepositoryImpl(ThrowingTodayDao()).getTodaySummary()
        assertIs<AppResult.Failure>(result)
    }

    /** A DAO whose reads fail — proves the repository translates infrastructure errors (never throws). */
    private class ThrowingTodayDao : TodayDao {
        override suspend fun goal(): TodayGoalEntity? = throw IllegalStateException("db unavailable")
        override suspend fun entries(): List<LogEntryEntity> = throw IllegalStateException("db unavailable")
        override suspend fun goalCount(): Int = 1 // non-zero so the repo skips seeding and hits goal()
        override suspend fun upsertGoal(goal: TodayGoalEntity) = Unit
        override suspend fun upsertEntries(entries: List<LogEntryEntity>) = Unit
        override suspend fun clearEntries() = Unit
    }
}
