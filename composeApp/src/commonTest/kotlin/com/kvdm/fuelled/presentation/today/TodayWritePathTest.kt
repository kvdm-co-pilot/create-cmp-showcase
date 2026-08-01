package com.kvdm.fuelled.presentation.today

import app.cash.turbine.test
import com.kvdm.fuelled.domain.model.LogEntry
import com.kvdm.fuelled.domain.model.LogStatus
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.PlanDay
import com.kvdm.fuelled.domain.usecase.ArmMealRemindersUseCase
import com.kvdm.fuelled.domain.usecase.CopyDayForwardUseCase
import com.kvdm.fuelled.domain.usecase.DeleteLogEntryUseCase
import com.kvdm.fuelled.domain.usecase.RestoreLogEntryUseCase
import com.kvdm.fuelled.domain.usecase.SetEntryServingsUseCase
import com.kvdm.fuelled.domain.usecase.GetPlanDayUseCase
import com.kvdm.fuelled.domain.usecase.SetSlotDoneUseCase
import com.kvdm.fuelled.domain.usecase.SetWaterDoneUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.presentation.mealplan.MealPlanViewModel
import com.kvdm.fuelled.testing.TEST_NOW
import com.kvdm.fuelled.testing.TEST_ZONE
import com.kvdm.fuelled.testing.fakes.FakeAppStateRepository
import com.kvdm.fuelled.testing.fakes.FakeMealPlanRepository
import com.kvdm.fuelled.testing.fakes.FakeReminderScheduler
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import com.kvdm.fuelled.testing.fakes.FixedClock
import com.kvdm.fuelled.testing.todayViewModel
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
import com.kvdm.fuelled.testing.fakes.FakeTimeSignal
import com.kvdm.fuelled.testing.keepCollecting

/**
 * TODAY-13 — Today renders a projection of the plan and writes through the SAME use case.
 *
 * This is the one clause that cannot be proven by looking at either surface alone, so it gets
 * its own file: it drives the two ViewModels through the identical user act against separate
 * repositories, and asserts the resulting stored state is byte-identical. A second write path
 * would show up here as a difference — a tick that logged different entries, recorded a
 * different slot, or left the day's derived state disagreeing between the two screens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayWritePathTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate(2026, 7, 22)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** The same starting day, twice — one for each surface to write into. */
    private fun seededRepository() = FakeMealPlanRepository(FakeTimeSignal(TEST_NOW), TEST_ZONE).apply {
        entries[today] = mapOf(
            MealSlot.LUNCH to listOf(
                LogEntry("l1", "Chicken & rice", "200 g", 620, 58, status = LogStatus.PLANNED),
            ),
        )
    }

    private fun planViewModel(
        repo: FakeMealPlanRepository,
        todayRepo: FakeTodayRepository = FakeTodayRepository(),
    ) = MealPlanViewModel(
        initialDate = today,
        getPlanDay = GetPlanDayUseCase(repo, time = FakeTimeSignal(TEST_NOW), zone = TEST_ZONE),
        setSlotDone = SetSlotDoneUseCase(repo),
        setWaterDone = SetWaterDoneUseCase(repo),
        copyDayForward = CopyDayForwardUseCase(repo),
        armReminders = ArmMealRemindersUseCase(repo, FakeReminderScheduler(), FakeAppStateRepository()),
        deleteLogEntry = DeleteLogEntryUseCase(todayRepo),
            setEntryServings = SetEntryServingsUseCase(todayRepo),
            restoreLogEntry = RestoreLogEntryUseCase(todayRepo),
    )

    // SPEC: UX-02
    @Test
    fun `deleting from Today and deleting from the plan screen is the same delete`() =
        runTest(dispatcher) {
            // One delete path (MEAL-06), two surfaces (TODAY-13's discipline applied to
            // removal): both ViewModels must land the same id on the same repository call.
            val fromToday = FakeTodayRepository()
            todayViewModel(today = fromToday).also { advanceUntilIdle() }.deleteEntry("l1")
            advanceUntilIdle()

            val fromPlan = FakeTodayRepository()
            planViewModel(seededRepository(), fromPlan).also { advanceUntilIdle() }.deleteEntry("l1")
            advanceUntilIdle()

            assertEquals(fromToday.deletedIds, fromPlan.deletedIds, "one verb, not two write paths")
            assertEquals(listOf("l1"), fromToday.deletedIds)
        }

    // SPEC: TODAY-13
    @Test
    fun `ticking a meal from Today and from the plan screen store identical state`() =
        runTest(dispatcher) {
            val fromToday = seededRepository()
            val fromPlan = seededRepository()

            // At 12:45 lunch is the focused container on both surfaces — the same derivation.
            todayViewModel(today = FakeTodayRepository(), plan = fromToday)
                .also { advanceUntilIdle() }
                .setSlotDone(MealSlot.LUNCH, done = true)
            advanceUntilIdle()

            planViewModel(fromPlan)
                .also { advanceUntilIdle() }
                .setDone(MealSlot.LUNCH, done = true)
            advanceUntilIdle()

            // The same write reached the repository from both screens: same target, same verb.
            assertEquals(fromPlan.doneCalls, fromToday.doneCalls)
            assertEquals(fromPlan.doneSlots, fromToday.doneSlots)
            assertEquals(fromPlan.entries, fromToday.entries)
        }

    // SPEC: TODAY-13
    @Test
    fun `ticking water from Today and from the plan screen store identical state`() =
        runTest(dispatcher) {
            val fromToday = seededRepository()
            val fromPlan = seededRepository()

            todayViewModel(today = FakeTodayRepository(), plan = fromToday)
                .also { advanceUntilIdle() }
                .setWaterDone(index = 1, done = true)
            advanceUntilIdle()

            planViewModel(fromPlan)
                .also { advanceUntilIdle() }
                .setWater(index = 1, done = true)
            advanceUntilIdle()

            assertEquals(fromPlan.waterCalls, fromToday.waterCalls)
            assertEquals(fromPlan.waterTicks, fromToday.waterTicks)
        }

    // SPEC: TODAY-13
    @Test
    fun `after the same tick both surfaces derive the same day`() = runTest(dispatcher) {
        val repo = seededRepository()

        // ONE repository this time, written through Today, then READ by the plan screen. If
        // Today kept its own projection, the plan would still show lunch outstanding here.
        val todayVm = todayViewModel(today = FakeTodayRepository(), plan = repo)
        // WhileSubscribed emits nothing until something collects: without this, `state.value`
        // below would read the initial Loading forever, not the observed day.
        keepCollecting(todayVm.state)
        advanceUntilIdle()
        todayVm.setSlotDone(MealSlot.LUNCH, done = true)
        advanceUntilIdle()

        planViewModel(repo).state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            val planDay = assertIs<ContentUiState.Content<PlanDay>>(awaitItem()).data

            val todayDay = assertIs<ContentUiState.Content<TodayHighlights>>(todayVm.state.value).data.plan
            // Not "equivalent" — EQUAL. One derived state, read twice.
            assertEquals(planDay, todayDay)
            assertEquals(MealSlot.AFTERNOON_SNACK, planDay.focusedSlot?.slot)
        }
    }
}
