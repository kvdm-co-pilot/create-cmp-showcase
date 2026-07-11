package com.kvdm.cmpshowcase.presentation.home

import app.cash.turbine.test
import com.kvdm.cmpshowcase.domain.model.Item
import com.kvdm.cmpshowcase.domain.usecase.GetItemsUseCase
import com.kvdm.cmpshowcase.testing.fakes.FakeItemRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
        viewModel().state.test {
            assertTrue(awaitItem().isLoading, "initial state should be loading")
        }
    }

    @Test
    fun `emits items when repository succeeds`() = runTest(dispatcher) {
        repository.items = listOf(Item(id = "1", title = "First", subtitle = "sub"))

        viewModel().state.test {
            assertTrue(awaitItem().isLoading)

            val loaded = awaitItem()
            assertEquals(false, loaded.isLoading)
            assertEquals(listOf("First"), loaded.items.map { it.title })
            assertNull(loaded.errorMessage)
        }
    }

    @Test
    fun `emits error message when repository fails`() = runTest(dispatcher) {
        repository.shouldFail = true
        repository.failureMessage = "network down"

        viewModel().state.test {
            assertTrue(awaitItem().isLoading)

            val failed = awaitItem()
            assertEquals(false, failed.isLoading)
            assertTrue(failed.items.isEmpty())
            assertEquals("network down", failed.errorMessage)
        }
    }

    // SPEC: HOME-04
    @Test
    fun `reload after failure clears the error and loads items`() = runTest(dispatcher) {
        repository.shouldFail = true
        val viewModel = viewModel()

        viewModel.state.test {
            assertTrue(awaitItem().isLoading)
            assertNotNull(awaitItem().errorMessage, "first load should fail")

            repository.shouldFail = false
            repository.items = listOf(Item(id = "1", title = "Recovered", subtitle = "sub"))
            viewModel.load()

            assertTrue(awaitItem().isLoading, "reload should show loading again")
            val recovered = awaitItem()
            assertNull(recovered.errorMessage)
            assertEquals(listOf("Recovered"), recovered.items.map { it.title })
        }
    }
}
