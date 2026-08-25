package com.kvdm.fuelled.domain.repository

import com.kvdm.fuelled.domain.model.AppRelease
import com.kvdm.fuelled.domain.result.AppResult

/**
 * Domain-facing contract for the update check (UPD-01). Presentation depends on THIS, never on
 * Ktor or on the GitHub response shape.
 *
 * One-shot and returning [AppResult] — it never throws (ARCH-06). A rate limit, a dead network
 * and a malformed body all arrive as typed [com.kvdm.fuelled.domain.model.DomainError] values
 * (UPD-05), because the one answer this feature must never give is a confident "up to date" it
 * could not actually verify.
 */
interface UpdateRepository {

    /** The newest published release, or null when the repository has published none. */
    suspend fun latestRelease(): AppResult<AppRelease?>
}
