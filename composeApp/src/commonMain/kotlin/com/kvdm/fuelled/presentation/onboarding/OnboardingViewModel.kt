package com.kvdm.fuelled.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.domain.usecase.CompleteOnboardingUseCase
import com.kvdm.fuelled.domain.usecase.ObserveAppStateUseCase
import com.kvdm.fuelled.domain.usecase.UpdateGoalsUseCase
import com.kvdm.fuelled.domain.usecase.UpdateProfileNameUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the shell needs to know before it renders anything (START-01). */
enum class StartGate { UNKNOWN, ONBOARDING, APP }

/**
 * The first-run interview (START-01).
 *
 * It writes through the SAME use cases the Profile editors use (PERS-02/PERS-03) — the
 * interview is not a second way to set your goals, it is the first time you are asked. That
 * is why there is no onboarding-specific write path here: one store, one set of guards.
 *
 * The gate is OBSERVED, so completing the interview swaps the shell in place rather than
 * needing a relaunch (RS-01's discipline applied to the app's own state).
 */
class OnboardingViewModel(
    observeAppState: ObserveAppStateUseCase,
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val updateGoals: UpdateGoalsUseCase,
    private val updateName: UpdateProfileNameUseCase,
) : ViewModel() {

    val gate: StateFlow<StartGate> =
        observeAppState()
            .map { result ->
                when (result) {
                    is AppResult.Success -> if (result.value.onboarded) StartGate.APP else StartGate.ONBOARDING
                    // A state we cannot read must not lock the user out of their own app:
                    // failing OPEN is the honest default — the interview can be re-offered,
                    // a blocked app cannot be used at all.
                    is AppResult.Failure -> StartGate.APP
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, StartGate.UNKNOWN)

    /**
     * START-01: save the answers, then mark the interview done — in that order, so a failure
     * to write the goals leaves the interview still pending rather than dropping the user
     * into an app that never learned their targets. Blank/non-positive answers are refused
     * by the same guards Profile's editors use; a skipped answer keeps the seeded default.
     */
    fun finish(name: String, targetKcal: Int?, proteinGoalG: Int?) {
        viewModelScope.launch {
            if (name.isNotBlank()) updateName(name.trim())
            if (targetKcal != null && proteinGoalG != null && targetKcal > 0 && proteinGoalG > 0) {
                updateGoals(targetKcal, proteinGoalG)
            }
            completeOnboarding()
        }
    }

    /** START-01: "I'll do it later" — the defaults stand, and the interview never returns. */
    fun skip() {
        viewModelScope.launch { completeOnboarding() }
    }
}
