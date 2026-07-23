package com.kvdm.fuelled.domain.result

import com.kvdm.fuelled.domain.model.DomainError

/**
 * The typed result that crosses the data → domain → presentation boundary. One-shot
 * repository operations return `AppResult<T>`, never throw (`specs/app-base.spec.md`
 * ARCH-06) — so a ViewModel exhaustively `when`s over Success/Failure instead of
 * catching exceptions (ARCH-07).
 *
 * Deliberately our own type rather than `kotlin.Result`: the stdlib Result carries an
 * untyped Throwable, which would put raw exceptions right back on the boundary this
 * type exists to keep them off. A [Failure] carries a typed [DomainError] kind instead
 * (the same call Now in Android makes with its own Result).
 *
 * Cancellation is NOT a result: `CancellationException` propagates (structured
 * concurrency), enforced at the single translation point — `suspendRunCatching` in
 * `data/AppResultCatching.kt` (ARCH-08).
 */
sealed interface AppResult<out T> {
    data class Success<out T>(val value: T) : AppResult<T>
    data class Failure(val error: DomainError) : AppResult<Nothing>
}
