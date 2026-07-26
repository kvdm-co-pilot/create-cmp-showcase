package com.kvdm.fuelled.domain.model

import kotlinx.datetime.LocalTime

/**
 * The meal-log aggregate's domain models (see specs/meal.spec.md). Pure Kotlin, no framework
 * types — the vocabulary the write path, the tray, and the day's grouping are all written in
 * (ARCH-02). Presentation owns display concerns: the slot's user-facing label and its icon
 * live in the screen, never here.
 */

/**
 * Which meal of the logical day an entry belongs to (MEAL-03) — a closed enum, deliberately
 * not the free `meal: String` the read-only Today model carried. A free-text meal name makes
 * "Brekkie" and "breakfast" two different groups and makes grouping unqueryable.
 *
 * **Declaration order IS slot order.** A day's entries group and order by this enum's natural
 * order — `entries` / `compareTo` — so BREAKFAST, LUNCH, DINNER, SNACK is the order the Today
 * screen's meal cards appear in. Reordering these constants silently reorders the app; add
 * new slots at the end, or the ordering the day relies on changes with them.
 */
enum class MealSlot { BREAKFAST, LUNCH, DINNER, SNACK }

/**
 * Whether an entry has been eaten or is planned ahead — the one flag that makes scheduling
 * and logging the same write with a different target.
 *
 * Declared here as part of the data model; the behavior it drives (a `PLANNED` entry starting
 * to count toward the day's total when it is marked logged, and the tray writing `PLANNED`
 * for a future logical date) is the write path, specified separately and built with it.
 */
enum class LogStatus { LOGGED, PLANNED }

/**
 * One item on its way INTO the log — a tray line, before it is a row (MEAL-05). Deliberately
 * not [LogEntry]: the read model carries only what the Today screen renders (protein is the
 * surfaced macro), while a write has to carry every macro the day's totals are computed from.
 *
 * [id] is supplied by the caller, not minted inside the repository. The tray line already has
 * a stable identity, and a client-generated id makes the confirm write idempotent: a retry
 * after a dropped write replaces the same rows instead of duplicating the meal.
 *
 * The target — logical date, slot, and status — is NOT carried per item: it belongs to the
 * confirm, which writes every item to the same `(date, slot)` in one transaction.
 */
data class NewLogEntry(
    val id: String,
    val name: String,
    val serving: String,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
)

private val BREAKFAST_START = LocalTime(4, 0)
private val LUNCH_START = LocalTime(10, 30)
private val DINNER_START = LocalTime(15, 0)
private val DINNER_END = LocalTime(21, 0)

/**
 * The slot the add-to-meal tray preselects when it opens at [time] (MEAL-04): Breakfast for
 * 04:00–10:30, Lunch for 10:30–15:00, Dinner for 15:00–21:00, and Snack for everything else —
 * the small hours before 04:00 and the late evening from 21:00.
 *
 * Every window is half-open, `[start, end)`, so each instant has exactly one slot and the
 * boundaries never overlap: 10:30 sharp is LUNCH, 21:00 sharp is SNACK.
 *
 * This is a *preselection*, not a classification. The tray shows it pre-set and one tap
 * changes it — the failure mode this whole function exists to avoid is an auto-assignment
 * that is hard to override, which is what lands a 2pm meal in Breakfast.
 */
fun slotForLocalTime(time: LocalTime): MealSlot = when {
    time < BREAKFAST_START -> MealSlot.SNACK
    time < LUNCH_START -> MealSlot.BREAKFAST
    time < DINNER_START -> MealSlot.LUNCH
    time < DINNER_END -> MealSlot.DINNER
    else -> MealSlot.SNACK
}
