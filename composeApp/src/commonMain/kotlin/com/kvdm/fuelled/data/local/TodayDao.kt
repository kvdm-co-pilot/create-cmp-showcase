package com.kvdm.fuelled.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TodayDao {
    @Query("SELECT * FROM today_goal LIMIT 1")
    suspend fun goal(): TodayGoalEntity?

    // Meal-grouped, stable order — the repository groups on this order to build the log (TODAY-03).
    @Query("SELECT * FROM today_log ORDER BY mealOrder, entryOrder")
    suspend fun entries(): List<LogEntryEntity>

    @Query("SELECT COUNT(*) FROM today_goal")
    suspend fun goalCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: TodayGoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntries(entries: List<LogEntryEntity>)

    @Query("DELETE FROM today_log")
    suspend fun clearEntries()
}
