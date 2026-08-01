package com.kvdm.fuelled.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kvdm.fuelled.domain.model.DeletedEntry
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
 * The day-zero targets, seeded once into the ONE goal store (PERS-01) — shared by
 * [com.kvdm.fuelled.data.remote.TodayRepositoryImpl] and
 * [com.kvdm.fuelled.data.remote.ProfileRepositoryImpl], whichever reads first, so neither
 * carries its own copy of these numbers (a second seed constant is how F5 happened).
 */
val DEFAULT_TODAY_GOAL = TodayGoalEntity(
    id = "current",
    targetKcal = 2400,
    proteinTargetG = 180,
    carbsTargetG = 260,
    fatTargetG = 70,
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
    /** The base serving label ("100 g") — the MULTIPLE is [servings], never baked in here. */
    val serving: String,
    // The macros are PER SERVING (schema v10, ENTRY-01): the row stores the base and the
    // multiple separately so a serving can be edited in place afterwards. Storing pre-
    // multiplied totals made "2 x" unrecoverable — you cannot divide back out without
    // knowing what was multiplied. `toDomain()` does the multiplication on read.
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    /** ENTRY-01: how many servings of [serving] this row is. Editable after the fact. */
    val servings: Int = 1,
    /**
     * CAT-03: which catalog food this came from, for recents. Empty for rows written before
     * the column existed (and for anything never sourced from the catalog) — recents simply
     * skip those rather than inventing a provenance.
     */
    val foodId: String = "",
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
    // The rendered serving states the multiple: "2 x 100 g". One place builds this label, so
    // the tray, the plan screen, and an in-place serving edit can never phrase it differently.
    serving = if (servings == 1) serving else "$servings x $serving",
    kcal = kcal * servings,
    proteinG = proteinG * servings,
    status = logStatus,
    veg = veg,
    servings = servings,
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
    servings = servings,
    foodId = foodId,
    veg = veg,
)

/** ENTRY-02: the row as the domain's undo record, read the moment before it is deleted. */
fun LogEntryEntity.toDeleted(): DeletedEntry = DeletedEntry(
    id = id,
    foodId = foodId,
    date = LocalDate.parse(logicalDate),
    slot = mealSlot,
    status = logStatus,
    entryOrder = entryOrder,
    name = name,
    serving = serving,
    kcal = kcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    servings = servings,
    veg = veg,
)

/** ENTRY-02: the undo record back as the row it was — same id, so the restore is idempotent. */
fun DeletedEntry.toEntity(): LogEntryEntity = LogEntryEntity(
    id = id,
    logicalDate = date.toString(),
    slot = slot.name,
    status = status.name,
    entryOrder = entryOrder,
    name = name,
    serving = serving,
    kcal = kcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    servings = servings,
    foodId = foodId,
    veg = veg,
)
