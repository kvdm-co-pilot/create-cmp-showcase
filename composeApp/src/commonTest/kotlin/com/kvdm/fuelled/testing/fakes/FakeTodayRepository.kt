package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.LogEntry
import com.kvdm.fuelled.domain.model.LogStatus
import com.kvdm.fuelled.domain.model.MacroProgress
import com.kvdm.fuelled.domain.model.MealGroup
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.NewLogEntry
import com.kvdm.fuelled.domain.model.TodayModel
import com.kvdm.fuelled.domain.repository.TodayRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.datetime.LocalDate

/**
 * Hand-written fake — the template's testing convention (no mocking frameworks). Follows the
 * FakeFoodRepository pattern: configurable behaviour (`summary`, `failure`), recorded
 * interactions (`getCallCount`, `addCalls`, `deletedIds`, `markedLoggedIds`), implements the
 * DOMAIN interface, and returns typed [AppResult.Failure] — it never throws (ARCH-06).
 *
 * The write recordings are what the write-path USE-CASE tests assert on: the use cases' whole
 * job is deciding WHAT to ask the repository for (MEAL-08's status, above all), so what
 * reached the repository is the behaviour under test.
 */
class FakeTodayRepository : TodayRepository {

    var summary: TodayModel = populatedDay
    var failure: DomainError? = null

    var getCallCount: Int = 0
        private set

    /** One `addEntries` call as the caller issued it — target and status included (MEAL-05/08). */
    data class AddCall(
        val entries: List<NewLogEntry>,
        val date: LocalDate,
        val slot: MealSlot,
        val status: LogStatus,
    )

    /** Every write ATTEMPT, recorded before `failure` is applied, so failures are observable too. */
    val addCalls: MutableList<AddCall> = mutableListOf()
    val deletedIds: MutableList<String> = mutableListOf()
    val markedLoggedIds: MutableList<String> = mutableListOf()

    override suspend fun getTodaySummary(): AppResult<TodayModel> {
        getCallCount++
        failure?.let { return AppResult.Failure(it) }
        return AppResult.Success(summary)
    }

    override suspend fun addEntries(
        entries: List<NewLogEntry>,
        date: LocalDate,
        slot: MealSlot,
        status: LogStatus,
    ): AppResult<Unit> {
        addCalls += AddCall(entries, date, slot, status)
        failure?.let { return AppResult.Failure(it) }
        return AppResult.Success(Unit)
    }

    override suspend fun deleteEntry(id: String): AppResult<Unit> {
        deletedIds += id
        failure?.let { return AppResult.Failure(it) }
        return AppResult.Success(Unit)
    }

    override suspend fun markEntryLogged(id: String): AppResult<Unit> {
        markedLoggedIds += id
        failure?.let { return AppResult.Failure(it) }
        return AppResult.Success(Unit)
    }

    companion object {
        /** The logical day these fixtures are dated with — a real Wednesday, so labels read true. */
        val fixtureDate = LocalDate(2026, 7, 22)

        val populatedDay = TodayModel(
            date = fixtureDate,
            consumedKcal = 535,
            targetKcal = 2400,
            protein = MacroProgress("Protein", 39, 180, "g"),
            carbs = MacroProgress("Carbs", 79, 260, "g"),
            fat = MacroProgress("Fat", 9, 70, "g"),
            meals = listOf(
                MealGroup(MealSlot.BREAKFAST, listOf(LogEntry("b1", "Rolled oats & whey", "80 g · 1 scoop", 430, 38))),
                MealGroup(MealSlot.SNACK, listOf(LogEntry("s1", "Banana", "1 medium", 105, 1))),
            ),
        )

        /** A day with a goal but no logged entries — consumed 0, so the ring reads full target (TODAY-04). */
        val emptyDay = TodayModel(
            date = fixtureDate,
            consumedKcal = 0,
            targetKcal = 2400,
            protein = MacroProgress("Protein", 0, 180, "g"),
            carbs = MacroProgress("Carbs", 0, 260, "g"),
            fat = MacroProgress("Fat", 0, 70, "g"),
            meals = emptyList(),
        )
    }
}
