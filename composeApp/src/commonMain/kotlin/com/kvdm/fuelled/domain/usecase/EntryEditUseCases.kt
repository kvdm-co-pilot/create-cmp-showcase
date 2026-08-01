package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.DeletedEntry
import com.kvdm.fuelled.domain.repository.TodayRepository
import com.kvdm.fuelled.domain.result.AppResult

/** ENTRY-01: change a logged entry's serving multiple. Below one serving is a delete's job. */
class SetEntryServingsUseCase(private val repository: TodayRepository) {
    suspend operator fun invoke(id: String, servings: Int): AppResult<Unit> =
        repository.setEntryServings(id, servings.coerceAtLeast(1))
}

/** ENTRY-02: put back exactly what a delete removed. */
class RestoreLogEntryUseCase(private val repository: TodayRepository) {
    suspend operator fun invoke(entry: DeletedEntry): AppResult<Unit> = repository.restoreEntry(entry)
}
