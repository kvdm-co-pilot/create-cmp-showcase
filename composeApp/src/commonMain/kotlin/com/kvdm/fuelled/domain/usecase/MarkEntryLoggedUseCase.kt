package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.repository.TodayRepository
import com.kvdm.fuelled.domain.result.AppResult

// Turn a scheduled entry into an eaten one (MEAL-07): its status becomes LOGGED and it starts
// counting toward that day's consumed total. One row, addressed by id — no other entry moves.
// Mirrors the other mutation use cases: the typed AppResult passes through untouched (ARCH-06).
class MarkEntryLoggedUseCase(
    private val repository: TodayRepository,
) {
    suspend operator fun invoke(id: String): AppResult<Unit> = repository.markEntryLogged(id)
}
