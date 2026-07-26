package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR
import com.kvdm.fuelled.data.local.LogEntryEntity
import com.kvdm.fuelled.data.local.TodayDao
import com.kvdm.fuelled.data.local.TodayGoalEntity
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.LogStatus
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.NewLogEntry
import com.kvdm.fuelled.domain.model.TodayModel
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.fakes.FakeTodayDao
import com.kvdm.fuelled.testing.fakes.FixedClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * The Today data-layer test. [TodayRepositoryImpl] is Room-backed via [TodayDao]; here it runs
 * against a hand-written in-memory DAO fake, exercising the repository through its DOMAIN
 * contract (AppResult in, never an exception out) with no real database — and, crucially,
 * asserting the AGGREGATION and the WRITE PATH the repository owns.
 *
 * Every repository here is built with a FIXED clock: the day in view is derived from the
 * current instant on every read (MEAL-02), so a test that used the wall clock would be
 * asserting against whatever day the CI machine happened to be having.
 */
class TodayRepositoryImplTest {

    private val zone = TimeZone.UTC
    private val dayInView = LocalDate(2026, 7, 22)

    /** Midday inside [dayInView] — comfortably inside the logical day on either side. */
    private val noon = LocalDateTime(2026, 7, 22, 12, 0).toInstant(zone)

    /** 03:59 on the NEXT calendar day — still [dayInView] with a 04:00 day start (MEAL-01). */
    private val justBeforeDayStart = LocalDateTime(2026, 7, 23, 3, 59).toInstant(zone)

    private fun repository(
        dao: TodayDao = FakeTodayDao(),
        at: kotlin.time.Instant = noon,
    ) = TodayRepositoryImpl(dao, FixedClock(at), zone, DEFAULT_DAY_START_HOUR)

    private suspend fun TodayRepositoryImpl.summary(): TodayModel =
        when (val result = getTodaySummary()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> fail("seeded source should succeed, got $result")
        }

    private fun tray(vararg ids: String) = ids.map { id ->
        NewLogEntry(id, "Food $id", "1 serving", kcal = 100, proteinG = 10, carbsG = 5, fatG = 2)
    }

    // SPEC: TODAY-01
    @Test
    fun `seeds a realistic day on first read and dates it with the current logical day`() = runTest {
        val model = repository().summary()

        // The date is a real LocalDate derived from the clock — not a stored display label.
        assertEquals(dayInView, model.date)
        assertTrue(model.targetKcal > 0, "the seeded day needs a calorie target")
        assertTrue(model.meals.isNotEmpty(), "the source should seed a sample day's log on first run")
    }

    // SPEC: TODAY-01
    @Test
    fun `dates the day in view by the logical day, not the calendar date, in the small hours`() = runTest {
        val dao = FakeTodayDao()
        repository(dao, at = noon).summary() // seeds the day

        // 03:59 the NEXT calendar day: the clock says the 23rd, the logical day is still the 22nd.
        val afterMidnight = repository(dao, at = justBeforeDayStart).summary()

        assertEquals(dayInView, afterMidnight.date)
        assertTrue(
            afterMidnight.meals.isNotEmpty(),
            "the evening's entries are still the day in view — the rollover mutates nothing (MEAL-02)",
        )
    }

    // SPEC: TODAY-03
    @Test
    fun `groups a day's entries by slot in slot order, with meal totals`() = runTest {
        val dao = FakeTodayDao()
        val repository = repository(dao)
        repository.summary() // seed: BREAKFAST, LUNCH, SNACK
        // Written LAST but slotted DINNER — so a repository that kept insertion order, or let
        // SQLite sort the stored names alphabetically, would put it in the wrong place.
        repository.addEntries(tray("d1"), dayInView, MealSlot.DINNER, LogStatus.LOGGED)

        val model = repository.summary()

        assertEquals(
            listOf(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.DINNER, MealSlot.SNACK),
            model.meals.map { it.slot },
            "meals come out in MealSlot declaration order, whatever order they were written in",
        )
        model.meals.forEach { meal ->
            assertEquals(meal.entries.sumOf { it.kcal }, meal.kcal, "${meal.slot} total must equal its entries")
        }
    }

    // SPEC: TODAY-03
    @Test
    fun `the day's consumed total is the sum of its LOGGED entries - a PLANNED entry is shown but never counted`() =
        runTest {
            val dao = FakeTodayDao()
            val repository = repository(dao)
            val seeded = repository.summary()
            val seededKcal = seeded.meals.flatMap { it.entries }.sumOf { it.kcal }

            // A dinner SCHEDULED for the same day: it belongs to the day, it is not eaten.
            repository.addEntries(tray("p1"), dayInView, MealSlot.DINNER, LogStatus.PLANNED)
            val model = repository.summary()

            val dinner = model.meals.single { it.slot == MealSlot.DINNER }
            assertEquals(LogStatus.PLANNED, dinner.entries.single().status)
            assertEquals(100, dinner.entries.single().kcal, "the planned entry is present in its group")

            val logged = model.meals.flatMap { it.entries }.filter { it.status == LogStatus.LOGGED }
            assertEquals(logged.sumOf { it.kcal }, model.consumedKcal)
            assertEquals(seededKcal, model.consumedKcal, "consumed is unchanged by scheduling a meal")
        }

    // SPEC: TODAY-02
    @Test
    fun `computes each macro's current as the sum across LOGGED entries only, against the goal target`() = runTest {
        val dao = FakeTodayDao()
        val repository = repository(dao)
        val seeded = repository.summary()
        val seededProtein = seeded.meals.flatMap { it.entries }.sumOf { it.proteinG }

        repository.addEntries(tray("p1"), dayInView, MealSlot.DINNER, LogStatus.PLANNED)
        val model = repository.summary()

        val loggedProtein = model.meals.flatMap { it.entries }
            .filter { it.status == LogStatus.LOGGED }
            .sumOf { it.proteinG }
        assertEquals(loggedProtein, model.protein.current)
        assertEquals(seededProtein, model.protein.current, "a planned entry's macros are not progress")
        assertTrue(model.protein.target > 0 && model.carbs.target > 0 && model.fat.target > 0)
    }

