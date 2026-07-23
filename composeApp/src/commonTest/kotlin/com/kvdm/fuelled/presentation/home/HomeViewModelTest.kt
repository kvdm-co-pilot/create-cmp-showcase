package com.kvdm.fuelled.presentation.home

import app.cash.turbine.test
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Item
import com.kvdm.fuelled.domain.usecase.GetItemsUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.testing.fakes.FakeItemRepository
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

/**
 * The exemplar ViewModel test — the pattern every generated ViewModel test follows:
 *  - Arrange/Act/Assert with behavior-named backtick tests, one behavior per test.
 *  - A [StandardTestDispatcher] installed as Main (viewModelScope launches on Main),
 *    so coroutines run under the test scheduler's virtual time.
 *  - Turbine (`state.test { … }`) for StateFlow assertions.
 *  - Hand-written fakes from `testing/fakes` — never mocks.
 *  - Sealed-state assertions: each emission IS one state (`assertEquals` on the state,
 *    `assertIs` on the branch) — no boolean-flag poking. The state type is the shared
 *    [ContentUiState] (the generalization of this feature's pre-generalization per-feature
 *    sealed state).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeItemRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = HomeViewModel(GetItemsUseCase(repository))

    // SPEC: HOME-01
    @Test
    fun `starts in loading state`() = runTest(dispatcher) {
        repository.items = listOf(Item(id = "1", title = "First", subtitle = "sub"))

        viewModel().state.test {
            assertEquals(ContentUiState.Loading, awaitItem(), "initial state should be Loading")
        }
    }

    // SPEC: HOME-02
    @Test
    fun `emits Content when the repository returns items`() = runTest(dispatcher) {
        val items = listOf(Item(id = "1", title = "First", subtitle = "sub"))
        repository.items = items

        viewModel().state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            assertEquals(ContentUiState.Content(items), awaitItem())
        }
    }

    // SPEC: HOME-07
    @Test
    fun `emits Empty when the repository succeeds with no items`() = runTest(dispatcher) {
        repository.items = emptyList()

        viewModel().state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            assertEquals(ContentUiState.Empty, awaitItem())
        }
    }

    // SPEC: HOME-03
    @Test
    fun `maps a typed failure to presentation copy - never a raw exception message`() = runTest(dispatcher) {
        repository.failure = DomainError.Network

        viewModel().state.test {
            assertEquals(ContentUiState.Loading, awaitItem())

            val failed = assertIs<ContentUiState.Error>(awaitItem())
            assertEquals(DomainError.Network.toUserMessage(), failed.message)
        }
    }

    // SPEC: HOME-04
    @Test
    fun `reload after failure clears the error and loads items`() = runTest(dispatcher) {
        repository.failure = DomainError.Network
        val viewModel = viewModel()

        viewModel.state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            assertIs<ContentUiState.Error>(awaitItem(), "first load should fail")

            repository.failure = null
            repository.items = listOf(Item(id = "1", title = "Recovered", subtitle = "sub"))
            viewModel.load()

            assertEquals(ContentUiState.Loading, awaitItem(), "reload should show loading again")
            val recovered = assertIs<ContentUiState.Content<List<Item>>>(awaitItem())
            assertEquals(listOf("Recovered"), recovered.data.map { it.title })
        }
    }
}
