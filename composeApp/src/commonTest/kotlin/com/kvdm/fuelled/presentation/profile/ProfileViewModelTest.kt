package com.kvdm.fuelled.presentation.profile

import app.cash.turbine.test
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Profile
import com.kvdm.fuelled.domain.usecase.GetProfileUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.testing.fakes.FakeProfileRepository
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
 * The Profile ViewModel test — mirrors TodayViewModelTest: Turbine over the [ContentUiState]
 * machine, hand-written fakes (never mocks), a [StandardTestDispatcher] installed as Main so
 * viewModelScope launches run under virtual time. A profile always exists, so the machine is
 * Loading, Content, or Error only — there is no Empty arm.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeProfileRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ProfileViewModel(GetProfileUseCase(repository))

    // SPEC: PROF-01
    @Test
    fun `emits Loading then Content when the profile loads`() = runTest(dispatcher) {
        val profile = FakeProfileRepository.sampleProfile
        repository.profile = profile

        viewModel().state.test {
            assertEquals(ContentUiState.Loading, awaitItem(), "initial state should be Loading")
            assertEquals(ContentUiState.Content(profile), awaitItem())
        }
    }

    // SPEC: PROF-05
    @Test
    fun `maps a typed failure to presentation copy - never a raw exception message`() = runTest(dispatcher) {
        repository.failure = DomainError.Network

        viewModel().state.test {
            assertEquals(ContentUiState.Loading, awaitItem())

            val failed = assertIs<ContentUiState.Error>(awaitItem())
            assertEquals(DomainError.Network.toUserMessage(), failed.message)
        }
    }

    // SPEC: PROF-05
    @Test
    fun `reload after failure clears the error and loads the profile`() = runTest(dispatcher) {
        repository.failure = DomainError.Network
        val viewModel = viewModel()

        viewModel.state.test {
            assertEquals(ContentUiState.Loading, awaitItem())
            assertIs<ContentUiState.Error>(awaitItem(), "first load should fail")

            repository.failure = null
            repository.profile = FakeProfileRepository.sampleProfile
            viewModel.load()

            assertEquals(ContentUiState.Loading, awaitItem(), "reload should show loading again")
            val recovered = assertIs<ContentUiState.Content<Profile>>(awaitItem())
            assertEquals("Karel", recovered.data.identity.name)
        }
    }
}
