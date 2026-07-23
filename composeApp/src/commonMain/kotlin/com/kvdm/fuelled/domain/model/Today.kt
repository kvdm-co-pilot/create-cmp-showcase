package com.kvdm.fuelled.domain.model

/**
 * The daily macro dashboard's domain models (see specs/today.spec.md). Pure Kotlin, no
 * framework types — the shape the presentation renders and the data layer aggregates its
 * flat `today_goal` + `today_log` rows into. Presentation owns display concerns (macro
 * colours are assigned per-macro in the screen, never carried here — ARCH-02 keeps domain
 * free of Compose types).
 */

/** One tracked macro's progress toward its daily goal (TODAY-02). */
data class MacroProgress(
    val label: String,
    val current: Int,
    val target: Int,
    val unit: String,
)

/** A single logged food, as rendered in a meal's list (TODAY-03). */
data class LogEntry(
    val name: String,
    val serving: String,
    val kcal: Int,
    val proteinG: Int,
)

/** A meal and its entries; [kcal] is the meal's own total — the sum of its entries (TODAY-03). */
data class MealGroup(
    val name: String,
    val entries: List<LogEntry>,
) {
    val kcal: Int get() = entries.sumOf { it.kcal }
}

/**
 * The aggregate the Today screen renders: the day's calories and macros against goal, plus
 * the day's log grouped by meal. [consumedKcal] equals the sum of every entry's calories
 * (TODAY-03); the data layer computes it — the model never lets the two disagree.
 */
data class TodayModel(
    val dateLabel: String,
    val consumedKcal: Int,
    val targetKcal: Int,
    val protein: MacroProgress,
    val carbs: MacroProgress,
    val fat: MacroProgress,
    val meals: List<MealGroup>,
) {
    val remainingKcal: Int get() = (targetKcal - consumedKcal).coerceAtLeast(0)
}
