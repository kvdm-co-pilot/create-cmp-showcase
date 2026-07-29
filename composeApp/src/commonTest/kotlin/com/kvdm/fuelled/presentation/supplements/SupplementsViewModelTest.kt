package com.kvdm.fuelled.presentation.supplements

import app.cash.turbine.test
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.domain.usecase.GetSupplementStackUseCase
import com.kvdm.fuelled.domain.usecase.SetSupplementTakenUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.testing.fakes.FakeSupplementRepository
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
 * The Supplements ViewModel test — mirrors FoodsViewModelTest/TodayViewModelTest: Turbine over
 * the [ContentUiState] machine, hand-written fakes (never mocks), a [StandardTestDispatcher]
 * installed as Main so viewModelScope launches run under virtual time. Each emission IS one
 * state — no boolean-flag poking.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SupplementsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeSupplementRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        SupplementsViewModel(GetSupplementStackUseCase(repository), SetSupplementTakenUseCase(repository))

    private val creatine = Supplement("1", "Creatine", "5 g", "Morning", taken = true)
    private val omega = Supplement("2", "Omega-3", "1 g", "Morning", taken = false)
    private val caffeine = Supplement("3", "Caffeine", "200 mg", "Pre-workout", taken = false)

    // SPEC: SUPP-01
    @Test
    fun `emits Loading then Content grouped by timing in order`() = runTest(dispatcher) {
        repository.stack = listOf(creatine, omega, caffeine)

        viewModel().state.test {
            assertEquals(ContentUiState.Loading, awaitItem(), "initial state should be Loading")
            val content = assertIs<ContentUiState.Content<SupplementStackUi>>(awaitItem())
            assertEquals(listOf("Morning", "Pre-workout"), content.data.groups.map { it.timing })
            assertEquals(listOf("Creatine", "Omega-3"), content.data.groups[0].items.map { it.name })
            assertEquals(listOf("Caffeine"), content.data.groups[1].items.map { it.name })
        }
    }

    // SPEC: SUPP-02
    @Test
    fun `Content carries the taken-of-total summary and progress`() = runTest(dispatcher) {
        repository.stack = listOf(creatine, omega, caffeine) // 1 of 3 taken

        viewModel().state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            val content = assertIs<ContentUiState.Content<SupplementStackUi>>(awaitItem())
            assertEquals(1, content.data.takenCount)
            assertEquals(3, content.data.total)
            assertEquals(1f / 3f, content.data.progress)
        }
    }

    // SPEC: SUPP-03
    @Test
    fun `toggling a supplement persists and updates the taken summary`() = runTest(dispatcher) {
        repository.stack = listOf(creatine, omega, caffeine) // 1 of 3 taken
        val viewModel = viewModel()
        keepCollecting(viewModel.state)

        viewModel.state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            val initial = assertIs<ContentUiState.Content<SupplementStackUi>>(awaitItem())
            assertEquals(1, initial.data.takenCount)

            viewModel.onToggleTaken("2", true) // take Omega-3

            val updated = assertIs<ContentUiState.Content<SupplementStackUi>>(awaitItem())
            assertEquals(2, updated.data.takenCount, "the summary reflects the persisted new state")
            assertEquals("2" to true, repository.lastSetTaken, "the toggle persisted through the repository")
        }
    }

    // SPEC: SUPP-04
    @Test
    fun `emits Empty when the stack is empty`() = runTest(dispatcher) {
        repository.stack = emptyList()

        viewModel().state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            assertEquals(ContentUiState.Empty, awaitItem())
        }
    }

    // SPEC: SUPP-05
    @Test
    fun `maps a typed failure to presentation copy - never a raw exception message`() = runTest(dispatcher) {
        repository.failure = DomainError.Network

        viewModel().state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            val failed = assertIs<ContentUiState.Error>(awaitItem())
            assertEquals(DomainError.Network.toUserMessage(), failed.message)
        }
    }

    // SPEC: SUPP-05, RS-01
    @Test
    fun `reload after failure clears the error and loads the stack`() = runTest(dispatcher) {
        repository.failure = DomainError.Network
        val viewModel = viewModel()
        keepCollecting(viewModel.state)

        viewModel.state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            assertIs<ContentUiState.Error>(awaitItem(), "first load should fail")

            // Recovery needs no retry button and no reload: clearing the failure re-emits, and
            // the observed state moves on its own. There is no second Loading — a recovery is
            // not a page load.
            repository.failure = null
            repository.stack = listOf(creatine)

            val recovered = assertIs<ContentUiState.Content<SupplementStackUi>>(awaitItem())
            assertEquals(1, recovered.data.total)
        }
    }
}
