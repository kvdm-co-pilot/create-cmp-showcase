package com.kvdm.fuelled.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TodayDao {
    @Query("SELECT * FROM today_goal LIMIT 1")
    suspend fun goal(): TodayGoalEntity?

    /** The goal as a stream — re-emitted by Room whenever the row is written. */
    @Query("SELECT * FROM today_goal LIMIT 1")
    fun goalStream(): Flow<TodayGoalEntity?>

    /**
     * One logical day's log, stably ordered within each slot (TODAY-03).
     *
     * The SLOT order is applied by the repository from `MealSlot.ordinal`, not by this query:
     * SQLite sorts the stored names alphabetically (BREAKFAST, DINNER, LUNCH, SNACK), and the
     * `CASE`-expression fix would be a second copy of the enum's order living in SQL — exactly
     * the duplicate truth the dropped `mealOrder` column was.
     */
    @Query("SELECT * FROM today_log WHERE logicalDate = :logicalDate ORDER BY entryOrder")
    suspend fun entries(logicalDate: String): List<LogEntryEntity>

    /**
     * The same day's log as a STREAM. Room's invalidation tracker re-runs this query and
     * emits on every write to `today_log` — which is how a meal added in the tray reaches
     * the Today dashboard and the plan screen with no reload, no lifecycle callback, and no
     * "something changed" event bus. Nothing is polled: the emission is the write.
     */
    @Query("SELECT * FROM today_log WHERE logicalDate = :logicalDate ORDER BY entryOrder")
    fun entriesStream(logicalDate: String): Flow<List<LogEntryEntity>>

    /** The highest `entryOrder` in one `(day, slot)`, or -1 when it is empty — the append point. */
    @Query("SELECT COALESCE(MAX(entryOrder), -1) FROM today_log WHERE logicalDate = :logicalDate AND slot = :slot")
    suspend fun maxEntryOrder(logicalDate: String, slot: String): Int

    @Query("SELECT COUNT(*) FROM today_goal")
    suspend fun goalCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: TodayGoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: LogEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntries(entries: List<LogEntryEntity>)

    /**
     * The tray confirm's write (MEAL-05): every item lands or none does. `@Transaction` is what
     * makes that true — a failure part-way through rolls the whole batch back, so a half-written
     * meal can never reach the ledger.
     */
    @Transaction
    suspend fun insertEntriesAtomically(entries: List<LogEntryEntity>) {
        for (entry in entries) upsertEntry(entry)
    }

    @Query("DELETE FROM today_log WHERE id = :id")
    suspend fun deleteEntry(id: String)

    @Query("UPDATE today_log SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)

    @Query("DELETE FROM today_log")
    suspend fun clearEntries()
}
