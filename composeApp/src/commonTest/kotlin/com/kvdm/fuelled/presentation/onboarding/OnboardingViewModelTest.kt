package com.kvdm.fuelled.presentation.onboarding

import com.kvdm.fuelled.domain.usecase.CompleteOnboardingUseCase
import com.kvdm.fuelled.domain.usecase.ObserveAppStateUseCase
import com.kvdm.fuelled.domain.usecase.UpdateGoalsUseCase
import com.kvdm.fuelled.domain.usecase.UpdateProfileNameUseCase
import com.kvdm.fuelled.testing.fakes.FakeAppStateRepository
import com.kvdm.fuelled.testing.fakes.FakeProfileRepository
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import com.kvdm.fuelled.testing.keepCollecting
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * The first-run interview (START-01): the gate the shell reads, and the three answers going
 * to the SAME stores Profile's editors write (PERS-02/PERS-03) — never a second write path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val appState = FakeAppStateRepository()
    private val profile = FakeProfileRepository()
    private val today = FakeTodayRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = OnboardingViewModel(
        observeAppState = ObserveAppStateUseCase(appState),
        completeOnboarding = CompleteOnboardingUseCase(appState),
        updateGoals = UpdateGoalsUseCase(today),
        updateName = UpdateProfileNameUseCase(profile),
    )

    // SPEC: START-01
    @Test
    fun `a fresh install gates to the interview, a returning one straight to the app`() =
        runTest(dispatcher) {
            val fresh = viewModel()
            keepCollecting(fresh.gate)
            advanceUntilIdle()
            assertEquals(StartGate.ONBOARDING, fresh.gate.value, "first open asks")

            appState.onboarded = true
            val returning = viewModel()
            keepCollecting(returning.gate)
            advanceUntilIdle()
            assertEquals(StartGate.APP, returning.gate.value, "and never asks twice")
        }

    // SPEC: START-01
    @Test
    fun `finishing writes the answers through the same stores the editors use, then opens the app`() =
        runTest(dispatcher) {
            val vm = viewModel()
            keepCollecting(vm.gate)

            vm.finish("Alex", targetKcal = 3000, proteinGoalG = 200)
            advanceUntilIdle()

            assertEquals(listOf("Alex"), profile.nameUpdates)
            assertEquals(listOf(3000 to 200), today.goalUpdates, "the ONE goal store (PERS-01)")
            assertEquals(true, appState.onboarded)
            assertEquals(StartGate.APP, vm.gate.value, "the gate swaps in place — no relaunch")
        }

    // SPEC: START-01
    @Test
    fun `skipping keeps the seeded defaults and still never asks again`() = runTest(dispatcher) {
        val vm = viewModel()
        keepCollecting(vm.gate)

        vm.skip()
        advanceUntilIdle()

        assertEquals(0, today.goalUpdates.size, "skip writes no goals — the defaults are real")
        assertEquals(0, profile.nameUpdates.size)
        assertEquals(true, appState.onboarded, "but the interview is done")
    }

    // SPEC: START-01
    @Test
    fun `a blank name and junk numbers reach no write - the interview is not a trap`() =
        runTest(dispatcher) {
            val vm = viewModel()
            keepCollecting(vm.gate)

            vm.finish("   ", targetKcal = null, proteinGoalG = 0)
            advanceUntilIdle()

            assertEquals(0, profile.nameUpdates.size, "a blank name is not a name")
            assertEquals(0, today.goalUpdates.size, "a non-positive target is refused before the write")
            assertEquals(true, appState.onboarded, "and the user still gets into their app")
        }
}
