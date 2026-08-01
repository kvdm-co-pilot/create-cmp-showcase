package com.kvdm.fuelled.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.kvdm.fuelled.domain.model.WeightEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * One weigh-in, keyed by LOGICAL day (HIST-06).
 *
 * The logical date IS the primary key, which is what makes "weighed twice this morning"
 * a correction rather than a second data point — the second write replaces the first with no
 * conflict logic to get wrong. Stored in kilograms whatever the display unit is (SET-02): a
 * stored unit that follows a preference silently reinterprets every old row the moment
 * somebody flips the switch.
 */
@Entity(tableName = "weight_log")
data class WeightEntity(
    @PrimaryKey val logicalDate: String,
    val kg: Double,
)

@Dao
interface WeightDao {
    /**
     * The window, observed and ascending — Room re-emits on every write, so recording a
     * weigh-in re-derives the Progress surface with no reload (RS-01).
     */
    @Query("SELECT * FROM weight_log WHERE logicalDate BETWEEN :from AND :to ORDER BY logicalDate")
    fun streamBetween(from: String, to: String): Flow<List<WeightEntity>>

    /** HIST-06: one row per logical day — REPLACE is the correction, not an append. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WeightEntity)
}

fun WeightEntity.toDomain(): WeightEntry = WeightEntry(date = LocalDate.parse(logicalDate), kg = kg)

fun WeightEntry.toEntity(): WeightEntity = WeightEntity(logicalDate = date.toString(), kg = kg)
