package com.kvdm.fuelled.core.time

import kotlinx.datetime.LocalDate

/**
 * Read an ISO-8601 date back, answering null for anything unreadable.
 *
 * **Why this exists rather than a `runCatching` at each call site.** Dates cross this app's
 * boundaries as STRINGS — the `logicalDate` primary keys Room stores them under, and the
 * extras a `PendingIntent` carries across a process death. Every one of those reads can meet
 * a value a future build wrote, a hand-edited database, or a truncated intent, and none of
 * them should crash the screen that asked.
 *
 * `LocalDate.parse` signals all of that by throwing, so the alternative is an ad-hoc catch at
 * every call — which in the data layer is exactly what ARCH-08 forbids, because a bare
 * `catch` there can swallow a `CancellationException` and quietly break structured
 * concurrency. This is a leaf utility (ARCH-10): pure, non-suspending, reachable from every
 * layer, and with no coroutine anywhere near it, so the swallow it performs can only ever be
 * the parse failure it is written for.
 */
fun parseIsoDateOrNull(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return try {
        LocalDate.parse(value)
    } catch (_: IllegalArgumentException) {
        // The ONLY exception this may absorb. Anything else — an OOM, a coroutine
        // cancellation — propagates, because this function knows nothing about it.
        null
    }
}
