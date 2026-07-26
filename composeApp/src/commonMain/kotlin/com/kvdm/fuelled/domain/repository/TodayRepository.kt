package com.kvdm.fuelled.domain.repository

import com.kvdm.fuelled.domain.model.LogStatus
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.NewLogEntry
import com.kvdm.fuelled.domain.model.TodayModel
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.datetime.LocalDate

// Domain-facing contract for the Today dashboard and the meal-log write path. Presentation
// depends on THIS, never on the concrete Room-backed source. Every operation returns
// AppResult — it never throws (ARCH-06): failures cross the boundary as typed DomainError
// values, translated inside the data implementation.
interface TodayRepository {
    /** The day's aggregated summary — calories + macros against goal, plus the log by slot. */
    suspend fun getTodaySummary(): AppResult<TodayModel>

    /**
     * Write [entries] to one target — [date], [slot], [status] — in a SINGLE transaction
     * (MEAL-05). All of them land or none of them do; a failed write persists nothing and
     * comes back as a typed failure, never a raw exception.
     *
     * [status] is decided by the caller, not here: whether a confirm is a log or a schedule
     * is a business rule about the CURRENT logical day, and it lives in the use case
     * (MEAL-08). The repository's job is to write what it is told, atomically.
     */
    suspend fun addEntries(
        entries: List<NewLogEntry>,
        date: LocalDate,
        slot: MealSlot,
        status: LogStatus,
    ): AppResult<Unit>

    /** Remove the entry with [id] from its day (MEAL-06); the day's totals recompute on read. */
    suspend fun deleteEntry(id: String): AppResult<Unit>

    /** Flip the entry with [id] to `LOGGED` (MEAL-07); no other entry is touched. */
    suspend fun markEntryLogged(id: String): AppResult<Unit>
}
