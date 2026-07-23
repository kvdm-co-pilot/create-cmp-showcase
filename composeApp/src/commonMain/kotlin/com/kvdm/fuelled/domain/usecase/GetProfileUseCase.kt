package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.Profile
import com.kvdm.fuelled.domain.repository.ProfileRepository
import com.kvdm.fuelled.domain.result.AppResult

// A use case is a single business action. ViewModels depend on use cases, not repositories
// directly, so business rules stay testable and out of the presentation layer. The typed
// result passes through untouched — never unwrapped into an exception (mirrors GetTodaySummaryUseCase).
class GetProfileUseCase(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(): AppResult<Profile> = repository.getProfile()
}
