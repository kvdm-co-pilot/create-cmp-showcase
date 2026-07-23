package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.repository.SupplementRepository
import com.kvdm.fuelled.domain.result.AppResult

// The tap-to-take business action: persist one supplement's taken state. A mutation use case
// mirrors the read use cases' shape — the typed AppResult passes through untouched, never
// unwrapped into an exception (ARCH-06).
class SetSupplementTakenUseCase(
    private val repository: SupplementRepository,
) {
    suspend operator fun invoke(id: String, taken: Boolean): AppResult<Unit> =
        repository.setTaken(id, taken)
}