    // SPEC: MEAL-05
    @Test
    fun `a multi-item tray confirm writes every item to the target date and slot in one transaction`() = runTest {
        val dao = FakeTodayDao()
        val repository = repository(dao)
        val tomorrow = LocalDate(2026, 7, 23)

        val result = repository.addEntries(tray("t1", "t2", "t3"), tomorrow, MealSlot.DINNER, LogStatus.PLANNED)

        assertIs<AppResult.Success<Unit>>(result)
        assertEquals(1, dao.atomicInsertCount, "the confirm is ONE transaction, not one per item")
        val written = dao.rows.filter { it.id in setOf("t1", "t2", "t3") }
        assertEquals(3, written.size)
        assertTrue(written.all { it.logicalDate == tomorrow.toString() }, "every item lands on the target date")
        assertTrue(written.all { it.slot == MealSlot.DINNER.name }, "every item lands in the target slot")
        assertTrue(written.all { it.status == LogStatus.PLANNED.name }, "every item carries the target's status")
        assertEquals(listOf(0, 1, 2), written.map { it.entryOrder }, "the tray keeps its order in the slot")
    }

    // SPEC: MEAL-05
    @Test
    fun `a mid-write failure persists nothing and surfaces a mapped DomainError`() = runTest {
        val dao = FakeTodayDao()
        val repository = repository(dao)
        repository.summary() // seed, so the day already has rows the rollback must not touch
        val seededIds = dao.rows.map { it.id }
        dao.failInsertOfId = "t2" // the SECOND of three items blows up mid-transaction

        val result = repository.addEntries(tray("t1", "t2", "t3"), dayInView, MealSlot.DINNER, LogStatus.LOGGED)

        val failure = assertIs<AppResult.Failure>(result)
        // A mapped KIND from suspendRunCatching — the source's message never becomes the error.
        assertIs<DomainError.Unexpected>(failure.error)
        assertEquals(
            seededIds,
            dao.rows.map { it.id },
            "the transaction rolled back: not even the first item survived",
        )
        assertNull(dao.rows.firstOrNull { it.id == "t1" }, "no partial meal reaches the ledger")
    }

    // SPEC: MEAL-06
    @Test
    fun `deleting a logged entry removes it and the day's totals recompute without it`() = runTest {
        val dao = FakeTodayDao()
        val repository = repository(dao)
        val before = repository.summary()
        val victim = before.meals.first { it.slot == MealSlot.BREAKFAST }.entries.first()

        val result = repository.deleteEntry(victim.id)

        assertIs<AppResult.Success<Unit>>(result)
        val after = repository.summary()
        assertNull(
            after.meals.flatMap { it.entries }.firstOrNull { it.id == victim.id },
            "the entry is gone from its day",
        )
        assertEquals(before.consumedKcal - victim.kcal, after.consumedKcal)
        assertEquals(before.protein.current - victim.proteinG, after.protein.current)
        assertEquals(before.remainingKcal + victim.kcal, after.remainingKcal)
    }

    // SPEC: MEAL-07
    @Test
    fun `marking a planned entry logged makes it count toward consumed and changes no other entry`() = runTest {
        val dao = FakeTodayDao()
        val repository = repository(dao)
        repository.summary()
        repository.addEntries(tray("p1"), dayInView, MealSlot.DINNER, LogStatus.PLANNED)
        val before = repository.summary()
        val planned = before.meals.single { it.slot == MealSlot.DINNER }.entries.single()

        val result = repository.markEntryLogged(planned.id)

        assertIs<AppResult.Success<Unit>>(result)
        val after = repository.summary()
        val flipped = after.meals.single { it.slot == MealSlot.DINNER }.entries.single()
        assertEquals(LogStatus.LOGGED, flipped.status)
        assertEquals(before.consumedKcal + planned.kcal, after.consumedKcal)
        assertEquals(before.protein.current + planned.proteinG, after.protein.current)
        // No other entry changed — same entries, same statuses, only p1 differs.
        val others = { model: TodayModel -> model.meals.flatMap { it.entries }.filterNot { it.id == planned.id } }
        assertEquals(others(before), others(after))
    }

    // SPEC: TODAY-05
    @Test
    fun `translates a thrown source error into a typed Failure - never lets it escape`() = runTest {
        val result = repository(ThrowingTodayDao()).getTodaySummary()
        assertIs<AppResult.Failure>(result)
    }

    /** A DAO whose reads fail — proves the repository translates infrastructure errors (never throws). */
    private class ThrowingTodayDao : TodayDao {
        override suspend fun goal(): TodayGoalEntity? = throw IllegalStateException("db unavailable")
        override suspend fun entries(logicalDate: String): List<LogEntryEntity> =
            throw IllegalStateException("db unavailable")
        override suspend fun maxEntryOrder(logicalDate: String, slot: String): Int =
            throw IllegalStateException("db unavailable")
        override suspend fun goalCount(): Int = 1 // non-zero so the repo skips seeding and hits goal()
        override suspend fun upsertGoal(goal: TodayGoalEntity) = Unit
        override suspend fun upsertEntry(entry: LogEntryEntity) = Unit
        override suspend fun upsertEntries(entries: List<LogEntryEntity>) = Unit
        override suspend fun deleteEntry(id: String) = Unit
        override suspend fun setStatus(id: String, status: String) = Unit
        override suspend fun clearEntries() = Unit
    }
}
