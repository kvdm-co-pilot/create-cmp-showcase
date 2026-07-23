package com.kvdm.fuelled.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kvdm.fuelled.domain.model.LogEntry

// ── Room entities for the Today dashboard — the on-device SSOT the repository aggregates ──
// Room can't nest, so the day is modelled FLAT: one goal row (the day's targets) plus many
// log rows (the logged foods). The repository reads both and aggregates them into the domain
// `TodayModel` at the seam, so domain never sees a Room type. Meal order is explicit
// (`mealOrder`/`entryOrder`) — SQLite has no inherent row order to lean on (TODAY-03).

/** The day's goal row: the calorie/macro targets and the date label the dashboard shows. */
@Entity(tableName = "today_goal")
data class TodayGoalEntity(
    @PrimaryKey val id: String,
    val dateLabel: String,
    val targetKcal: Int,
    val proteinTargetG: Int,
    val carbsTargetG: Int,
    val fatTargetG: Int,
)

/**
 * One logged food. Carries the macros the aggregate sums (protein/carbs/fat) even though the
 * screen only renders protein per row — the repository needs them to compute each macro's
 * current-vs-target. `mealOrder`/`entryOrder` give the log a stable, meal-grouped order.
 */
@Entity(tableName = "today_log")
data class LogEntryEntity(
    @PrimaryKey val id: String,
    val meal: String,
    val mealOrder: Int,
    val entryOrder: Int,
    val name: String,
    val serving: String,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
)

/** Map a log row to the domain [LogEntry] the screen renders (protein is the surfaced macro). */
fun LogEntryEntity.toDomain(): LogEntry = LogEntry(
    name = name,
    serving = serving,
    kcal = kcal,
    proteinG = proteinG,
)
