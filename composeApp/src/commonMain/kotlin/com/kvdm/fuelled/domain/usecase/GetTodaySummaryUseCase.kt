package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.TodayModel
import com.kvdm.fuelled.domain.repository.TodayRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

// A use case is a single business action. ViewModels depend on use cases, not repositories
// directly, so business rules stay testable and out of the presentation layer. The typed
// result passes through untouched — never unwrapped into an exception (mirrors GetFoodsUseCase).
//
// This one OBSERVES rather than fetches: the dashboard's answer changes without anyone asking
// it to (a meal logged elsewhere, 04:00 arriving), so the use case hands back the stream and
// the ViewModel keeps its state on it. See TodayRepository.observeTodaySummary.
class GetTodaySummaryUseCase(
    private val repository: TodayRepository,
) {
    operator fun invoke(): Flow<AppResult<TodayModel>> = repository.observeTodaySummary()
}
