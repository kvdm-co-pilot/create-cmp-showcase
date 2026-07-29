package com.kvdm.fuelled.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplementDao {
    // Timing-grouped, stable order — the repository returns this order and grouping preserves it.
    @Query("SELECT * FROM supplements ORDER BY timingOrder")
    suspend fun getAll(): List<SupplementEntity>

    /** The stack as a stream — taking one on the Supplements tab moves Today's bucket count. */
    @Query("SELECT * FROM supplements ORDER BY timingOrder")
    fun getAllStream(): Flow<List<SupplementEntity>>

    // ── The day's doses (SUPP-03/SUPP-07) ────────────────────────────────────────────────
    // Set/clear rather than a toggle, for the reason MealPlanDao states: a toggle's result
    // depends on state the caller did not read, so two taps racing land on either answer.

    @Query("SELECT * FROM supplement_taken WHERE logicalDate = :logicalDate")
    suspend fun takenOn(logicalDate: String): List<SupplementTakenEntity>

    /** The day's doses as a stream — a tap on the Supplements tab moves Today's bucket count. */
    @Query("SELECT * FROM supplement_taken WHERE logicalDate = :logicalDate")
    fun takenStream(logicalDate: String): Flow<List<SupplementTakenEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaken(row: SupplementTakenEntity)

    @Query("DELETE FROM supplement_taken WHERE logicalDate = :logicalDate AND supplementId = :id")
    suspend fun clearTaken(logicalDate: String, id: String)

    @Query("SELECT COUNT(*) FROM supplements")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(supplements: List<SupplementEntity>)

    @Query("DELETE FROM supplements")
    suspend fun clear()
}
