package com.kvdm.fuelled.domain.repository

import com.kvdm.fuelled.domain.model.AppState
import com.kvdm.fuelled.domain.model.PREP_LEAD_RANGE
import com.kvdm.fuelled.domain.model.UnitSystem
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * The app's knowledge about ITSELF (START-01/START-02): whether the first-run interview is
 * done, and the instant this install was first opened.
 *
 * Observed, not read once: finishing onboarding must swap the shell in place, and the
 * first-open instant is what the plan screen consults to tell "you skipped breakfast" from
 * "breakfast happened before this app existed".
 */
interface AppStateRepository {
    /** The state, observed — seeded on first read (the install's first open is NOW). */
    fun observe(): Flow<AppResult<AppState>>

    /** A one-shot read, for a derivation that must not hold a subscription. */
    suspend fun current(): AppResult<AppState>

    /** START-01: the interview is done; the shell takes over. */
    suspend fun markOnboarded(): AppResult<Unit>

    /** SET-02: the unit system. Display only — nothing stored is ever re-expressed. */
    suspend fun setUnitSystem(system: UnitSystem): AppResult<Unit>

    /** SET-07: the reminder prep lead, in minutes. Values outside [PREP_LEAD_RANGE] are refused. */
    suspend fun setPrepLeadMinutes(minutes: Int): AppResult<Unit>
}
