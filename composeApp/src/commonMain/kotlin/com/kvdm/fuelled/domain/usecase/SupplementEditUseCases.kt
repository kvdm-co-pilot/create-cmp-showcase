package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.domain.model.SupplementTiming
import com.kvdm.fuelled.domain.repository.SupplementRepository
import com.kvdm.fuelled.domain.result.AppResult

/**
 * SET-04/SET-05: the stack becomes the user's.
 *
 * The guards live HERE, before the write (MEAL-11's stance): a supplement with no name is
 * unfindable in its own list, and one with no dose is a reminder to take an unspecified
 * amount of something. A refused save leaves the form exactly as typed — nothing is silently
 * corrected and nothing is written.
 */
class SaveSupplementUseCase(private val repository: SupplementRepository) {
    suspend operator fun invoke(
        id: String,
        name: String,
        dose: String,
        timing: SupplementTiming,
    ): AppResult<Unit> {
        if (name.isBlank() || dose.isBlank()) return AppResult.Success(Unit)
        return repository.save(
            Supplement(
                id = id,
                name = name.trim(),
                dose = dose.trim(),
                timing = timing,
                // Whether it has been taken TODAY is a fact about the day, held in its own
                // table (SUPP-07). Editing the catalog row cannot assert anything about it,
                // so this carries the only honest value: the write path ignores it entirely.
                taken = false,
            ),
        )
    }
}

/** SET-05: drop it from the stack. Past doses stand — see [SupplementRepository.delete]. */
class DeleteSupplementUseCase(private val repository: SupplementRepository) {
    suspend operator fun invoke(id: String): AppResult<Unit> = repository.delete(id)
}
