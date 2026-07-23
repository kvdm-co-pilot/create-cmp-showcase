package com.kvdm.fuelled.data

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.result.AppResult
import kotlin.coroutines.cancellation.CancellationException

/**
 * The data layer's ONLY exception-catching mechanism (`specs/app-base.spec.md` ARCH-08).
 * Repository implementations wrap their I/O in this instead of writing `try`/`catch` —
 * it is the single translation point where infrastructure exceptions become typed
 * [DomainError] values, and it enforces the one non-negotiable rule of coroutine error
 * handling:
 *
 * **`CancellationException` is ALWAYS rethrown, never mapped.** Swallowing it breaks
 * structured concurrency — a cancelled screen would render an error state instead of
 * simply stopping. The conformance gate scans for exactly this guard.
 *
 * [mapError] classifies everything else into your [DomainError] vocabulary; the default
 * files anything unclassified under [DomainError.Unexpected] with the cause preserved
 * for logging (never for display).
 */
suspend fun <T> suspendRunCatching(
    mapError: (Throwable) -> DomainError = { DomainError.Unexpected(it) },
    block: suspend () -> T,
): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e // never mapped: cancellation is not a failure state
    } catch (e: Throwable) {
        AppResult.Failure(mapError(e))
    }
