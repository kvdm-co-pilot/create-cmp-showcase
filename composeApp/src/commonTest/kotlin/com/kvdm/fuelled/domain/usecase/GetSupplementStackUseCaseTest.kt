package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.fakes.FakeSupplementRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first

/**
 * The Supplements read use-case test — mirrors GetFoodsUseCaseTest: fake in, behaviour out.
 * Results stay typed end-to-end; nothing here throws or catches.
 */
class GetSupplementStackUseCaseTest {

    private val repository = FakeSupplementRepository()
    private val getStack = GetSupplementStackUseCase(repository)

    // SPEC: SUPP-01
    @Test
    fun `returns the repository's stack as Success`() = runTest {
        val expected = listOf(
            Supplement("1", "Creatine", "5 g", "Morning", taken = true),
            Supplement("2", "Caffeine", "200 mg", "Pre-workout", taken = false),
        )
        repository.stack = expected

        assertEquals(AppResult.Success(expected), getStack().first())
    }

    // SPEC: SUPP-01
    @Test
    fun `passes a typed failure through untouched`() = runTest {
        repository.failure = DomainError.Network

        assertEquals(AppResult.Failure(DomainError.Network), getStack().first())
    }
}
