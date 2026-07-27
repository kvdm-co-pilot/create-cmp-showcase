package com.kvdm.fuelled.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kvdm.fuelled.domain.model.MealSlot
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

// ── Room entities for the structured day (specs/meal-plan.spec.md) ───────────────────────
// Only three things about the plan are STORED; everything else is derived on read from these
// plus the log rows (see domain/model/MealPlan.kt): the six slot times, which slots have been
// ticked done on a given day, and which water containers have been ticked on a given day.
//
// What is deliberately absent is the whole point of the shape. There is no water-time table
// (PLAN-09 — water times are midpoints of the meal times, so a stored copy could disagree
// with them), no focus row (PLAN-15 — focus is a function of now), no "missed" flag (PLAN-19
// — same), and no row-per-slot-per-day skeleton (PLAN-02 — the six containers are the enum,
// so an unplanned day needs no rows at all to render).

/**
 * One slot's time (PLAN-05/PLAN-06). Keyed by the [MealSlot] NAME — the closed enum, not an
 * index — so reordering the enum can never silently re-point a stored time at a different meal.
 *
 * The time is stored as [secondOfDay] rather than a "HH:mm" string: it is the form every
 * derivation already works in (midpoints, grace windows, the ascending-order invariant), and a
 * display string would put formatting in the database and make ordering a lexical accident.
 *
 * A slot with no row falls back to its Body-for-LIFE default, so this table is empty until the
 * user actually changes something (PLAN-05: "asks for them once and never prompts again" —
 * the defaults are already an answer).
 */
@Entity(tableName = "meal_slot_time")
data class SlotTimeEntity(
    @PrimaryKey val slot: String,
    val secondOfDay: Int,
)

/**
 * A slot ticked done on one logical day (PLAN-13/PLAN-14). Presence IS the tick — there is no
 * `done: Boolean` column, because a row that says false is indistinguishable in meaning from
 * no row at all, and two encodings of the same fact is how they drift.
 *
 * Crucially this is SEPARATE from the log entries: PLAN-14 makes ticking an empty container a
 * completion with no food, so done-ness cannot be inferred from "has LOGGED entries". Eaten
 * off-plan is a real outcome in this method, and fabricating a food to represent it would
 * corrupt the day's totals to satisfy the data model.
 */
@Entity(tableName = "meal_slot_done", primaryKeys = ["logicalDate", "slot"])
data class SlotDoneEntity(
    val logicalDate: String,
    val slot: String,
)

/**
 * One water container ticked on one logical day (PLAN-10). [index] is its 1..6 position in the
 * day, which is derived from slot order — so moving a meal time moves *when* container 3 is
 * due (PLAN-09) without disturbing the fact that container 3 was drunk.
 *
 * Per-day by construction: a new logical day simply has no rows, so it starts at 0.0 L with
 * nothing to reset (PLAN-10).
 */
@Entity(tableName = "meal_water_tick", primaryKeys = ["logicalDate", "waterIndex"])
data class WaterTickEntity(
    val logicalDate: String,
    val waterIndex: Int,
)

/** The stored row's slot as the closed enum (PLAN-01). Throws on a malformed name, inside the
 *  repository's `suspendRunCatching`, so it surfaces as a typed failure — never a silent skip. */
val SlotTimeEntity.mealSlot: MealSlot get() = MealSlot.valueOf(slot)

/** The stored row's time, back from its second-of-day form. */
val SlotTimeEntity.localTime: LocalTime get() = LocalTime.fromSecondOfDay(secondOfDay)

/** Map a slot time into its row. */
fun slotTimeEntity(slot: MealSlot, time: LocalTime): SlotTimeEntity =
    SlotTimeEntity(slot = slot.name, secondOfDay = time.toSecondOfDay())

/** Map a done-tick into its row. */
fun slotDoneEntity(date: LocalDate, slot: MealSlot): SlotDoneEntity =
    SlotDoneEntity(logicalDate = date.toString(), slot = slot.name)

/** Map a water tick into its row. */
fun waterTickEntity(date: LocalDate, index: Int): WaterTickEntity =
    WaterTickEntity(logicalDate = date.toString(), waterIndex = index)
