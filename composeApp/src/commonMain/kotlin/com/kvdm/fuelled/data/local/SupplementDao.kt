package com.kvdm.fuelled.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SupplementDao {
    // Timing-grouped, stable order — the repository returns this order and grouping preserves it.
    @Query("SELECT * FROM supplements ORDER BY timingOrder")
    suspend fun getAll(): List<SupplementEntity>

    // The tap-to-take write: persists the taken state so it survives a reload (SUPP-03).
    @Query("UPDATE supplements SET taken = :taken WHERE id = :id")
    suspend fun setTaken(id: String, taken: Boolean)

    @Query("SELECT COUNT(*) FROM supplements")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(supplements: List<SupplementEntity>)

    @Query("DELETE FROM supplements")
    suspend fun clear()
}
