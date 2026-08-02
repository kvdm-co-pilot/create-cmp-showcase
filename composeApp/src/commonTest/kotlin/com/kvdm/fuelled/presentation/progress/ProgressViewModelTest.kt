package com.kvdm.fuelled.presentation.progress

import com.kvdm.fuelled.domain.model.LogEntry
import com.kvdm.fuelled.domain.model.LogStatus
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.WEEK_REVIEW_DAYS
import com.kvdm.fuelled.domain.usecase.GetPlanDayUseCase
import com.kvdm.fuelled.domain.usecase.GetTodaySummaryUseCase
import com.kvdm.fuelled.domain.usecase.GetHistoryUseCase
import com.kvdm.fuelled.domain.usecase.ObserveAppStateUseCase
import com.kvdm.fuelled.domain.usecase.ObserveGoalHistoryUseCase
import com.kvdm.fuelled.domain.usecase.ObserveWeightLogUseCase
import com.kvdm.fuelled.domain.usecase.RecordWeightUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.testing.TEST_NOW
import com.kvdm.fuelled.testing.TEST_ZONE
import com.kvdm.fuelled.testing.fakes.FakeAppStateRepository
import com.kvdm.fuelled.testing.fakes.FakeMealPlanRepository
import com.kvdm.fuelled.testing.fakes.FakeWeightRepository
import com.kvdm.fuelled.testing.fakes.FakeTimeSignal
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import com.kvdm.fuelled.testing.keepCollecting
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

/**
 * The Progress surface (JRN-01, HIST-01) — the week section derived through the SAME plan-day
 * derivation and observed reads every other surface uses: seven rows, ascending, today last;
 * LOGGED-only totals; a day with nothing logged is zeros, never an error. HIST-05..08 cover
 * the trend and the weight built on the same stream.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val planRepository = FakeMealPlanRepository(FakeTimeSignal(TEST_NOW), TEST_ZONE)
    private val todayRepository = FakeTodayRepository()
    private val today = LocalDate(2026, 7, 22)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val weightRepository = FakeWeightRepository()
    private val appState = FakeAppStateRepository()

    private fun getPlanDay() =
        GetPlanDayUseCase(planRepository, time = FakeTimeSignal(TEST_NOW), zone = TEST_ZONE)

    private fun viewModel() = ProgressViewModel(
        getHistory = GetHistoryUseCase(
            getPlanDay = getPlanDay(),
            getTodaySummary = GetTodaySummaryUseCase(todayRepository),
            goalHistory = ObserveGoalHistoryUseCase(todayRepository),
        ),
        observeWeight = ObserveWeightLogUseCase(weightRepository, getPlanDay()),
        observeAppState = ObserveAppStateUseCase(appState),
        recordWeight = RecordWeightUseCase(weightRepository, getPlanDay()),
    )

    // SPEC: JRN-01
    @Test
    fun `seven logical days ascending, today last and marked - LOGGED-only totals, empty days are zeros`() =
        runTest(dispatcher) {
            // Two days back: a real logged day with one PLANNED straggler that must not count.
            planRepository.entries[LocalDate(2026, 7, 20)] = mapOf(
                MealSlot.LUNCH to listOf(
                    LogEntry("a", "Chicken & rice", "200 g", 620, 58, status = LogStatus.LOGGED),
                    LogEntry("b", "Planned extra", "1", 999, 99, status = LogStatus.PLANNED),
                ),
            )
            planRepository.doneSlots[LocalDate(2026, 7, 20)] = mutableSetOf(MealSlot.LUNCH, MealSlot.BREAKFAST)
            planRepository.waterTicks[LocalDate(2026, 7, 20)] = mutableSetOf(1, 2, 3)

            val vm = viewModel()
            keepCollecting(vm.state)
            advanceUntilIdle()

            val week = assertIs<ContentUiState.Content<ProgressUi>>(vm.state.value).data.history.week
            assertEquals(WEEK_REVIEW_DAYS, week.days.size)
            assertEquals(LocalDate(2026, 7, 16), week.days.first().date, "window starts six days back")
            assertEquals(today, week.days.last().date, "today is the last row")
            assertEquals(true, week.days.last().isToday, "and it is marked")
            assertEquals(listOf(true), week.days.filter { it.isToday }.map { it.isToday }, "exactly one today")

            val monday = week.days.single { it.date == LocalDate(2026, 7, 20) }
            assertEquals(620, monday.consumedKcal, "PLANNED entries never count as consumed (TODAY-03)")
            assertEquals(58, monday.proteinG)
            assertEquals(2, monday.slotsDone)
            assertEquals(1500, monday.waterMl)

            val emptyDay = week.days.single { it.date == LocalDate(2026, 7, 17) }
            assertEquals(0, emptyDay.consumedKcal, "a day with nothing logged is zeros, not an error")
            assertEquals(0, emptyDay.slotsDone)

            // Targets are the CURRENT goals, on every row (the S1 caveat, stated by contract).
            assertEquals(week.days.map { it.targetKcal }.toSet().size, 1)
            assertEquals(todayRepository.summary.targetKcal, week.days.first().targetKcal)
        }

    // SPEC: JRN-01
    @Test
    fun `a write anywhere re-renders the week without a reload - the state is observed`() =
        runTest(dispatcher) {
            val vm = viewModel()
            keepCollecting(vm.state)
            advanceUntilIdle()
            val before = assertIs<ContentUiState.Content<ProgressUi>>(vm.state.value).data.history.week
            assertEquals(0, before.days.last().slotsDone)

            // The same write path every surface uses; the fake re-emits like Room would (RS-01).
            planRepository.setSlotDone(today, MealSlot.BREAKFAST, true)
            advanceUntilIdle()

            val after = assertIs<ContentUiState.Content<ProgressUi>>(vm.state.value).data.history.week
            assertEquals(1, after.days.last().slotsDone, "the tick reached the week with no reload call")
        }
}
