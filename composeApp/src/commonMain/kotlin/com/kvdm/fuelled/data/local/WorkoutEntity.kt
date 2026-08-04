package com.kvdm.fuelled.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.kvdm.fuelled.domain.model.WorkoutDayPlan
import com.kvdm.fuelled.domain.model.WorkoutWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

// ── Room entities for training (WORK-01, schema v15) ─────────────────────────────────────
// Two tables, for the reason the supplement stack has two: the WEEK is a plan you edit, and a
// DONE-MARK is a fact about a date. Keeping them apart is what lets Wednesday change from
// Lower body to Rest without rewriting the Wednesdays you already trained.

/**
 * One day of the training week (WORK-02). Keyed by weekday name — exactly seven rows, ever.
 *
 * A null [label] is the rest day. [remindAtMinute] is per-day (WORK-07): a weekday session
 * after work and a Saturday morning session are the normal shape of a week, and one time for
 * all seven would be wrong on most of them.
 */
@Entity(tableName = "workout_week")
data class WorkoutDayEntity(
    @PrimaryKey val dayOfWeek: String,
    val label: String?,
    val remindAtMinute: Int?,
    /** CSV of `ReminderLead` names — the same codec the supplement rows use. */
    val leads: String,
)

/**
 * One session marked done, on one logical day (WORK-04).
 *
 * The row's EXISTENCE is the fact — the same shape as a supplement dose (SUPP-07) and a water
 * tick (PLAN-10), for the same reason: a new logical day simply has no row, so it starts
 * undone with nothing to reset and no boundary job to run. A `done` boolean column would let
 * a row exist saying `false`, which is indistinguishable from never having trained and is one
 * more state to keep honest.
 */
@Entity(tableName = "workout_done")
data class WorkoutDoneEntity(
    @PrimaryKey val logicalDate: String,
)

@Dao
interface WorkoutDao {
    // ── The week (WORK-02/WORK-07) ───────────────────────────────────────────────────────

    @Query("SELECT * FROM workout_week")
    suspend fun week(): List<WorkoutDayEntity>

    /** The week as a stream — editing a day in Settings re-renders Today with no reload. */
    @Query("SELECT * FROM workout_week")
    fun weekStream(): Flow<List<WorkoutDayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(day: WorkoutDayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeek(days: List<WorkoutDayEntity>)

    @Query("SELECT COUNT(*) FROM workout_week")
    suspend fun count(): Int

    // ── The done-marks (WORK-04/WORK-05) ─────────────────────────────────────────────────
    // Set/clear rather than a toggle, for the reason MealPlanDao states: a toggle's result
    // depends on state the caller did not read, so two taps racing land on either answer.

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDone(row: WorkoutDoneEntity)

    @Query("DELETE FROM workout_done WHERE logicalDate = :logicalDate")
    suspend fun clearDone(logicalDate: String)

    @Query("SELECT * FROM workout_done WHERE logicalDate BETWEEN :from AND :to")
    suspend fun doneBetween(from: String, to: String): List<WorkoutDoneEntity>

    /**
     * The done-marks in a window, observed.
     *
     * `BETWEEN` on ISO-8601 date strings is a lexicographic comparison that happens to be the
     * chronological one — zero-padded, big-endian, fixed width. True for every date this app
     * can hold, and the same assumption every other dated query here already makes.
     */
    @Query("SELECT * FROM workout_done WHERE logicalDate BETWEEN :from AND :to")
    fun doneBetweenStream(from: String, to: String): Flow<List<WorkoutDoneEntity>>
}

// ── Mapping (the repository seam — domain never sees a Room type) ────────────────────────

/** Read the seven stored rows into the total week, defaulting anything absent to rest. */
fun List<WorkoutDayEntity>.toWeek(): WorkoutWeek = WorkoutWeek(
    mapNotNull { row ->
        val day = DayOfWeek.entries.firstOrNull { it.name == row.dayOfWeek } ?: return@mapNotNull null
        day to WorkoutDayPlan(
            // Blank is rest, the same as absent: an editor that cleared the field must not
            // leave a training day whose name is the empty string.
            label = row.label?.takeIf { it.isNotBlank() },
            remindAtMinute = row.remindAtMinute,
            leads = row.leads,
        )
    }.toMap(),
)

private fun WorkoutDayPlan(label: String?, remindAtMinute: Int?, leads: String) = WorkoutDayPlan(
    label = label,
    remindAt = remindAtMinute?.let { LocalTime.fromSecondOfDay(it.coerceIn(0, MINUTES_IN_DAY - 1) * 60) },
    leads = leads.decodeLeads(),
)

fun WorkoutDayPlan.toEntity(day: DayOfWeek): WorkoutDayEntity = WorkoutDayEntity(
    dayOfWeek = day.name,
    label = label,
    // A rest day has nothing to be reminded of, so its time and rungs are dropped rather than
    // carried — otherwise turning Wednesday into a rest day would leave a live alarm behind.
    remindAtMinute = if (isTraining) remindAt?.let { it.hour * 60 + it.minute } else null,
    leads = if (isTraining) leads.encodeLeads() else "",
)

fun WorkoutWeek.toEntities(): List<WorkoutDayEntity> =
    DayOfWeek.entries.map { day -> this[day].toEntity(day) }

fun workoutDoneEntity(date: LocalDate): WorkoutDoneEntity =
    WorkoutDoneEntity(logicalDate = date.toString())
