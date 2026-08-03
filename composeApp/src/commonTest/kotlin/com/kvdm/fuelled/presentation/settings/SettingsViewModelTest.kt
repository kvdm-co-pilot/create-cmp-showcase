package com.kvdm.fuelled.presentation.settings

import com.kvdm.fuelled.domain.model.PREP_LEAD_RANGE
import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.domain.model.SupplementTiming
import com.kvdm.fuelled.domain.model.UnitSystem
import com.kvdm.fuelled.domain.usecase.ArmMealRemindersUseCase
import com.kvdm.fuelled.domain.usecase.DeleteSupplementUseCase
import com.kvdm.fuelled.domain.usecase.GetSupplementStackUseCase
import com.kvdm.fuelled.domain.usecase.ObserveAppStateUseCase
import com.kvdm.fuelled.domain.usecase.SaveSupplementUseCase
import com.kvdm.fuelled.domain.usecase.SetPrepLeadUseCase
import com.kvdm.fuelled.domain.usecase.SetUnitSystemUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.testing.TEST_NOW
import com.kvdm.fuelled.testing.TEST_ZONE
import com.kvdm.fuelled.testing.fakes.FakeAppStateRepository
import com.kvdm.fuelled.testing.fakes.FakeMealPlanRepository
import com.kvdm.fuelled.testing.fakes.FakeReminderScheduler
import com.kvdm.fuelled.testing.fakes.FakeSupplementRepository
import com.kvdm.fuelled.testing.fakes.FakeTimeSignal
import com.kvdm.fuelled.testing.keepCollecting
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
import com.kvdm.fuelled.domain.usecase.TomorrowUnplannedUseCase

/**
 * Settings (SET-01..08): the unit system, the user's supplement stack, and the reminder lead
 * — each written through the SAME use cases the rest of the app reads, never a private path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val appState = FakeAppStateRepository()
    private val supplements = FakeSupplementRepository()
    private val plan = FakeMealPlanRepository(FakeTimeSignal(TEST_NOW), TEST_ZONE)
    private val scheduler = FakeReminderScheduler()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SettingsViewModel(
        observeAppState = ObserveAppStateUseCase(appState),
        getStack = GetSupplementStackUseCase(supplements),
        setUnitSystem = SetUnitSystemUseCase(appState),
        setPrepLead = SetPrepLeadUseCase(appState, ArmMealRemindersUseCase(plan, scheduler, appState, TomorrowUnplannedUseCase(plan, FakeTimeSignal(TEST_NOW), TEST_ZONE))),
        saveSupplement = SaveSupplementUseCase(supplements),
        deleteSupplement = DeleteSupplementUseCase(supplements),
    )

    private fun content(vm: SettingsViewModel): SettingsUi =
        assertIs<ContentUiState.Content<SettingsUi>>(vm.state.value).data

    // SPEC: SET-02
    @Test
    fun `choosing a unit system stores it and the observed state re-derives with no reload`() =
        runTest(dispatcher) {
            val vm = viewModel()
            keepCollecting(vm.state)
            advanceUntilIdle()
            assertEquals(UnitSystem.METRIC, content(vm).settings.unitSystem)

            vm.onUnitSystem(UnitSystem.IMPERIAL)
            advanceUntilIdle()

            assertEquals(UnitSystem.IMPERIAL, content(vm).settings.unitSystem, "no reload was called")
            assertEquals(UnitSystem.IMPERIAL, appState.settings.unitSystem, "and it reached the one row")
        }

    // SPEC: SET-04
    @Test
    fun `adding a supplement joins the stack, and a blank name or dose reaches no write`() =
        runTest(dispatcher) {
            val vm = viewModel()
            keepCollecting(vm.state)
            advanceUntilIdle()

            vm.onSaveSupplement("s-9", "Zinc", "25 mg", SupplementTiming.EVENING)
            advanceUntilIdle()
            assertEquals(1, supplements.saves.size)
            assertEquals("Zinc", supplements.saves.single().name)
            assertEquals(SupplementTiming.EVENING, supplements.saves.single().timing)
            assertTrue(content(vm).stack.any { it.name == "Zinc" }, "it is in the stack, observed")

            vm.onSaveSupplement("s-10", "   ", "25 mg", SupplementTiming.MORNING)
            vm.onSaveSupplement("s-11", "Zinc", "", SupplementTiming.MORNING)
            advanceUntilIdle()
            assertEquals(1, supplements.saves.size, "a nameless or doseless supplement is refused before the write")
        }

    // SPEC: SET-05
    @Test
    fun `editing corrects the same row rather than adding a twin, and removing drops it`() =
        runTest(dispatcher) {
            supplements.stack = listOf(Supplement("1", "Creatine", "5 g", SupplementTiming.MORNING, taken = true))
            val vm = viewModel()
            keepCollecting(vm.state)
            advanceUntilIdle()

            vm.onSaveSupplement("1", "Creatine mono", "10 g", SupplementTiming.PRE_WORKOUT)
            advanceUntilIdle()
            assertEquals(1, content(vm).stack.size, "a re-save of the same id corrects, never twins")
            assertEquals("Creatine mono", content(vm).stack.single().name)
            assertEquals(SupplementTiming.PRE_WORKOUT, content(vm).stack.single().timing)
            assertTrue(content(vm).stack.single().taken, "the day's dose is a fact about the day, not the edit")

            vm.onDeleteSupplement("1")
            advanceUntilIdle()
            assertEquals(listOf("1"), supplements.deletes)
            assertTrue(content(vm).stack.isEmpty())
        }

    // SPEC: SET-07
    // SPEC: SET-08
    @Test
    fun `the prep lead is stored, re-arms every reminder at once, and refuses an out-of-range value`() =
        runTest(dispatcher) {
            val vm = viewModel()
            keepCollecting(vm.state)
            advanceUntilIdle()
            val armedBefore = scheduler.armed.size

            vm.onPrepLead(60)
            advanceUntilIdle()

            assertEquals(60, appState.settings.prepLeadMinutes)
            assertEquals(60, content(vm).settings.prepLeadMinutes)
            // SET-08: not tomorrow. A lead that waits for the next arming pass is a setting
            // that looks broken on the evening you change it.
            assertTrue(scheduler.armed.size > armedBefore, "the change re-armed on the spot")

            val outOfRange = PREP_LEAD_RANGE.last + 1
            vm.onPrepLead(outOfRange)
            advanceUntilIdle()
            assertEquals(60, appState.settings.prepLeadMinutes, "an out-of-range lead is refused, not clamped")
            assertTrue(outOfRange in appState.prepLeadCalls, "and the attempt is observable")
        }
}
