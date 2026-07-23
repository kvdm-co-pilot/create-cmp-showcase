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
    val timing: String,
    val taken: Boolean,
)
