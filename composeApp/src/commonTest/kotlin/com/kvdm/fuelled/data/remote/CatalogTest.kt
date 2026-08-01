package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.model.LogStatus
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.NewLogEntry
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.fakes.FakeFoodDao
import com.kvdm.fuelled.testing.fakes.FakeTodayDao
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * CAT-02/CAT-03 at the data layer — where "favourites lead" and "recents come from the log's
 * own provenance" are actually decided (one SQL ordering, one derivation), rather than in a
 * caller that could quietly disagree with the next caller.
 */
class CatalogTest {

    private val foodDao = FakeFoodDao()
    private val todayDao = FakeTodayDao()
    private fun repository() = FoodRepositoryImpl(foodDao, todayDao)

    private suspend fun foods(): List<Food> = when (val r = repository().getFoods()) {
        is AppResult.Success -> r.value
        is AppResult.Failure -> fail("expected Success, got $r")
    }

    // SPEC: CAT-02
    @Test
    fun `favourites lead every list the catalog feeds`() = runTest {
        val repo = repository()
        repo.getFoods() // seed
        val last = foods().last()

        repo.setFavourite(last.id, true)

        val ordered = foods()
        assertEquals(last.id, ordered.first().id, "a pinned food leads, wherever it sat alphabetically")
        assertTrue(ordered.first().favourite)
    }

    // SPEC: CAT-02
    @Test
    fun `unpinning puts it back in its ordinary place`() = runTest {
        val repo = repository()
        repo.getFoods()
        val last = foods().last()
        repo.setFavourite(last.id, true)

        repo.setFavourite(last.id, false)

        assertEquals(last.id, foods().last().id, "and the list is exactly as it was")
    }

    // SPEC: CAT-03
    @Test
    fun `recents come from what was actually logged, newest day first`() = runTest {
        val repo = repository()
        repo.getFoods()
        val today = TodayRepositoryImpl(todayDao)
        // Logged on two different logical days: the later day must lead.
        today.addEntries(
            listOf(NewLogEntry(id = "e1", foodId = "5", name = "Banana", serving = "1", kcal = 105, proteinG = 1, carbsG = 27, fatG = 0)),
            LocalDate(2026, 7, 20), MealSlot.LUNCH, LogStatus.LOGGED,
        )
        today.addEntries(
            listOf(NewLogEntry(id = "e2", foodId = "1", name = "Chicken", serving = "100 g", kcal = 165, proteinG = 31, carbsG = 0, fatG = 4)),
            LocalDate(2026, 7, 22), MealSlot.DINNER, LogStatus.LOGGED,
        )

        val recents = when (val r = repo.recentFoods(limit = 5)) {
            is AppResult.Success -> r.value
            is AppResult.Failure -> fail("expected Success, got $r")
        }

        assertEquals(listOf("1", "5"), recents.map { it.id }, "most recently eaten leads")
    }

    // SPEC: CAT-03
    @Test
    fun `a recent whose food was deleted is omitted, never rendered broken`() = runTest {
        val repo = repository()
        repo.getFoods()
        TodayRepositoryImpl(todayDao).addEntries(
            listOf(NewLogEntry(id = "e1", foodId = "gone", name = "Deleted food", serving = "1", kcal = 100, proteinG = 5, carbsG = 5, fatG = 5)),
            LocalDate(2026, 7, 22), MealSlot.LUNCH, LogStatus.LOGGED,
        )

        val recents = when (val r = repo.recentFoods(limit = 5)) {
            is AppResult.Success -> r.value
            is AppResult.Failure -> fail("expected Success, got $r")
        }

        assertTrue(recents.none { it.id == "gone" }, "an id with no food is dropped, not surfaced")
    }
}
