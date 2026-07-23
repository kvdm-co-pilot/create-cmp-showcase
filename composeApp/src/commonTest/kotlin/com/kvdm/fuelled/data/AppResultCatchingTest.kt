package com.kvdm.fuelled.data

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.result.AppResult
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

/**
 * The exception-translation helper's contract (`specs/app-base.spec.md` ARCH-08):
 * success wraps, failures map to typed kinds, and cancellation ALWAYS propagates.
 */
class AppResultCatchingTest {

    @Test
    fun `wraps the block's value in Success`() = runTest {
        val result = suspendRunCatching { 42 }

        assertEquals(AppResult.Success(42), result)
    }

    @Test
    fun `maps a thrown exception through mapError to a typed Failure`() = runTest {
        val boom = IllegalStateException("io broke")

        val result = suspendRunCatching(mapError = { DomainError.Network }) { throw boom }

        assertEquals(AppResult.Failure(DomainError.Network), result)
    }

    @Test
    fun `files unclassified exceptions under Unexpected with the cause preserved`() = runTest {
        val boom = IllegalStateException("io broke")

        val result = suspendRunCatching { throw boom }

        val failure = assertIs<AppResult.Failure>(result)
        val error = assertIs<DomainError.Unexpected>(failure.error)
        assertEquals(boom, error.cause)
    }

    // SPEC: ARCH-08
    @Test
    fun `rethrows CancellationException instead of mapping it`() = runTest {
        assertFailsWith<CancellationException> {
            suspendRunCatching { throw CancellationException("scope cancelled") }
        }
    }
}
