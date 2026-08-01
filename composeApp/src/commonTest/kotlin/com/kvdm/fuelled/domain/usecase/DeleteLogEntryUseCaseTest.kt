package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import kotlin.test.Test
import com.kvdm.fuelled.domain.model.DeletedEntry
import kotlin.test.assertIs
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * The delete use-case test — pure business action, fake in / behavior out (mirrors
 * SetSupplementTakenUseCaseTest). Results stay typed end-to-end; nothing here throws or
 * catches. The recompute the clause promises is the repository's, and is asserted there.
 */
class DeleteLogEntryUseCaseTest {

    private val repository = FakeTodayRepository()
    private val deleteLogEntry = DeleteLogEntryUseCase(repository)

    // SPEC: MEAL-06
    @Test
    fun `deletes exactly the entry it was given`() = runTest {
        // ENTRY-02: the delete hands back WHAT it removed, so an undo can restore it exactly.
        val removed = assertIs<AppResult.Success<DeletedEntry>>(deleteLogEntry("b1"))
        assertEquals("b1", removed.value.id)

        assertEquals(listOf("b1"), repository.deletedIds)
    }

    // SPEC: MEAL-06
    @Test
    fun `passes a typed failure through untouched`() = runTest {
        repository.failure = DomainError.Network

        assertEquals(AppResult.Failure(DomainError.Network), deleteLogEntry("b1"))
    }
}
