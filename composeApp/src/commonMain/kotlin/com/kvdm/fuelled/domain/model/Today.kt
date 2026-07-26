package com.kvdm.fuelled.domain.model

import kotlinx.datetime.LocalDate

/**
 * The daily macro dashboard's domain models (see specs/today.spec.md). Pure Kotlin, no
 * framework types — the shape the presentation renders and the data layer aggregates its
 * flat `today_goal` + `today_log` rows into. Presentation owns display concerns (macro
 * colours are assigned per-macro in the screen, never carried here — ARCH-02 keeps domain
 * free of Compose types; the day's date and the slot's label are FORMATTED in the screen,
 * so the model carries the value and never its presentation).
 */

/** One tracked macro's progress toward its daily goal (TODAY-02). */
data class MacroProgress(
    val label: String,
    val current: Int,
    val target: Int,
    val unit: String,
)

/**
 * A single food on the day's log, as rendered in a meal's list (TODAY-03).
 *
 * [id] is the entry's identity: deleting one (MEAL-06) and marking a planned one logged
 * (MEAL-07) both address it by id, so the read model has to carry it — a row the UI can see
 * but not name is a row the UI cannot act on.
 *
 * [status] tells a scheduled entry from an eaten one. BOTH render in their meal group; only
 * `LOGGED` counts toward the day's consumed total (TODAY-03/MEAL-08). It defaults to `LOGGED`
 * because that is what the overwhelming majority of entries are — the tests that care about
 * the planned case state it explicitly rather than relying on the default.
 */
data class LogEntry(
    val id: String,
    val name: String,
    val serving: String,
    val kcal: Int,
    val proteinG: Int,
    val status: LogStatus = LogStatus.LOGGED,
)

/**
 * A meal and its entries; [kcal] is the meal's own total — the sum of its entries (TODAY-03).
 *
 * The group is keyed by the closed [MealSlot] enum, never a free-text meal name (MEAL-03):
 * "Brekkie" and "breakfast" would otherwise be two groups. Slot ORDER is the enum's
 * declaration order, and the data layer emits the groups in it. The user-facing label
 * ("Breakfast") is rendered in the screen — the domain carries the value, never its
 * formatting.
 */
data class MealGroup(
    val slot: MealSlot,
    val entries: List<LogEntry>,
) {
    val kcal: Int get() = entries.sumOf { it.kcal }
}

/**
 * The aggregate the Today screen renders: the day's calories and macros against goal, plus
 * the day's log grouped by meal slot.
 *
 * [date] is the LOGICAL day in view (TODAY-01/MEAL-01) — a real date, re-derived from the
 * current instant on every read (MEAL-02), not a stored display string. Presentation formats
 * it.
 *
 * [consumedKcal] equals the sum of the calories of the day's **`LOGGED`** entries — a
 * `PLANNED` entry is scheduled, not eaten, and never counts as consumed (TODAY-03/MEAL-08).
 * The data layer computes it, and each [MacroProgress.current] the same way, so the model
 * never lets the ring and the macro bars disagree with each other.
 */
data class TodayModel(
    val date: LocalDate,
    val consumedKcal: Int,
    val targetKcal: Int,
    val protein: MacroProgress,
    val carbs: MacroProgress,
    val fat: MacroProgress,
    val meals: List<MealGroup>,
) {
    val remainingKcal: Int get() = (targetKcal - consumedKcal).coerceAtLeast(0)
}
