package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.domain.model.SupplementTiming
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.fakes.FakeSupplementRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * The tap-to-take mutation use-case test. Proves the action reaches the repository with the
 * exact (id, taken) it was given, and that the typed result passes through untouched.
 */
class SetSupplementTakenUseCaseTest {

    private val repository = FakeSupplementRepository()
    private val setTaken = SetSupplementTakenUseCase(repository)

    // SPEC: SUPP-03
    @Test
    fun `persists the taken state through the repository and returns Success`() = runTest {
        repository.stack = listOf(Supplement("3", "Omega-3", "1 g", SupplementTiming.MORNING, taken = false))

        assertEquals(AppResult.Success(Unit), setTaken("3", true))
        assertEquals("3" to true, repository.lastSetTaken)
    }

    // SPEC: SUPP-03
    @Test
    fun `passes a typed failure through untouched`() = runTest {
        repository.failure = DomainError.Unexpected()

        val result = setTaken("3", true)
        assertEquals(AppResult.Failure(DomainError.Unexpected()), result)
    }
}
