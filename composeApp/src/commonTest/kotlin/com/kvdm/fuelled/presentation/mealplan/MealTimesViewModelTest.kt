package com.kvdm.fuelled.presentation.mealplan

import app.cash.turbine.test
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.ReminderCapability
import com.kvdm.fuelled.domain.model.ReminderMode
import com.kvdm.fuelled.domain.usecase.ArmMealRemindersUseCase
import com.kvdm.fuelled.domain.usecase.GetMealTimesUseCase
import com.kvdm.fuelled.domain.usecase.SetMealTimeUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.testing.fakes.FakeAppStateRepository
import com.kvdm.fuelled.testing.fakes.FakeMealPlanRepository
import com.kvdm.fuelled.testing.fakes.FakeReminderScheduler
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalTime
import com.kvdm.fuelled.testing.fakes.FakeTimeSignal
import com.kvdm.fuelled.testing.TEST_ZONE
import com.kvdm.fuelled.testing.TEST_NOW
import com.kvdm.fuelled.testing.keepCollecting

/** The meal-times sheet's ViewModel (PLAN-05/PLAN-06/PLAN-07). */
@OptIn(ExperimentalCoroutinesApi::class)
class MealTimesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeMealPlanRepository(FakeTimeSignal(TEST_NOW), TEST_ZONE)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(scheduler: FakeReminderScheduler = FakeReminderScheduler()) =
        MealTimesViewModel(
            getMealTimes = GetMealTimesUseCase(repository),
            setMealTime = SetMealTimeUseCase(repository, ArmMealRemindersUseCase(repository, scheduler, FakeAppStateRepository())),
            armReminders = ArmMealRemindersUseCase(repository, scheduler, FakeAppStateRepository()),
            scheduler = scheduler,
        )

    // SPEC: PLAN-05
    @Test
    fun `an unset profile shows the body-for-life defaults, never a prompt`() = runTest(dispatcher) {
        viewModel().state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            val content = assertIs<ContentUiState.Content<MealTimesUiState>>(awaitItem())

            assertEquals(LocalTime(7, 0), content.data.times[MealSlot.BREAKFAST])
            assertEquals(LocalTime(19, 30), content.data.times[MealSlot.EVENING_SNACK])
            assertEquals(6, content.data.times.inSlotOrder().size, "no slot is ever left timeless")
        }
    }

    // SPEC: PLAN-06
    @Test
    fun `changing one slot moves that slot and shows where the write actually landed`() =
        runTest(dispatcher) {
            val vm = viewModel()
            keepCollecting(vm.state)

            vm.state.test {
                assertEquals(ContentUiState.Loading, awaitItem())
                assertIs<ContentUiState.Content<MealTimesUiState>>(awaitItem())

                vm.setTime(MealSlot.LUNCH, LocalTime(13, 15))
                val moved = assertIs<ContentUiState.Content<MealTimesUiState>>(awaitItem())
                assertEquals(LocalTime(13, 15), moved.data.times[MealSlot.LUNCH])
                assertEquals(LocalTime(7, 0), moved.data.times[MealSlot.BREAKFAST], "no other slot moved")

                // An out-of-range write is COERCED, and the sheet then shows the coerced value —
                // a control that silently discarded the change would teach you not to trust it.
                vm.setTime(MealSlot.DINNER, LocalTime(5, 0))
                val coerced = assertIs<ContentUiState.Content<MealTimesUiState>>(awaitItem())
                val ordered = coerced.data.times.inSlotOrder().map { it.second }
                assertEquals(ordered, ordered.sorted(), "the timetable stays ascending")
            }
        }

    // SPEC: PLAN-07
    @Test
    fun `the sheet states plainly when the platform will not deliver reminders`() = runTest(dispatcher) {
        val denied = FakeReminderScheduler(
            ReminderCapability(notificationsAllowed = false, exactAlarmsAllowed = false),
        )

        viewModel(denied).state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            val content = assertIs<ContentUiState.Content<MealTimesUiState>>(awaitItem())

            // The state carries the truth, so the screen can say it. Six rows of times with no
            // mode on the state would be six alarms the sheet silently implies but cannot fire.
            assertEquals(ReminderMode.UNAVAILABLE, content.data.reminderMode)
        }
    }

    // SPEC: PLAN-07
    @Test
    fun `opening the sheet re-arms - one of the moments the clause names`() = runTest(dispatcher) {
        val scheduler = FakeReminderScheduler()

        viewModel(scheduler).state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            assertIs<ContentUiState.Content<MealTimesUiState>>(awaitItem())
        }

        assertEquals(12, scheduler.armed.size, "six meals + six waters, re-armed on open")
    }
}
