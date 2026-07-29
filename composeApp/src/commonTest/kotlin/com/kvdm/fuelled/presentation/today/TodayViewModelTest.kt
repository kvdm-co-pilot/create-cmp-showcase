package com.kvdm.fuelled.presentation.today

import app.cash.turbine.test
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.testing.todayViewModel
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
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
import kotlinx.datetime.LocalDate
import com.kvdm.fuelled.testing.keepCollecting

/**
 * The Today ViewModel test — mirrors FoodsViewModelTest: Turbine over the [ContentUiState]
 * machine, hand-written fakes (never mocks), a [StandardTestDispatcher] installed as Main so
 * viewModelScope launches run under virtual time. A valid day (even with no entries) is
 * Content, so the empty-log case is a screen concern (see TodayScreenTest), not a VM arm.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeTodayRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = todayViewModel(today = repository)

    // SPEC: TODAY-01
    @Test
    fun `emits Loading then Content carrying the logical day's date`() = runTest(dispatcher) {
        val day = FakeTodayRepository.populatedDay
        repository.summary = day

        viewModel().state.test {
            assertEquals(ContentUiState.Loading, awaitItem(), "initial state should be Loading")
            val content = assertIs<ContentUiState.Content<TodayHighlights>>(awaitItem())
            // The VM hands the screen a real LocalDate; formatting it is the screen's job, and
            // the VM never invents a label of its own (TODAY-01).
            assertEquals(LocalDate(2026, 7, 22), content.data.today.date)
        }
    }

    // SPEC: TODAY-04
    @Test
    fun `a day with no entries is Content carrying the full target - not the dataless Empty arm`() =
        runTest(dispatcher) {
            repository.summary = FakeTodayRepository.emptyDay

            viewModel().state.test {
                assertEquals(ContentUiState.Loading, awaitItem())
                val content = assertIs<ContentUiState.Content<TodayHighlights>>(awaitItem())
                assertEquals(emptyList(), content.data.today.meals)
                assertEquals(2400, content.data.today.remainingKcal, "ring reads the full target as remaining")
            }
        }

    // SPEC: TODAY-05
    @Test
    fun `maps a typed failure to presentation copy - never a raw exception message`() = runTest(dispatcher) {
        repository.failure = DomainError.Network

        viewModel().state.test {
            assertEquals(ContentUiState.Loading, awaitItem())

            val failed = assertIs<ContentUiState.Error>(awaitItem())
            assertEquals(DomainError.Network.toUserMessage(), failed.message)
        }
    }

    // SPEC: TODAY-05
    @Test
    fun `reload after failure clears the error and loads the summary`() = runTest(dispatcher) {
        repository.failure = DomainError.Network
        val viewModel = viewModel()
        keepCollecting(viewModel.state)

        viewModel.state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            assertIs<ContentUiState.Error>(awaitItem(), "first load should fail")

            repository.failure = null
            repository.summary = FakeTodayRepository.populatedDay
            val recovered = assertIs<ContentUiState.Content<TodayHighlights>>(awaitItem())
            assertEquals(LocalDate(2026, 7, 22), recovered.data.today.date)
        }
    }
}
