package com.kvdm.fuelled.presentation.foods

import app.cash.turbine.test
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.usecase.GetFoodUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.testing.fakes.FakeFoodRepository
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

    private fun viewModel() = FoodDetailViewModel(GetFoodUseCase(repository))

    private val banana = Food("5", "Banana", "Medium", "1 · 118 g", 105, 1, 27, 0)

    // SPEC: FOODS-06
    @Test
    fun `resolves the food by id and emits Content`() = runTest(dispatcher) {
        repository.foods = listOf(banana)
        val viewModel = viewModel()

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

        viewModel.state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            viewModel.load("does-not-exist")
            val failed = assertIs<ContentUiState.Error>(awaitItem())
            assertEquals(DomainError.NotFound.toUserMessage(), failed.message)
        }
    }
}
