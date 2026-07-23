package com.kvdm.fuelled.domain.repository

import com.kvdm.fuelled.domain.model.Profile
import com.kvdm.fuelled.domain.result.AppResult

// Domain-facing contract for the Profile aggregate. Presentation depends on THIS, never on the
// concrete Room-backed source. The one-shot read returns AppResult — it never throws (ARCH-06):
// failures cross the boundary as typed DomainError values, translated inside the data
// implementation.
interface ProfileRepository {
    /** The user's profile — identity, daily goals, and the week's stats. */
    suspend fun getProfile(): AppResult<Profile>
}
