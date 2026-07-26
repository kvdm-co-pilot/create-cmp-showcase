package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * The mark-logged use-case test — pure business action, fake in / behavior out. It addresses
 * ONE entry by id; that no other entry moves is the repository's guarantee, asserted there.
 */
class MarkEntryLoggedUseCaseTest {

    private val repository = FakeTodayRepository()
    private val markEntryLogged = MarkEntryLoggedUseCase(repository)

    // SPEC: MEAL-07
    @Test
    fun `marks exactly the entry it was given as logged`() = runTest {
        assertEquals(AppResult.Success(Unit), markEntryLogged("p1"))

        assertEquals(listOf("p1"), repository.markedLoggedIds)
    }

    // SPEC: MEAL-07
    @Test
    fun `passes a typed failure through untouched`() = runTest {
        repository.failure = DomainError.Network

        assertEquals(AppResult.Failure(DomainError.Network), markEntryLogged("p1"))
    }
}
