package com.kvdm.fuelled.presentation.mealplan

import app.cash.turbine.test
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.PlanDay
import com.kvdm.fuelled.domain.usecase.ArmMealRemindersUseCase
import com.kvdm.fuelled.domain.usecase.CopyDayForwardUseCase
import com.kvdm.fuelled.domain.usecase.DeleteLogEntryUseCase
import com.kvdm.fuelled.domain.usecase.GetPlanDayUseCase
import com.kvdm.fuelled.domain.usecase.SetSlotDoneUseCase
import com.kvdm.fuelled.domain.usecase.SetWaterDoneUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.presentation.today.toUserMessage
import com.kvdm.fuelled.testing.TEST_NOW
import com.kvdm.fuelled.testing.TEST_ZONE
import com.kvdm.fuelled.testing.fakes.FakeMealPlanRepository
import com.kvdm.fuelled.testing.fakes.FakeReminderScheduler
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import com.kvdm.fuelled.testing.fakes.FixedClock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import com.kvdm.fuelled.testing.fakes.FakeTimeSignal
import kotlinx.coroutines.flow.first
import com.kvdm.fuelled.testing.keepCollecting

/**
 * The plan screen's ViewModel — Turbine over [ContentUiState], hand-written fakes, a fixed
 * clock installed so focus and lateness are the test's choice rather than the wall clock's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MealPlanViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeMealPlanRepository(FakeTimeSignal(TEST_NOW), TEST_ZONE)
    private val todayRepository = FakeTodayRepository()
    private val scheduler = FakeReminderScheduler()
    private val today = LocalDate(2026, 7, 22)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(): MealPlanViewModel {
        val getPlanDay = GetPlanDayUseCase(repository, time = FakeTimeSignal(TEST_NOW), zone = TEST_ZONE)
        return MealPlanViewModel(
            initialDate = today,
            getPlanDay = getPlanDay,
            setSlotDone = SetSlotDoneUseCase(repository),
            setWaterDone = SetWaterDoneUseCase(repository),
            copyDayForward = CopyDayForwardUseCase(repository),
            armReminders = ArmMealRemindersUseCase(repository, scheduler),
            deleteLogEntry = DeleteLogEntryUseCase(todayRepository),
        )
    }

    // SPEC: UX-02
    @Test
    fun `deleting an entry goes through the one delete path - and a failure raises the write flag`() =
        runTest(dispatcher) {
            val vm = viewModel()
            keepCollecting(vm.state)

            vm.deleteEntry("l1")
            advanceUntilIdle()
            assertEquals(listOf("l1"), todayRepository.deletedIds, "the ledger's own delete (MEAL-06)")
            assertEquals(false, vm.writeFailed.value)

            todayRepository.failure = DomainError.Unexpected()
            vm.deleteEntry("l2")
            advanceUntilIdle()
            assertEquals(true, vm.writeFailed.value, "a failed delete is a write failure (RS-04), not a destroyed day")
        }

    // SPEC: PLAN-11
    @Test
    fun `the strip is yesterday, today and the next seven - nine days, today selected on open`() =
        runTest(dispatcher) {
            val vm = viewModel()
            keepCollecting(vm.state)

            assertEquals(9, vm.stripDays.value.size)
            assertEquals(LocalDate(2026, 7, 21), vm.stripDays.value.first(), "one leading day for back-filling")
            assertEquals(today, vm.stripDays.value[1])
            assertEquals(LocalDate(2026, 7, 29), vm.stripDays.value.last())
            assertEquals(today, vm.selectedDate.value, "opens on the current logical day")
        }

    // SPEC: PLAN-11
    @Test
    fun `selecting a day renders that day's containers`() = runTest(dispatcher) {
        val vm = viewModel()
        keepCollecting(vm.state)

        vm.state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            assertIs<ContentUiState.Content<PlanDay>>(awaitItem())

            vm.select(LocalDate(2026, 7, 25))
            // No Loading flash between days: selecting re-collects the OBSERVED stream, and
            // the state holds the previous day until the selected day's first emission
            // replaces it — switching a tab of the same screen is not a page load.
            val content = assertIs<ContentUiState.Content<PlanDay>>(awaitItem())
            assertEquals(LocalDate(2026, 7, 25), content.data.date)
            assertEquals(6, content.data.slots.size, "a day never opened is still a full day")
        }
    }

    // SPEC: PLAN-13, PLAN-07
    @Test
    fun `ticking a slot carries the write back on the stream and cancels that slot's reminder for today`() =
        runTest(dispatcher) {
            val vm = viewModel()
            keepCollecting(vm.state)

            vm.state.test {
                assertEquals(ContentUiState.Loading, awaitItem())
                val before = assertIs<ContentUiState.Content<PlanDay>>(awaitItem())
                assertEquals(MealSlot.LUNCH, before.data.focusedSlot?.slot, "12:45 → lunch is focused")

                vm.setDone(MealSlot.LUNCH, done = true)

                // No Loading flash: a tick is not a page load. The state goes straight to the
                // re-derived day, where focus has ALREADY moved on — the write's own emission
                // lands while the write call is still suspended (Room emits on commit; the
                // fake suspends the same way), which is what the re-arm below reads.
                val after = assertIs<ContentUiState.Content<PlanDay>>(awaitItem())
                assertTrue(after.data.slots.single { it.slot == MealSlot.LUNCH }.done)
                assertEquals(MealSlot.AFTERNOON_SNACK, after.data.focusedSlot?.slot)
            }

            // The emission precedes the write call's return (Room emits on commit), so let the
            // write coroutine finish its re-arm before asserting on the schedule.
            advanceUntilIdle()

            // A meal already eaten is never announced.
            assertTrue(scheduler.armed.none { it.key == "meal_LUNCH" })
            assertEquals(6, scheduler.armed.count { it.key.startsWith("water_") }, "water is untouched")
        }

    // SPEC: PLAN-10
    @Test
    fun `ticking water re-reads the day's litres`() = runTest(dispatcher) {
        val vm = viewModel()
        keepCollecting(vm.state)

        vm.state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            assertEquals(0, assertIs<ContentUiState.Content<PlanDay>>(awaitItem()).data.waterMl)

            vm.setWater(index = 1, done = true)
            assertEquals(500, assertIs<ContentUiState.Content<PlanDay>>(awaitItem()).data.waterMl)
        }
    }

    // SPEC: PLAN-20
    @Test
    fun `copy-forward copies the SELECTED day, not always today`() = runTest(dispatcher) {
        val vm = viewModel()
        keepCollecting(vm.state)

        vm.state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            assertIs<ContentUiState.Content<PlanDay>>(awaitItem())

            vm.select(LocalDate(2026, 7, 24))
            // No Loading flash between days (see `selecting a day renders that day's containers`).
            assertIs<ContentUiState.Content<PlanDay>>(awaitItem())

            vm.copyForward(days = 3)
            advanceUntilIdle()
            // No new emission, and that is CORRECT: a copy-forward writes to the days AFTER the
            // selected one, so the day on screen is byte-identical and StateFlow conflates it
            // away. The source day being visibly untouched is the clause's own guarantee
            // (PLAN-20), not a missed update.
            expectNoEvents()
        }

        // The copy targets the SELECTED day, not whatever day it happens to be — otherwise
        // "plan Thursday, then repeat it" would silently copy today instead.
        assertEquals(LocalDate(2026, 7, 24), repository.copyCalls.single().from)
    }

    // SPEC: RS-04
    @Test
    fun `a failed write surfaces on its own channel and leaves the rendered day standing`() =
        runTest(dispatcher) {
            val vm = viewModel()
            keepCollecting(vm.state)

            vm.state.test {
                assertEquals(ContentUiState.Loading, awaitItem())
                assertIs<ContentUiState.Content<PlanDay>>(awaitItem())

                repository.writeFailure = DomainError.Network
                vm.setWater(index = 1, done = true)
                advanceUntilIdle()

                // The read stream never carried the write's error: no emission, and the day on
                // screen is exactly the one that was there before the failed tick.
                expectNoEvents()
            }

            assertTrue(vm.writeFailed.value, "the failure surfaced on its own channel")
            vm.clearWriteError()
            assertEquals(false, vm.writeFailed.value, "clearing the flag is explicit, not an emission side-effect")
        }

    // SPEC: PLAN-02
    @Test
    fun `a source failure becomes presentation copy, never a raw exception`() = runTest(dispatcher) {
        repository.failure = DomainError.Network

        viewModel().state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            assertEquals(DomainError.Network.toUserMessage(), assertIs<ContentUiState.Error>(awaitItem()).message)
        }
    }
}
