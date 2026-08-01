package com.kvdm.fuelled.domain.repository

import com.kvdm.fuelled.domain.model.WeightEntry
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * The weigh-in log (HIST-06..08) — the one stored thing on the Progress surface.
 *
 * Observed, like every other read in this app: recording a weight must move the section that
 * shows it without a reload (RS-01). Values cross this seam in KILOGRAMS; the unit system is
 * a display concern (SET-02) and never reaches storage.
 */
interface WeightRepository {
    /** The window, ascending by date. */
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<AppResult<List<WeightEntry>>>

    /** HIST-06: one entry per logical day — recording again for the same day REPLACES it. */
    suspend fun record(entry: WeightEntry): AppResult<Unit>
}
