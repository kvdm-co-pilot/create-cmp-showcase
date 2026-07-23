package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.LogEntry
import com.kvdm.fuelled.domain.model.MacroProgress
import com.kvdm.fuelled.domain.model.MealGroup
import com.kvdm.fuelled.domain.model.TodayModel
import com.kvdm.fuelled.domain.repository.TodayRepository
import com.kvdm.fuelled.domain.result.AppResult

/**
 * Hand-written fake — the template's testing convention (no mocking frameworks). Follows the
 * FakeFoodRepository pattern: configurable behaviour (`summary`, `failure`), recorded
 * interactions (`getCallCount`), implements the DOMAIN interface, and returns typed
 * [AppResult.Failure] — it never throws (repositories don't, per ARCH-06).
 */
class FakeTodayRepository : TodayRepository {

    var summary: TodayModel = populatedDay
    var failure: DomainError? = null

    var getCallCount: Int = 0
        private set

    override suspend fun getTodaySummary(): AppResult<TodayModel> {
        getCallCount++
        failure?.let { return AppResult.Failure(it) }
        return AppResult.Success(summary)
    }

    companion object {
        val populatedDay = TodayModel(
            dateLabel = "Wednesday, Jul 23",
            consumedKcal = 535,
            targetKcal = 2400,
            protein = MacroProgress("Protein", 39, 180, "g"),
            carbs = MacroProgress("Carbs", 79, 260, "g"),
            fat = MacroProgress("Fat", 9, 70, "g"),
            meals = listOf(
                MealGroup("Breakfast", listOf(LogEntry("Rolled oats & whey", "80 g · 1 scoop", 430, 38))),
                MealGroup("Snack", listOf(LogEntry("Banana", "1 medium", 105, 1))),
            ),
        )

        /** A day with a goal but no logged entries — consumed 0, so the ring reads full target (TODAY-04). */
        val emptyDay = TodayModel(
            dateLabel = "Wednesday, Jul 23",
            consumedKcal = 0,
            targetKcal = 2400,
            protein = MacroProgress("Protein", 0, 180, "g"),
            carbs = MacroProgress("Carbs", 0, 260, "g"),
            fat = MacroProgress("Fat", 0, 70, "g"),
            meals = emptyList(),
        )
    }
}
