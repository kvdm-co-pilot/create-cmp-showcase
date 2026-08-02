package com.kvdm.fuelled.presentation.progress

import com.kvdm.fuelled.domain.model.LogEntry
import com.kvdm.fuelled.domain.model.LogStatus
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.TREND_DAYS
import com.kvdm.fuelled.domain.model.TREND_WEEKS
import com.kvdm.fuelled.domain.model.UnitSystem
import com.kvdm.fuelled.domain.model.WEEK_REVIEW_DAYS
import com.kvdm.fuelled.domain.model.WeightEntry
import com.kvdm.fuelled.domain.model.weightFromKg
import com.kvdm.fuelled.domain.usecase.GetHistoryUseCase
import com.kvdm.fuelled.domain.usecase.GetPlanDayUseCase
import com.kvdm.fuelled.domain.usecase.GetTodaySummaryUseCase
import com.kvdm.fuelled.domain.usecase.ObserveAppStateUseCase
import com.kvdm.fuelled.domain.usecase.ObserveGoalHistoryUseCase
import com.kvdm.fuelled.domain.usecase.ObserveWeightLogUseCase
import com.kvdm.fuelled.domain.usecase.RecordWeightUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.testing.TEST_NOW
import com.kvdm.fuelled.testing.TEST_ZONE
import com.kvdm.fuelled.testing.fakes.FakeAppStateRepository
import com.kvdm.fuelled.testing.fakes.FakeMealPlanRepository
import com.kvdm.fuelled.testing.fakes.FakeTimeSignal
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import com.kvdm.fuelled.testing.fakes.FakeWeightRepository
import com.kvdm.fuelled.testing.keepCollecting
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

/**
 * The look back beyond a week (HIST-01, HIST-05..08) — the trend and the outcome variable,
 * both folded off the SAME observed stream the day cards read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val planRepository = FakeMealPlanRepository(FakeTimeSignal(TEST_NOW), TEST_ZONE)
    private val todayRepository = FakeTodayRepository()
    private val weightRepository = FakeWeightRepository()
    private val appState = FakeAppStateRepository()
    private val today = LocalDate(2026, 7, 22)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun getPlanDay() =
        GetPlanDayUseCase(planRepository, time = FakeTimeSignal(TEST_NOW), zone = TEST_ZONE)

    private fun viewModel() = ProgressViewModel(
        getHistory = GetHistoryUseCase(getPlanDay(), GetTodaySummaryUseCase(todayRepository), ObserveGoalHistoryUseCase(todayRepository)),
        observeWeight = ObserveWeightLogUseCase(weightRepository, getPlanDay()),
        observeAppState = ObserveAppStateUseCase(appState),
        recordWeight = RecordWeightUseCase(weightRepository, getPlanDay()),
    )

    private fun content(vm: ProgressViewModel): ProgressUi =
        assertIs<ContentUiState.Content<ProgressUi>>(vm.state.value).data

    // SPEC: HIST-01
    @Test
    fun `the window is four weeks and the seven-day verdict is a projection of the same stream`() =
        runTest(dispatcher) {
            planRepository.entries[LocalDate(2026, 7, 20)] = mapOf(
                MealSlot.LUNCH to listOf(
                    LogEntry("a", "Chicken & rice", "200 g", 620, 58, status = LogStatus.LOGGED),
                ),
            )

            val vm = viewModel()
            keepCollecting(vm.state)
            advanceUntilIdle()
            val history = content(vm).history

            assertEquals(TREND_DAYS, history.days.size)
            assertEquals(today, history.days.last().date, "the window ends on the current logical day")
            assertEquals(WEEK_REVIEW_DAYS, history.week.days.size)
            // The point of the shape: the verdict is a TAIL of the same list, not a second
            // query. A separate aggregate could disagree with the day cards; this cannot.
            assertEquals(history.days.takeLast(WEEK_REVIEW_DAYS), history.week.days)
            assertEquals(TREND_WEEKS, history.weeks.size)
            assertEquals(history.days.first().date, history.weeks.first().start, "oldest week first")
            assertEquals(today, history.weeks.last().end, "the current week last")
        }

    // SPEC: HIST-05
    @Test
    fun `a week with nothing logged has no data - it is never a zero-valued bar`() =
        runTest(dispatcher) {
            // Only the most recent week has anything in it.
            planRepository.entries[today] = mapOf(
                MealSlot.LUNCH to listOf(
                    LogEntry("a", "Chicken & rice", "200 g", 620, 58, status = LogStatus.LOGGED),
                ),
            )

            val vm = viewModel()
            keepCollecting(vm.state)
            advanceUntilIdle()
            val weeks = content(vm).history.weeks

            assertTrue(weeks.first().days.all { it.consumedKcal == 0 })
            assertEquals(false, weeks.first().hasData, "a week the app was not installed for reports nothing")
            assertEquals(true, weeks.last().hasData)
            assertEquals(620, weeks.last().avgConsumedKcal, "averaged over STARTED days only")
        }

    // SPEC: HIST-06
    @Test
    fun `a second weigh-in on the same logical day corrects the first rather than appending`() =
        runTest(dispatcher) {
            val vm = viewModel()
            keepCollecting(vm.state)
            advanceUntilIdle()

            vm.onWeightRecorded(83.4)
            advanceUntilIdle()
            vm.onWeightRecorded(83.1)
            advanceUntilIdle()

            val entries = content(vm).weight.entries
            assertEquals(1, entries.size, "one row per logical day — the primary key, not a convention")
            assertEquals(83.1, entries.single().kg)
            assertEquals(today, entries.single().date, "the day is derived at the write (MEAL-02)")
            // And the observed read carried it back with no reload.
            assertEquals(83.1, content(vm).weight.latest?.kg)
        }

    // SPEC: HIST-07
    @Test
    fun `with nothing recorded the log states nothing and invents no zero`() = runTest(dispatcher) {
        val vm = viewModel()
        keepCollecting(vm.state)
        advanceUntilIdle()

        assertNull(content(vm).weight.latest, "absence, not a zero reading")
        assertNull(content(vm).weight.change)

        // A non-positive or implausible weight never reaches the store either.
        vm.onWeightRecorded(0.0)
        vm.onWeightRecorded(-5.0)
        vm.onWeightRecorded(9000.0)
        advanceUntilIdle()
        assertTrue(weightRepository.recorded.isEmpty(), "guarded before the write")
    }

    // SPEC: HIST-08
    @Test
    fun `change is stated across the window only once there are two readings, and follows the unit`() =
        runTest(dispatcher) {
            weightRepository.entries = listOf(WeightEntry(LocalDate(2026, 7, 1), 84.2))
            val vm = viewModel()
            keepCollecting(vm.state)
            advanceUntilIdle()
            assertNull(content(vm).weight.change, "one reading makes no claim about change")

            weightRepository.entries = weightRepository.entries + WeightEntry(today, 82.8)
            advanceUntilIdle()

            val change = content(vm).weight.change
            assertTrue(change != null && change < 0, "a loss reads as a signed change: ${'$'}change")
            assertEquals(-1.4, kotlin.math.round(change!! * 10) / 10)

            // SET-02: the STORE is always kilograms; only the display follows the unit.
            assertEquals(-1.4, kotlin.math.round(UnitSystem.METRIC.weightFromKg(change) * 10) / 10)
            assertEquals(-3.1, kotlin.math.round(UnitSystem.IMPERIAL.weightFromKg(change) * 10) / 10)
            assertEquals(82.8, weightRepository.entries.last().kg, "stored in kg regardless")
        }
}
