package com.kvdm.fuelled.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * The structured day's stored state: slot times, done-ticks, water ticks (specs/meal-plan.spec.md).
 *
 * Every tick verb is an explicit set/clear pair rather than a toggle. A toggle's result depends
 * on state the caller did not read, so two taps racing each other can land on either answer;
 * "make it done" is idempotent and says what the user actually meant.
 */
@Dao
interface MealPlanDao {

    // ── Slot times (PLAN-05/PLAN-06) ─────────────────────────────────────────────────────
    @Query("SELECT * FROM meal_slot_time")
    suspend fun slotTimes(): List<SlotTimeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSlotTime(row: SlotTimeEntity)

    // ── Done-ticks (PLAN-13/PLAN-14) ─────────────────────────────────────────────────────
    @Query("SELECT * FROM meal_slot_done WHERE logicalDate = :logicalDate")
    suspend fun doneSlots(logicalDate: String): List<SlotDoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoneSlot(row: SlotDoneEntity)

    @Query("DELETE FROM meal_slot_done WHERE logicalDate = :logicalDate AND slot = :slot")
    suspend fun clearDoneSlot(logicalDate: String, slot: String)

    // ── Water ticks (PLAN-10) ────────────────────────────────────────────────────────────
    @Query("SELECT * FROM meal_water_tick WHERE logicalDate = :logicalDate")
    suspend fun waterTicks(logicalDate: String): List<WaterTickEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaterTick(row: WaterTickEntity)

    @Query("DELETE FROM meal_water_tick WHERE logicalDate = :logicalDate AND waterIndex = :waterIndex")
    suspend fun clearWaterTick(logicalDate: String, waterIndex: Int)

    // ── Ticking a slot done (PLAN-13) ────────────────────────────────────────────────────
    /**
     * Record [slot] done on [logicalDate] and flip that container's `PLANNED` entries to
     * `LOGGED` in the SAME transaction (PLAN-13). One transaction because the two halves are
     * one user act: a crash between them would leave a day claiming a meal was eaten while its
     * calories sat uncounted — the exact disagreement between the tick and the ring that the
     * derived-day shape exists to prevent.
     *
     * An empty container writes only the done row (PLAN-14): the UPDATE matches nothing and no
     * food is fabricated.
     */
    @Transaction
    suspend fun markSlotDone(logicalDate: String, slot: String) {
        insertDoneSlot(SlotDoneEntity(logicalDate = logicalDate, slot = slot))
        logPlannedEntries(logicalDate, slot)
    }

    @Query(
        "UPDATE today_log SET status = 'LOGGED' " +
            "WHERE logicalDate = :logicalDate AND slot = :slot AND status = 'PLANNED'",
    )
    suspend fun logPlannedEntries(logicalDate: String, slot: String)

    // ── Copy a day forward (PLAN-20) ─────────────────────────────────────────────────────
    /** One day's `PLANNED` rows — the source of a copy-forward, in stable order. */
    @Query("SELECT * FROM today_log WHERE logicalDate = :logicalDate AND status = 'PLANNED' ORDER BY slot, entryOrder")
    suspend fun plannedEntries(logicalDate: String): List<LogEntryEntity>

    /**
     * Write the copies (PLAN-20). Atomic for the same reason the tray's confirm is: a
     * half-copied week is worse than an uncopied one, because it looks planned.
     */
    @Transaction
    suspend fun insertCopiedEntries(entries: List<LogEntryEntity>) {
        for (entry in entries) upsertCopiedEntry(entry)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCopiedEntry(entry: LogEntryEntity)
}
