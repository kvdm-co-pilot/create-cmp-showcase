package com.kvdm.fuelled.presentation.meal

import app.cash.turbine.test
import com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.model.LogStatus
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.usecase.AddLogEntriesUseCase
import com.kvdm.fuelled.domain.usecase.GetFoodsUseCase
import com.kvdm.fuelled.domain.usecase.SearchFoodsUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.presentation.foods.toUserMessage
import com.kvdm.fuelled.testing.fakes.FakeFoodRepository
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
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * The add-to-meal tray's ViewModel — MEAL-09 (the running total), MEAL-10 (retargeting), and
 * MEAL-11 (an empty tray attempts no write).
 *
 * Follows the exemplar [com.kvdm.fuelled.presentation.foods.FoodsViewModelTest]: a
 * [StandardTestDispatcher] as Main, Turbine for StateFlow assertions, hand-written fakes,
 * one behaviour per test. The clock is FIXED at 12:30 on a known day so the opening target is
 * deterministic — a test that read the wall clock would preselect a different slot after 15:00.
 *
 * MEAL-05's transaction/rollback semantics are NOT re-tested here; they are proven at
 * [AddLogEntriesUseCase], which this ViewModel calls rather than reimplements.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MealTrayViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone = TimeZone.UTC
    private val foodRepository = FakeFoodRepository()
    private val todayRepository = FakeTodayRepository()

    private val logicalToday = LocalDate(2026, 7, 22)
    private val tomorrow = LocalDate(2026, 7, 23)
    private val openedAt = LocalDateTime(2026, 7, 22, 12, 30)

    // Whole-number macros chosen so every assertion below is exact arithmetic, not a rounding.
    private val chicken = Food("1", "Chicken breast", "Raw", "100 g", 165, 31, 0, 4)
    private val oats = Food("2", "Rolled oats", "Quaker", "80 g", 303, 11, 54, 6)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        foodRepository.foods = listOf(chicken, oats)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): MealTrayViewModel {
        val clock = FixedClock(openedAt.toInstant(zone))
        return MealTrayViewModel(
            getFoods = GetFoodsUseCase(foodRepository),
            searchFoods = SearchFoodsUseCase(foodRepository),
            addLogEntries = AddLogEntriesUseCase(todayRepository, clock, zone, DEFAULT_DAY_START_HOUR),
            clock = clock,
            zone = zone,
            dayStartHour = DEFAULT_DAY_START_HOUR,
        )
    }

    @Test
    fun `loads the catalog through the foods use case`() = runTest(dispatcher) {
        viewModel().state.test {
            assertEquals(ContentUiState.Loading, awaitItem(), "initial state should be Loading")
            assertEquals(ContentUiState.Content(listOf(chicken, oats)), awaitItem())
        }
    }

    @Test
    fun `opens aimed at the current logical day and the slot for the hour`() = runTest(dispatcher) {
        val target = viewModel().target.value

        assertEquals(logicalToday, target.date)
        assertEquals(MealSlot.LUNCH, target.slot, "12:30 sits in the Lunch window")
        assertEquals(listOf(LocalDate(2026, 7, 21), logicalToday, tomorrow), target.dateOptions)
    }

    // SPEC: MEAL-09
    @Test
    fun `the running total carries calories and all three macros as items go in and out`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.tray.test {
                assertEquals(TrayTotal.Empty, awaitItem().total, "an untouched tray totals nothing")

                viewModel.onFoodToggled(chicken)
                assertEquals(
                    TrayTotal(items = 1, kcal = 165, proteinG = 31, carbsG = 0, fatG = 4),
                    awaitItem().total,
                )

                viewModel.onFoodToggled(oats)
                assertEquals(
                    TrayTotal(items = 2, kcal = 468, proteinG = 42, carbsG = 54, fatG = 10),
                    awaitItem().total,
                    "adding recomputes every macro, not just calories",
                )

                viewModel.onFoodToggled(chicken)
                assertEquals(
                    TrayTotal(items = 1, kcal = 303, proteinG = 11, carbsG = 54, fatG = 6),
                    awaitItem().total,
                    "removing recomputes it back down",
                )
            }
        }

    // SPEC: MEAL-09
    @Test
    fun `adjusting a serving recomputes the running total`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onFoodToggled(chicken)

        viewModel.onServingsChanged(chicken.id, servings = 3)

        assertEquals(
            TrayTotal(items = 1, kcal = 495, proteinG = 93, carbsG = 0, fatG = 12),
            viewModel.tray.value.total,
            "three servings is three times every macro",
        )
    }

    // SPEC: MEAL-10
    @Test
    fun `changing the slot retargets the same tray and keeps its contents`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onFoodToggled(chicken)
        viewModel.onFoodToggled(oats)
        val totalBefore = viewModel.tray.value.total

        viewModel.onSlotSelected(MealSlot.DINNER)

        assertEquals(MealSlot.DINNER, viewModel.target.value.slot)
        assertEquals(logicalToday, viewModel.target.value.date, "the date is untouched")
        assertEquals(listOf(chicken, oats), viewModel.tray.value.lines.map { it.food })
        assertEquals(totalBefore, viewModel.tray.value.total, "retargeting is not a reset")
    }

    // SPEC: MEAL-10
    @Test
    fun `changing the date retargets the same tray and keeps its contents`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onFoodToggled(chicken)
        val totalBefore = viewModel.tray.value.total

        viewModel.onDateSelected(tomorrow)

        assertEquals(tomorrow, viewModel.target.value.date)
        assertEquals(MealSlot.LUNCH, viewModel.target.value.slot, "the slot is untouched")
        assertEquals(listOf(chicken), viewModel.tray.value.lines.map { it.food })
        assertEquals(totalBefore, viewModel.tray.value.total, "retargeting is not a reset")
    }

    // SPEC: MEAL-10
    @Test
    fun `confirming after retargeting writes to the new date and slot - one flow, two targets`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.onFoodToggled(chicken)
            viewModel.onSlotSelected(MealSlot.DINNER)
            viewModel.onDateSelected(tomorrow)

            viewModel.confirm()
            advanceUntilIdle()

            val call = todayRepository.addCalls.single()
            assertEquals(tomorrow, call.date, "the tray wrote where its header pointed")
            assertEquals(MealSlot.DINNER, call.slot)
            assertEquals(LogStatus.PLANNED, call.status, "a future target is a plan (MEAL-08)")
            assertEquals(listOf("Chicken breast"), call.entries.map { it.name })
            assertEquals(TrayConfirmState.Saved, viewModel.confirmState.value)
        }

    // SPEC: MEAL-11
    @Test
    fun `an empty tray attempts no write at all`() = runTest(dispatcher) {
        val viewModel = viewModel()

        // Called DIRECTLY, bypassing the disabled Add control: a disabled button is a
        // rendering, and the clause says no write can be ATTEMPTED. The refusal has to hold
        // for every caller of the ViewModel, so this is the caller that proves it.
        viewModel.confirm()
        advanceUntilIdle()

        assertTrue(
            todayRepository.addCalls.isEmpty(),
            "the repository recorded a write attempt from an empty tray",
        )
        assertEquals(TrayConfirmState.Idle, viewModel.confirmState.value, "nothing was submitted")
        assertTrue(viewModel.tray.value.total.isEmpty, "the total the control reads is empty")
    }

    // SPEC: MEAL-11
    @Test
    fun `emptying a filled tray makes the write unreachable again`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onFoodToggled(chicken)
        viewModel.onFoodToggled(chicken)

        viewModel.confirm()
        advanceUntilIdle()

        assertTrue(
            todayRepository.addCalls.isEmpty(),
            "a tray emptied back out must not carry a stale write through",
        )
        assertEquals(TrayConfirmState.Idle, viewModel.confirmState.value)
    }

    @Test
    fun `a failed confirm surfaces mapped copy and keeps the tray`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onFoodToggled(chicken)
        todayRepository.failure = DomainError.Network

        viewModel.confirm()
        advanceUntilIdle()

        val failed = assertIs<TrayConfirmState.Error>(viewModel.confirmState.value)
        assertEquals(DomainError.Network.toUserMessage(), failed.message)
        assertEquals(listOf(chicken), viewModel.tray.value.lines.map { it.food }, "the tray survives a failure")
    }
}
