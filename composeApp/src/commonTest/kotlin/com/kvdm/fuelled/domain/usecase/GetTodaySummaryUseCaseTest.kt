package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * The Today use-case test — pure business action, fake in / behavior out (mirrors
 * GetFoodsUseCaseTest). Results stay typed end-to-end; nothing here throws or catches.
 */
class GetTodaySummaryUseCaseTest {

    private val repository = FakeTodayRepository()
    private val getTodaySummary = GetTodaySummaryUseCase(repository)

    // SPEC: TODAY-01
    @Test
    fun `returns the repository's summary as Success`() = runTest {
        val expected = FakeTodayRepository.populatedDay
        repository.summary = expected

        assertEquals(AppResult.Success(expected), getTodaySummary())
        assertEquals(1, repository.getCallCount)
    }

    // SPEC: TODAY-05
    @Test
    fun `passes a typed failure through untouched`() = runTest {
        repository.failure = DomainError.Network

        assertEquals(AppResult.Failure(DomainError.Network), getTodaySummary())
    }
}
