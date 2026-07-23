package com.kvdm.fuelled.domain.repository

import com.kvdm.fuelled.domain.model.TodayModel
import com.kvdm.fuelled.domain.result.AppResult

// Domain-facing contract for the Today dashboard. Presentation depends on THIS, never on the
// concrete Room-backed source. The one-shot summary returns AppResult — it never throws
// (ARCH-06): failures cross the boundary as typed DomainError values, translated inside the
// data implementation.
interface TodayRepository {
    /** The day's aggregated summary — calories + macros against goal, plus the log by meal. */
    suspend fun getTodaySummary(): AppResult<TodayModel>
}
