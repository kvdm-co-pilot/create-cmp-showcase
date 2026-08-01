package com.kvdm.fuelled.domain.repository

import com.kvdm.fuelled.domain.model.Profile
import com.kvdm.fuelled.domain.result.AppResult

// Domain-facing contract for the Profile aggregate. Presentation depends on THIS, never on the
// concrete Room-backed source. The one-shot read returns AppResult — it never throws (ARCH-06):
// failures cross the boundary as typed DomainError values, translated inside the data
// implementation.
interface ProfileRepository {
    /**
     * The user's profile — identity, daily goals, and the week's stats. The goal values are
     * READ from the one goal store (PERS-01); this aggregate never carries its own copy.
     */
    suspend fun getProfile(): AppResult<Profile>

    /** PERS-03: rename the user. Identity only — goals and stats are untouched. */
    suspend fun updateName(name: String): AppResult<Unit>
}
