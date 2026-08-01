package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.AppState
import com.kvdm.fuelled.domain.model.UnitSystem
import com.kvdm.fuelled.domain.repository.AppStateRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

/** START-01: the app's own state, observed — the shell decides interview-vs-app from it. */
class ObserveAppStateUseCase(private val repository: AppStateRepository) {
    operator fun invoke(): Flow<AppResult<AppState>> = repository.observe()
}

/** START-01: the first-run interview is finished. */
class CompleteOnboardingUseCase(private val repository: AppStateRepository) {
    suspend operator fun invoke(): AppResult<Unit> = repository.markOnboarded()
}

/** SET-02: choose the unit system. Every observed surface re-derives from the one row. */
class SetUnitSystemUseCase(private val repository: AppStateRepository) {
    suspend operator fun invoke(system: UnitSystem): AppResult<Unit> = repository.setUnitSystem(system)
}

/**
 * SET-07/SET-08: choose the reminder prep lead — and re-arm on the spot.
 *
 * The re-arm is the whole point, and it is why this is a use case rather than a bare
 * repository call: a lead that only takes effect after the next arming pass is a setting that
 * looks broken on the evening you change it. Out-of-range values are refused by the store
 * before anything is re-armed, so a rejected lead never disturbs the reminders already set.
 */
class SetPrepLeadUseCase(
    private val repository: AppStateRepository,
    private val armReminders: ArmMealRemindersUseCase,
) {
    suspend operator fun invoke(minutes: Int): AppResult<Unit> =
        when (val stored = repository.setPrepLeadMinutes(minutes)) {
            is AppResult.Failure -> stored
            is AppResult.Success -> when (val armed = armReminders()) {
                is AppResult.Failure -> armed
                is AppResult.Success -> AppResult.Success(Unit)
            }
        }
}
