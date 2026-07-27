package com.kvdm.fuelled.domain.model

import kotlinx.datetime.LocalTime

/**
 * The meal-log aggregate's domain models (see specs/meal.spec.md). Pure Kotlin, no framework
 * types — the vocabulary the write path, the tray, and the day's grouping are all written in
 * (ARCH-02). Presentation owns display concerns: the slot's user-facing label and its icon
 * live in the screen, never here.
 */

/**
 * Which meal of the logical day an entry belongs to (MEAL-03, PLAN-01) — a closed enum,
 * deliberately not the free `meal: String` the read-only Today model carried. A free-text meal
 * name makes "Brekkie" and "breakfast" two different groups and makes grouping unqueryable.
 *
 * **Declaration order IS slot order.** A day's entries group and order by this enum's natural
 * order — `entries` / `compareTo` — so this is the order the day's containers appear in.
 * Reordering these constants silently reorders the app.
 *
 * The three snacks are distinct constants, not one generic `SNACK`: with three snack containers
 * on screen every day (PLAN-02), "a snack" is not an identity — you cannot say which container
 * a row belongs to, which is the whole job of this enum.
 */
enum class MealSlot { BREAKFAST, MORNING_SNACK, LUNCH, AFTERNOON_SNACK, DINNER, EVENING_SNACK }

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
    /**
     * Copied off the catalog [Food] at write time (PLAN-22), not looked up later. A log row is
     * a SNAPSHOT — it already carries its own name, serving and macros rather than a foreign
     * key — so veg-ness travels with it for the same reason: re-flagging a catalog food must
     * not silently rewrite what last Tuesday's veg count was.
     */
    val veg: Boolean = false,
)

// `slotForLocalTime` lived here: the tray's time-of-day slot preselect (MEAL-04, withdrawn).
// Every way into the tray now opens it already aimed at a specific container (MEAL-10,
// PLAN-04), so there is no untargeted open left to guess a slot for. Deleted rather than kept
// "just in case" — an unused classifier is exactly what later gets wired back in by accident.
