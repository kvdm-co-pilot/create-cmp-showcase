package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.fakes.FakeProfileRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * The Profile use-case test — pure business action, fake in / behavior out (mirrors
 * GetTodaySummaryUseCaseTest). Results stay typed end-to-end; nothing here throws or catches.
 */
class GetProfileUseCaseTest {

    private val repository = FakeProfileRepository()
    private val getProfile = GetProfileUseCase(repository)

    // SPEC: PROF-01
    @Test
    fun `returns the repository's profile as Success`() = runTest {
        val expected = FakeProfileRepository.sampleProfile
        repository.profile = expected

        assertEquals(AppResult.Success(expected), getProfile())
        assertEquals(1, repository.getCallCount)
    }

    // SPEC: PROF-05
    @Test
    fun `passes a typed failure through untouched`() = runTest {
        repository.failure = DomainError.Network

        assertEquals(AppResult.Failure(DomainError.Network), getProfile())
    }
}
