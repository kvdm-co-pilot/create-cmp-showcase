package com.kvdm.fuelled.presentation.foods

import app.cash.turbine.test
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.usecase.GetFoodsUseCase
import com.kvdm.fuelled.domain.usecase.SearchFoodsUseCase
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
import com.kvdm.fuelled.testing.keepCollecting

/**
 * The exemplar ViewModel test — the pattern every generated ViewModel test follows:
 *  - Arrange/Act/Assert with behavior-named backtick tests, one behavior per test.
 *  - A [StandardTestDispatcher] installed as Main (viewModelScope launches on Main),
 *    so coroutines run under the test scheduler's virtual time.
 *  - Turbine (`state.test { … }`) for StateFlow assertions.
 *  - Hand-written fakes from `testing/fakes` — never mocks.
 *  - Sealed-state assertions: each emission IS one state — no boolean-flag poking. The state
 *    type is the shared [ContentUiState].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FoodsViewModelTest {

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

    private fun viewModel() =
        FoodsViewModel(GetFoodsUseCase(repository), SearchFoodsUseCase(repository))

    private val chicken = Food("1", "Chicken breast", "Raw", "100 g", 165, 31, 0, 4)
    private val oats = Food("2", "Rolled oats", "Quaker", "80 g", 303, 11, 54, 6)

    // SPEC: FOODS-01
    @Test
    fun `emits Loading then Content when the catalog loads`() = runTest(dispatcher) {
        repository.foods = listOf(chicken, oats)

        viewModel().state.test {
            assertEquals(ContentUiState.Loading, awaitItem(), "initial state should be Loading")
            assertEquals(ContentUiState.Content(listOf(chicken, oats)), awaitItem())
        }
    }

    // SPEC: FOODS-03
    @Test
    fun `emits Empty when the catalog is empty`() = runTest(dispatcher) {
        repository.foods = emptyList()

        viewModel().state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            assertEquals(ContentUiState.Empty, awaitItem())
        }
    }

    // SPEC: FOODS-02
    @Test
    fun `search filters the catalog through the use case and updates query state`() = runTest(dispatcher) {
        repository.foods = listOf(chicken, oats)
        val viewModel = viewModel()
        keepCollecting(viewModel.state)

        viewModel.state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            assertEquals(ContentUiState.Content(listOf(chicken, oats)), awaitItem())

            viewModel.onQueryChange("oat")

            assertEquals(ContentUiState.Loading, awaitItem(), "a new query reloads")
            assertEquals(ContentUiState.Content(listOf(oats)), awaitItem())
        }
        assertEquals("oat", viewModel.query.value, "query is VM state")
        assertEquals("oat", repository.lastQuery, "the filter ran at the repository, not the screen")
    }

    // SPEC: FOODS-03
    @Test
    fun `search with no matches emits Empty`() = runTest(dispatcher) {
        repository.foods = listOf(chicken, oats)
        val viewModel = viewModel()
        keepCollecting(viewModel.state)

        viewModel.state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            assertIs<ContentUiState.Content<List<Food>>>(awaitItem())

            viewModel.onQueryChange("zzz")

            assertEquals(ContentUiState.Loading, awaitItem())
            assertEquals(ContentUiState.Empty, awaitItem())
        }
    }

    // SPEC: FOODS-04
    @Test
    fun `maps a typed failure to presentation copy - never a raw exception message`() = runTest(dispatcher) {
        repository.failure = DomainError.Network

        viewModel().state.test {
            assertEquals(ContentUiState.Loading, awaitItem())

            val failed = assertIs<ContentUiState.Error>(awaitItem())
            assertEquals(DomainError.Network.toUserMessage(), failed.message)
        }
    }

    // SPEC: FOODS-04
    @Test
    fun `reload after failure clears the error and loads foods`() = runTest(dispatcher) {
        repository.failure = DomainError.Network
        val viewModel = viewModel()
        keepCollecting(viewModel.state)

        viewModel.state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            assertIs<ContentUiState.Error>(awaitItem(), "first load should fail")

            repository.failure = null
            repository.foods = listOf(chicken)
            viewModel.load()

            assertEquals(ContentUiState.Loading, awaitItem(), "reload should show loading again")
            val recovered = assertIs<ContentUiState.Content<List<Food>>>(awaitItem())
            assertEquals(listOf("Chicken breast"), recovered.data.map { it.name })
        }
    }
}
