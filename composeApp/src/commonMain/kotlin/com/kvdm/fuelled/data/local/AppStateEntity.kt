package com.kvdm.fuelled.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * The app's own state, and its owner's settings, in one row (schema v11, START-01/START-02,
 * SET-02/SET-07).
 *
 * [onboarded] gates the first-run interview. [startedAtEpochMs] is the instant the app was
 * first opened, which is what lets a first day distinguish "you skipped breakfast" from
 * "breakfast happened before this app existed" — the framing defect journey J3 found.
 * One row, fixed key, like the profile and the goal.
 *
 * [unitSystem] and [prepLeadMinutes] join them rather than each getting a table (settings
 * decision D8): typed columns beat a key-value bag — four columns cannot hold a malformed
 * value, and every read is already parsed — and sharing the row means one observed stream
 * re-renders every surface when any of it changes.
 */
@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val id: String,
    val onboarded: Boolean,
    val startedAtEpochMs: Long,
    val unitSystem: String,
    val prepLeadMinutes: Int,
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
