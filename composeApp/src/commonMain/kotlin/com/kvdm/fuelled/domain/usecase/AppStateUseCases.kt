package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.AppState
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
