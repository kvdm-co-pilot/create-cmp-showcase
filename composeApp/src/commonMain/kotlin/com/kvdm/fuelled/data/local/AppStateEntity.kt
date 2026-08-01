package com.kvdm.fuelled.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * The app's own state — not the user's data and not a setting: the two facts the app knows
 * about ITSELF (schema v10, START-01/START-02).
 *
 * [onboarded] gates the first-run interview. [startedAtEpochMs] is the instant the app was
 * first opened, which is what lets a first day distinguish "you skipped breakfast" from
 * "breakfast happened before this app existed" — the framing defect journey J3 found.
 * One row, fixed key, like the profile and the goal.
 */
@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val id: String,
    val onboarded: Boolean,
    val startedAtEpochMs: Long,
)

@Dao
interface AppStateDao {
    @Query("SELECT * FROM app_state LIMIT 1")
    suspend fun get(): AppStateEntity?

    /** The state as a stream — the shell observes it so finishing onboarding swaps the UI. */
    @Query("SELECT * FROM app_state LIMIT 1")
    fun stream(): kotlinx.coroutines.flow.Flow<AppStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: AppStateEntity)
}
