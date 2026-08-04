package com.kvdm.fuelled.domain.repository

import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

// Domain-facing contract for the day's supplement stack. Presentation depends on THIS, never
// on the concrete Room-backed source. One-shot operations return AppResult — they never throw
// (ARCH-06): failures cross the boundary as typed DomainError values, translated inside the
// data implementation.
interface SupplementRepository {
    /** The whole stack, ordered for display (grouping preserves that order). */
    suspend fun getStack(): AppResult<List<Supplement>>

    /** The stack as a stream — taking one on the Supplements tab moves Today's bucket count. */
    fun observeStack(): Flow<AppResult<List<Supplement>>>

    /**
     * Persist the taken state of one supplement. The write survives a reload of the screen —
     * the point of the feature (SUPP-03). [AppResult.Success] of Unit on success.
     */
    suspend fun setTaken(id: String, taken: Boolean): AppResult<Unit>

    /**
     * SET-04/SET-05: add a supplement or correct an existing one. Idempotent on the id, so a
     * double-tapped save replaces the row rather than creating a twin (the tray's reasoning
     * for client-minted ids, MEAL-05).
     */
    suspend fun save(supplement: Supplement): AppResult<Unit>

    /**
     * SET-05: drop it from the stack. Past doses are left alone — you stopped taking it, you
     * did not stop having taken it.
     */
    suspend fun delete(id: String): AppResult<Unit>

    /**
     * Which supplements were taken on [date] (NOTIF-08).
     *
     * Asked at DELIVERY, not at arming: an alarm set at midnight for an 08:00 dose knows
     * nothing about the dose being swallowed at 07:30, and firing it anyway is the same defect
     * NOTIF-06 fixed for the plan-tomorrow nudge. Takes an explicit date rather than reading
     * the clock because the reminder carries the day it is ABOUT, which after a Doze-held
     * delivery is not necessarily today.
     */
    suspend fun takenOn(date: LocalDate): AppResult<Set<String>>
}
