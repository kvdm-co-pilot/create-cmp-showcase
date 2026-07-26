package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.TodayModel
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * The Today use-case test — pure business action, fake in / behavior out (mirrors
 * GetFoodsUseCaseTest). Results stay typed end-to-end; nothing here throws or catches.
 */
class GetTodaySummaryUseCaseTest {

    private val repository = FakeTodayRepository()
    private val getTodaySummary = GetTodaySummaryUseCase(repository)

    // SPEC: TODAY-01
    @Test
    fun `returns the repository's summary, carrying the logical day's date, as Success`() = runTest {
        val expected = FakeTodayRepository.populatedDay
        repository.summary = expected

        val result = getTodaySummary()

        assertEquals(AppResult.Success(expected), result)
        // The day the screen shows is a real LocalDate carried through untouched — the use case
        // neither derives it nor formats it (TODAY-01; the derivation is the repository's, and
        // the formatting is the screen's).
        assertEquals(LocalDate(2026, 7, 22), assertIs<AppResult.Success<TodayModel>>(result).value.date)
        assertEquals(1, repository.getCallCount)
    }

    // SPEC: TODAY-05
    @Test
    fun `passes a typed failure through untouched`() = runTest {
        repository.failure = DomainError.Network

        assertEquals(AppResult.Failure(DomainError.Network), getTodaySummary())
    }
}
