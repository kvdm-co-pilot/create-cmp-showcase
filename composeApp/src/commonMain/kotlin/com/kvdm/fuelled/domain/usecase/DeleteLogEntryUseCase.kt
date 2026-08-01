package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.DeletedEntry
import com.kvdm.fuelled.domain.repository.TodayRepository
import com.kvdm.fuelled.domain.result.AppResult

// Remove one entry from its day (MEAL-06). A mutation use case mirrors the read use cases'
// shape — the typed AppResult passes through untouched, never unwrapped into an exception
// (ARCH-06). The day's consumed total and macro progress are not adjusted here: they are
// DERIVED from the surviving rows on the next read, so there is no second total to drift.
class DeleteLogEntryUseCase(
    private val repository: TodayRepository,
) {
    suspend operator fun invoke(id: String): AppResult<DeletedEntry> = repository.deleteEntry(id)
}
