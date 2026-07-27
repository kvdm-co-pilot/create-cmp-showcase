package com.kvdm.fuelled.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kvdm.fuelled.domain.model.LogEntry
import com.kvdm.fuelled.domain.model.LogStatus
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.NewLogEntry
import kotlinx.datetime.LocalDate

// ── Room entities for the Today dashboard — the on-device SSOT the repository aggregates ──
// Room can't nest, so the day is modelled FLAT: one goal row (the day's targets) plus many
// log rows (the logged foods). The repository reads both and aggregates them into the domain
// `TodayModel` at the seam, so domain never sees a Room type.

/**
 * The day's goal row: the calorie/macro targets.
 *
 * It deliberately carries NO date: the day in view is DERIVED from the current instant on
 * every read (MEAL-02/TODAY-01), so a stored `dateLabel` would be a second, staler truth —
 * and a display string at that, which nothing could ever query by.
 */
@Entity(tableName = "today_goal")
data class TodayGoalEntity(
    @PrimaryKey val id: String,
    val targetKcal: Int,
    val proteinTargetG: Int,
    val carbsTargetG: Int,
    val fatTargetG: Int,
)

/**
 * One log row. Carries the macros the aggregate sums (protein/carbs/fat) even though the
 * screen only renders protein per row — the repository needs them to compute each macro's
 * current-vs-target.
 *
 * [logicalDate] is the ISO-8601 date of the LOGICAL day the entry belongs to (MEAL-01), so a
 * day is a query rather than "whatever rows exist". [slot] stores a [MealSlot] NAME and
 * [status] a [LogStatus] name — enum values, not free text (MEAL-03).
 *
 * There is no `mealOrder` column: slot order is the enum's declaration order, derived from
 * `MealSlot.ordinal` when the repository builds the groups. A stored copy of it would be a
 * second truth that silently disagrees the day someone reorders the enum. [entryOrder] orders
 * entries WITHIN one `(logicalDate, slot)` — SQLite has no inherent row order to lean on.
 */
@Entity(tableName = "today_log")
data class LogEntryEntity(
    @PrimaryKey val id: String,
    val logicalDate: String,
    val slot: String,
    val status: String,
    val entryOrder: Int,
    val name: String,
    val serving: String,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    /**
     * Snapshotted off the catalog food at write time (PLAN-22) — the day's veg count is a fact
     * about what was eaten that day, so re-flagging a catalog entry must not retroactively
     * change it. Same reason this row carries its own name and macros instead of a food id.
     */
    val veg: Boolean = false,
)

/** The row's slot as the closed enum the domain groups by (MEAL-03). */
val LogEntryEntity.mealSlot: MealSlot get() = MealSlot.valueOf(slot)

/** The row's status as the closed enum the day's totals filter on (MEAL-08). */
val LogEntryEntity.logStatus: LogStatus get() = LogStatus.valueOf(status)

/**
 * Map a log row to the domain [LogEntry] the screen renders (protein is the surfaced macro).
 * A malformed enum name throws here, inside the repository's `suspendRunCatching`, so it
 * surfaces as a typed `DomainError` like any other source failure — never as a silent default.
 */
fun LogEntryEntity.toDomain(): LogEntry = LogEntry(
    id = id,
    name = name,
    serving = serving,
    kcal = kcal,
    proteinG = proteinG,
    status = logStatus,
    veg = veg,
)

/** Map a tray line to the row it becomes, stamped with the confirm's target (MEAL-05). */
fun NewLogEntry.toEntity(
    logicalDate: LocalDate,
    slot: MealSlot,
    status: LogStatus,
    entryOrder: Int,
): LogEntryEntity = LogEntryEntity(
    id = id,
    logicalDate = logicalDate.toString(),
    slot = slot.name,
    status = status.name,
    entryOrder = entryOrder,
    name = name,
    serving = serving,
    kcal = kcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    veg = veg,
)
