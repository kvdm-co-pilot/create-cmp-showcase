package com.kvdm.fuelled.domain.model

import com.kvdm.fuelled.domain.usecase.GetHistoryUseCase
import com.kvdm.fuelled.domain.usecase.GetPlanDayUseCase
import com.kvdm.fuelled.domain.usecase.GetTodaySummaryUseCase
import com.kvdm.fuelled.domain.usecase.ObserveGoalHistoryUseCase
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.TEST_NOW
import com.kvdm.fuelled.testing.TEST_ZONE
import com.kvdm.fuelled.testing.fakes.FakeMealPlanRepository
import com.kvdm.fuelled.testing.fakes.FakeTimeSignal
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * Dated goals (GOAL-01..04) — a target belongs to the days it applied to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DatedGoalTest {

    private val today = LocalDate(2026, 7, 22)

    // SPEC: GOAL-01
    @Test
    fun `the goal for a day is the latest one starting on or before it`() {
        val goals = listOf(
            DatedGoal(LocalDate(2026, 7, 1), 2600, 190),
            DatedGoal(LocalDate(2026, 7, 15), 2400, 180),
        )

        assertEquals(2600, goals.goalOn(LocalDate(2026, 7, 1))?.targetKcal, "effective ON its own day")
        assertEquals(2600, goals.goalOn(LocalDate(2026, 7, 14))?.targetKcal, "the day before the change")
        assertEquals(2400, goals.goalOn(LocalDate(2026, 7, 15))?.targetKcal, "the day of the change")
        assertEquals(2400, goals.goalOn(today)?.targetKcal, "and every day after")

        // GOAL-03/decision D3: the EARLIEST goal reaches back. A day before anyone opened the
        // editor is judged against the seeded default — a day with no applicable goal would
        // render "0 / 0 kcal", which is its own kind of lie.
        assertEquals(2600, goals.goalOn(LocalDate(2020, 1, 1))?.targetKcal, "before any goal existed")
        assertNull(emptyList<DatedGoal>().goalOn(today), "no goals at all is the one honest null")
    }

    // SPEC: GOAL-03
    @Test
    fun `lowering a target today does not re-score the weeks before it`() = runTest {
        val plan = FakeMealPlanRepository(FakeTimeSignal(TEST_NOW), TEST_ZONE)
        val todayRepo = FakeTodayRepository()
        // Cut from 2600 to 2400 a week ago — the exact scenario the brief opens with.
        val changedOn = LocalDate(2026, 7, 15)
        todayRepo.goals = listOf(
            DatedGoal(LocalDate(1, 1, 1), 2600, 190),
            DatedGoal(changedOn, 2400, 180),
        )

        val getPlanDay = GetPlanDayUseCase(plan, time = FakeTimeSignal(TEST_NOW), zone = TEST_ZONE)
        val history = GetHistoryUseCase(
            getPlanDay = getPlanDay,
            getTodaySummary = GetTodaySummaryUseCase(todayRepo),
            goalHistory = ObserveGoalHistoryUseCase(todayRepo),
        )

        val result = history().first()
        val days = (result as AppResult.Success).value.days

        val before = days.single { it.date == LocalDate(2026, 7, 14) }
        assertEquals(2600, before.targetKcal, "the fortnight you were deliberately eating 2600")
        assertEquals(190, before.proteinGoalG)

        val after = days.single { it.date == changedOn }
        assertEquals(2400, after.targetKcal, "the day the cut took effect")
        assertEquals(180, after.proteinGoalG)
        assertEquals(2400, days.last().targetKcal, "and today")

        // The trend rows inherit it, since a week's target is its last day's (WeekTrend).
        val trend = (result).value.weeks
        assertEquals(2600, trend.first().targetKcal, "an early week keeps the target it was judged against")
        assertEquals(2400, trend.last().targetKcal)
    }

    // SPEC: GOAL-02
    // SPEC: GOAL-04
    @Test
    fun `editing writes today's row, replaces it on a second edit, and leaves earlier days alone`() =
        runTest {
            val repo = FakeTodayRepository()
            val original = DatedGoal(LocalDate(2026, 7, 1), 2600, 190)
            repo.goals = listOf(original)

            repo.updateGoals(2400, 180)
            repo.updateGoals(2450, 185)

            val goals = (repo.observeGoalHistory().first() as AppResult.Success).value
            assertEquals(2, goals.size, "two edits in one day are ONE row — a target changes on a day, not at 09:14")
            assertEquals(original, goals.single { it.effectiveFrom == LocalDate(2026, 7, 1) }, "history is untouched")
            assertEquals(2450, goals.goalOn(today)?.targetKcal, "and today reads the correction")
            assertEquals(2600, goals.goalOn(LocalDate(2026, 7, 10))?.targetKcal, "a past day still reads its own")
        }
}
