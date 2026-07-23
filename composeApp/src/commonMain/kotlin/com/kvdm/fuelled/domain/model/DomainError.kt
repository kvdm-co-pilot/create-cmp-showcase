package com.kvdm.fuelled.domain.model

/**
 * The typed failure vocabulary of the domain layer — every failure a repository can report
 * is one of these KINDS. No user-facing message strings live here: mapping a kind to copy
 * is the presentation layer's job (see the exemplar ViewModel's `toUserMessage()`), so the
 * domain stays translatable and UI-copy changes never touch this layer.
 *
 * Extend with the kinds YOUR sources can actually produce (e.g. `Unauthorized`, `Conflict`)
 * — the data layer's `suspendRunCatching` mapper is the single place they are assigned.
 */
sealed interface DomainError {
    /** The source was unreachable — connectivity, DNS, timeouts. */
    data object Network : DomainError

    /** The requested entity does not exist at the source. */
    data object NotFound : DomainError

    /** Anything not yet classified. Carries the cause for logging — never for display. */
    data class Unexpected(val cause: Throwable? = null) : DomainError
}
