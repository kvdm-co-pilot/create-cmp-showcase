package com.kvdm.fuelled.data

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.result.AppResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

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

/**
 * The streaming counterpart of [suspendRunCatching] — the same single translation point, for
 * the observable read path.
 *
 * A repository that returns `Flow<T>` still must not let an infrastructure exception cross the
 * layer boundary (ARCH-06/ARCH-08), so every stream is mapped to `Flow<AppResult<T>>` through
 * here rather than through a hand-written `catch` at each call site.
 *
 * `CancellationException` is rethrown for the same non-negotiable reason: a screen that stops
 * collecting because it left the composition has not failed, and rendering an error for it
 * would be a lie. (`Flow.catch` is already transparent to downstream cancellation; the
 * explicit guard states the rule where a reader — and the conformance gate — will look.)
 */
fun <T> Flow<T>.asAppResult(
    mapError: (Throwable) -> DomainError = { DomainError.Unexpected(it) },
): Flow<AppResult<T>> =
    map<T, AppResult<T>> { AppResult.Success(it) }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(AppResult.Failure(mapError(e)))
        }
