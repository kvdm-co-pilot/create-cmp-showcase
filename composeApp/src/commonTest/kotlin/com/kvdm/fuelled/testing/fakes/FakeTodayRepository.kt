package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.domain.model.DatedGoal
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.LogEntry
import com.kvdm.fuelled.domain.model.DeletedEntry
import com.kvdm.fuelled.domain.model.LogStatus
import com.kvdm.fuelled.domain.model.MacroProgress
import com.kvdm.fuelled.domain.model.MealGroup
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.NewLogEntry
import com.kvdm.fuelled.domain.model.TodayModel
import com.kvdm.fuelled.domain.repository.TodayRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
        set(value) { field = value; revision.value += 1 }
    var failure: DomainError? = null
        set(value) { field = value; revision.value += 1 }

    /**
     * What makes this fake OBSERVABLE. Setting `summary` or `failure` re-emits, so a test can
     * assert that a collector saw the change — the behaviour the real repository gets from
     * Room's invalidation tracker, and the whole point of the read path being a stream.
     */
    private val revision = MutableStateFlow(0)

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

    suspend fun getTodaySummary(): AppResult<TodayModel> {
        getCallCount++
        failure?.let { return AppResult.Failure(it) }
        return AppResult.Success(summary)
    }

    override fun observeTodaySummary(): Flow<AppResult<TodayModel>> =
        revision.map { failure?.let { AppResult.Failure(it) } ?: AppResult.Success(summary) }

    override fun observeGoalHistory(): Flow<AppResult<List<DatedGoal>>> =
        revision.map { failure?.let { AppResult.Failure(it) } ?: AppResult.Success(goals) }

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

    override suspend fun deleteEntry(id: String): AppResult<DeletedEntry> {
        deletedIds += id
        failure?.let { return AppResult.Failure(it) }
        // ENTRY-02: hand back a faithful undo record so a test can prove the restore path.
        return AppResult.Success(
            DeletedEntry(
                id = id,
                foodId = "",
                date = LocalDate(2026, 7, 22),
                slot = MealSlot.LUNCH,
                status = LogStatus.LOGGED,
                entryOrder = 0,
                name = "Removed food",
                serving = "100 g",
                kcal = 100,
                proteinG = 10,
                carbsG = 5,
                fatG = 2,
                servings = 1,
                veg = false,
            ),
        )
    }

    /** ENTRY-02: recorded restores, so a test can assert the undo reached the ledger. */
    val restoredIds: MutableList<String> = mutableListOf()

    override suspend fun restoreEntry(entry: DeletedEntry): AppResult<Unit> {
        restoredIds += entry.id
        failure?.let { return AppResult.Failure(it) }
        return AppResult.Success(Unit)
    }

    /** ENTRY-01: recorded serving edits — (id, servings). */
    val servingEdits: MutableList<Pair<String, Int>> = mutableListOf()

    override suspend fun setEntryServings(id: String, servings: Int): AppResult<Unit> {
        servingEdits += id to servings
        failure?.let { return AppResult.Failure(it) }
        return AppResult.Success(Unit)
    }

    override suspend fun markEntryLogged(id: String): AppResult<Unit> {
        markedLoggedIds += id
        failure?.let { return AppResult.Failure(it) }
        return AppResult.Success(Unit)
    }

    /**
     * GOAL-01: the goal history the trend resolves against. Defaults to ONE goal effective
     * from the beginning of time, which is what a fresh install has — so a test that says
     * nothing about goals gets the pre-dating behaviour and reads the same as it always did.
     */
    var goals: List<DatedGoal> = listOf(
        DatedGoal(LocalDate(1, 1, 1), populatedDay.targetKcal, populatedDay.protein.target),
    )
        set(value) { field = value; revision.value += 1 }

    /** PERS-01: recorded and APPLIED to the observed summary — the fake re-emits like Room. */
    val goalUpdates: MutableList<Pair<Int, Int>> = mutableListOf()

    override suspend fun updateGoals(targetKcal: Int, proteinTargetG: Int): AppResult<Unit> {
        failure?.let { return AppResult.Failure(it) }
        goalUpdates += targetKcal to proteinTargetG
        // GOAL-02: effective from today, replacing today's row if one exists.
        val today = LocalDate(2026, 7, 22)
        goals = goals.filterNot { it.effectiveFrom == today } + DatedGoal(today, targetKcal, proteinTargetG)
        summary = summary.copy(
            targetKcal = targetKcal,
            protein = summary.protein.copy(target = proteinTargetG),
        )
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
                MealGroup(MealSlot.MORNING_SNACK, listOf(LogEntry("s1", "Banana", "1 medium", 105, 1))),
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
