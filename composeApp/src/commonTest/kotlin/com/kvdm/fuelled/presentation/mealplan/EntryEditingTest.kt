package com.kvdm.fuelled.presentation.mealplan

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.usecase.ArmMealRemindersUseCase
import com.kvdm.fuelled.domain.usecase.CopyDayForwardUseCase
import com.kvdm.fuelled.domain.usecase.DeleteLogEntryUseCase
import com.kvdm.fuelled.domain.usecase.GetPlanDayUseCase
import com.kvdm.fuelled.domain.usecase.RestoreLogEntryUseCase
import com.kvdm.fuelled.domain.usecase.SetEntryServingsUseCase
import com.kvdm.fuelled.domain.usecase.SetSlotDoneUseCase
import com.kvdm.fuelled.domain.usecase.SetWaterDoneUseCase
import com.kvdm.fuelled.testing.TEST_NOW
import com.kvdm.fuelled.testing.TEST_ZONE
import com.kvdm.fuelled.testing.fakes.FakeAppStateRepository
import com.kvdm.fuelled.testing.fakes.FakeMealPlanRepository
import com.kvdm.fuelled.testing.fakes.FakeReminderScheduler
import com.kvdm.fuelled.testing.fakes.FakeTimeSignal
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import com.kvdm.fuelled.testing.keepCollecting
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import com.kvdm.fuelled.domain.usecase.TomorrowUnplannedUseCase

/**
 * ENTRY-01/ENTRY-02 — correcting the log where it is read: the in-place serving stepper and
 * the undo behind a removal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntryEditingTest {

    private val dispatcher = StandardTestDispatcher()
    private val plan = FakeMealPlanRepository(FakeTimeSignal(TEST_NOW), TEST_ZONE)
    private val today = FakeTodayRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = MealPlanViewModel(
        initialDate = LocalDate(2026, 7, 22),
        getPlanDay = GetPlanDayUseCase(plan, time = FakeTimeSignal(TEST_NOW), zone = TEST_ZONE),
        setSlotDone = SetSlotDoneUseCase(plan),
        setWaterDone = SetWaterDoneUseCase(plan),
        copyDayForward = CopyDayForwardUseCase(plan),
        armReminders = ArmMealRemindersUseCase(plan, FakeReminderScheduler(), FakeAppStateRepository(), TomorrowUnplannedUseCase(plan, FakeTimeSignal(TEST_NOW), TEST_ZONE)),
        deleteLogEntry = DeleteLogEntryUseCase(today),
        setEntryServings = SetEntryServingsUseCase(today),
        restoreLogEntry = RestoreLogEntryUseCase(today),
    )

    // SPEC: ENTRY-01
    @Test
    fun `stepping an entry's servings writes the new multiple through the one path`() =
        runTest(dispatcher) {
            val vm = viewModel()
            keepCollecting(vm.state)

            vm.setServings("l1", 2)
            advanceUntilIdle()

            assertEquals(listOf("l1" to 2), today.servingEdits)
            assertEquals(false, vm.writeFailed.value)
        }

    // SPEC: ENTRY-01
    @Test
    fun `stepping below one serving is a removal, not a zero-serving row`() = runTest(dispatcher) {
        val vm = viewModel()
        keepCollecting(vm.state)

        vm.setServings("l1", 0)
        advanceUntilIdle()

        assertEquals(0, today.servingEdits.size, "no zero-serving write is attempted")
        assertEquals(listOf("l1"), today.deletedIds, "it becomes the ordinary removal")
    }

    // SPEC: ENTRY-02
    @Test
    fun `a removal offers an undo naming what went, and restores it exactly`() =
        runTest(dispatcher) {
            val vm = viewModel()
            keepCollecting(vm.state)

            vm.deleteEntry("l1")
            advanceUntilIdle()
            assertEquals("Removed food", vm.lastDeleted.value?.name, "the bar can name what it lost")

            vm.undoDelete()
            advanceUntilIdle()

            assertEquals(listOf("l1"), today.restoredIds, "put back through the one write path")
            assertNull(vm.lastDeleted.value, "and the bar retires once it has done its job")
        }

    // SPEC: ENTRY-02
    @Test
    fun `a failed undo keeps the bar - the offer stands until it actually works`() =
        runTest(dispatcher) {
            val vm = viewModel()
            keepCollecting(vm.state)
            vm.deleteEntry("l1")
            advanceUntilIdle()

            today.failure = DomainError.Unexpected()
            vm.undoDelete()
            advanceUntilIdle()

            assertEquals(true, vm.writeFailed.value)
            assertEquals("l1", vm.lastDeleted.value?.id, "a failed restore must not silently drop the undo")
        }
}
