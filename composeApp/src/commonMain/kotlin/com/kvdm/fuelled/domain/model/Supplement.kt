package com.kvdm.fuelled.domain.model

/**
 * One entry in the day's supplement stack: what to take, how much, and when in the day, plus
 * whether it has been taken today. The canonical domain model for the Supplements feature —
 * pure Kotlin, no framework types, the shape the presentation renders and the data layer maps
 * its `SupplementEntity` rows into.
 *
 * `timing` is the grouping key the display buckets on (e.g. "Morning", "Pre-workout",
 * "Evening"); the stable ORDER of those buckets is a data concern (the DAO's `timingOrder`
 * column), so it never surfaces on this model — the repository returns the stack already
 * ordered and grouping preserves that first-seen order (mirrors the Today log's meal order).
 */
data class Supplement(
    val id: String,
    val name: String,
    val dose: String,
    val timing: SupplementTiming,
    val taken: Boolean,
)

/**
 * When in the day a supplement is taken (SET-06).
 *
 * A CLOSED set, and its ordinal IS the stack's display order — one fact, so grouping and
 * ordering cannot disagree. It was free text while the stack was seed data nobody could edit;
 * the moment SET-04 let users type it, `"Morning"` and `"morning"` would have rendered as two
 * separate buckets with no way for the app to know they were the same time of day.
 */
enum class SupplementTiming(val label: String) {
    MORNING("Morning"),
    PRE_WORKOUT("Pre-workout"),
    POST_WORKOUT("Post-workout"),
    EVENING("Evening"),
    ;

    companion object {
        /** Read a stored value back, falling back to [MORNING] rather than throwing on a legacy row. */
        fun of(name: String?): SupplementTiming =
            entries.firstOrNull { it.name == name } ?: MORNING
    }
}
