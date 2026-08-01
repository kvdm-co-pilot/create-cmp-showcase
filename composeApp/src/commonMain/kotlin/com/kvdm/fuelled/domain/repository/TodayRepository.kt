package com.kvdm.fuelled.domain.repository

import com.kvdm.fuelled.domain.model.DeletedEntry
import com.kvdm.fuelled.domain.model.LogStatus
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.NewLogEntry
import com.kvdm.fuelled.domain.model.TodayModel
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

// Domain-facing contract for the Today dashboard and the meal-log write path. Presentation
// depends on THIS, never on the concrete Room-backed source. Every operation returns
// AppResult — it never throws (ARCH-06): failures cross the boundary as typed DomainError
// values, translated inside the data implementation.
interface TodayRepository {
    /**
     * The day's aggregated summary as a STREAM — calories + macros against goal, plus the log
     * by slot — re-emitted whenever the answer changes, for either reason it can change:
     *
     * - **the log changed** — a meal added in the tray, an entry deleted, a slot ticked done.
     *   Room's invalidation tracker drives this; the dashboard follows a write made on another
     *   screen with no reload and no lifecycle callback.
     * - **the logical day changed** — 04:00 arrived, or the app came back to the foreground
     *   after the device slept. The day is re-derived from the clock signal, so an app left
     *   open overnight speaks for the new day rather than the one it launched in.
     *
     * Collect this; never hold the value. A one-shot read is exactly how both of those went
     * stale (observed on-device 2026-07-28).
     */
    fun observeTodaySummary(): Flow<AppResult<TodayModel>>

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

    /**
     * Remove the entry with [id] from its day (MEAL-06); the day's totals recompute on read.
     * Returns what was removed (ENTRY-02) so an undo can put it back exactly — including its
     * day, slot, order and serving multiple. A delete that returned Unit made undo impossible
     * to build without a second read racing the delete.
     */
    suspend fun deleteEntry(id: String): AppResult<DeletedEntry>

    /** ENTRY-02: put a removed entry back, unchanged, where it was. */
    suspend fun restoreEntry(entry: DeletedEntry): AppResult<Unit>

    /** ENTRY-01: change one logged entry's serving multiple; its totals re-derive. */
    suspend fun setEntryServings(id: String, servings: Int): AppResult<Unit>

    /** Flip the entry with [id] to `LOGGED` (MEAL-07); no other entry is touched. */
    suspend fun markEntryLogged(id: String): AppResult<Unit>

    /**
     * PERS-01: update the daily targets in the ONE goal store. Every observed reader — the
     * ring, the macros, the week review, Profile's goal rows — re-targets on its existing
     * stream; what was eaten is never touched. Carbs/fat targets keep their stored values.
     */
    suspend fun updateGoals(targetKcal: Int, proteinTargetG: Int): AppResult<Unit>
}
