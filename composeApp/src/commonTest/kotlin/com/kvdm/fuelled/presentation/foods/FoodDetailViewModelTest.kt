package com.kvdm.fuelled.presentation.foods

import app.cash.turbine.test
import com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.model.LogStatus
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.usecase.AddLogEntriesUseCase
import com.kvdm.fuelled.domain.usecase.GetFoodUseCase
import com.kvdm.fuelled.domain.usecase.SetFavouriteUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.testing.TEST_NOW
import com.kvdm.fuelled.testing.TEST_ZONE
import com.kvdm.fuelled.testing.fakes.FakeFoodRepository
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import com.kvdm.fuelled.testing.fakes.FixedClock
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
import com.kvdm.fuelled.testing.keepCollecting

/** The detail VM resolves one food by id through the repository — Content on hit, mapped copy on miss. */
@OptIn(ExperimentalCoroutinesApi::class)
class FoodDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeFoodRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val todayRepository = FakeTodayRepository()

    private fun viewModel() = FoodDetailViewModel(
        getFood = GetFoodUseCase(repository),
        addLogEntries = AddLogEntriesUseCase(
            todayRepository,
            FixedClock(TEST_NOW),
            TEST_ZONE,
            DEFAULT_DAY_START_HOUR,
        ),
        setFavourite = SetFavouriteUseCase(repository),
        clock = FixedClock(TEST_NOW),
        zone = TEST_ZONE,
        dayStartHour = DEFAULT_DAY_START_HOUR,
    )

    private val banana = Food("5", "Banana", "Medium", "1 · 118 g", 105, 1, 27, 0)

    // SPEC: FOODS-06
    @Test
    fun `resolves the food by id and emits Content`() = runTest(dispatcher) {
        repository.foods = listOf(banana)
        val viewModel = viewModel()
        keepCollecting(viewModel.state)

        viewModel.state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            // The VM starts in Loading; load() sets Loading again — StateFlow conflates the
            // duplicate, so the next distinct emission is Content.
            viewModel.load("5")
            assertEquals(ContentUiState.Content(banana), awaitItem())
        }
    }

    // SPEC: FOODS-07
    @Test
    fun `an unknown id maps NotFound to presentation copy`() = runTest(dispatcher) {
        repository.foods = listOf(banana)
        val viewModel = viewModel()
        keepCollecting(viewModel.state)

        viewModel.state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            viewModel.load("does-not-exist")
            val failed = assertIs<ContentUiState.Error>(awaitItem())
            assertEquals(DomainError.NotFound.toUserMessage(), failed.message)
        }
    }

    // SPEC: UX-03
    @Test
    fun `logging to a slot writes one LOGGED serving to the current logical day - the same write path as the tray`() =
        runTest(dispatcher) {
            repository.foods = listOf(banana)
            val viewModel = viewModel()
            keepCollecting(viewModel.state)
            viewModel.load("5")
            advanceUntilIdle()

            viewModel.log(MealSlot.AFTERNOON_SNACK)
            advanceUntilIdle()

            val call = todayRepository.addCalls.single()
            assertEquals(MealSlot.AFTERNOON_SNACK, call.slot, "the picked slot is the aim")
            assertEquals("2026-07-22", call.date.toString(), "the current logical day, never another")
            assertEquals(LogStatus.LOGGED, call.status, "today's write is LOGGED (MEAL-08)")
            assertEquals("Banana", call.entries.single().name)
            assertEquals(FoodLogState.Logged(MealSlot.AFTERNOON_SNACK), viewModel.logState.value)
        }

    // SPEC: UX-03
    @Test
    fun `a failed log surfaces mapped copy on the log channel - the food stays rendered`() =
        runTest(dispatcher) {
            repository.foods = listOf(banana)
            val viewModel = viewModel()
            keepCollecting(viewModel.state)
            viewModel.load("5")
            advanceUntilIdle()

            todayRepository.failure = DomainError.Unexpected()
            viewModel.log(MealSlot.LUNCH)
            advanceUntilIdle()

            assertIs<FoodLogState.Error>(viewModel.logState.value)
            assertIs<ContentUiState.Content<Food>>(viewModel.state.value, "the read state never carries the write's error")
        }

    // SPEC: UX-03
    @Test
    fun `logging before the food resolves reaches no write`() = runTest(dispatcher) {
        repository.foods = listOf(banana)
        val viewModel = viewModel()
        keepCollecting(viewModel.state)
        // No load() — the state is still Loading, so there is nothing to write.

        viewModel.log(MealSlot.LUNCH)
        advanceUntilIdle()

        assertEquals(0, todayRepository.addCalls.size, "an unresolved food must attempt no write")
    }
}
