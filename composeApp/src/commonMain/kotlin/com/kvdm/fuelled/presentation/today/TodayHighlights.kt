package com.kvdm.fuelled.presentation.today

import com.kvdm.fuelled.domain.model.PlanDay
import com.kvdm.fuelled.domain.model.PlanSlotView
import com.kvdm.fuelled.domain.model.PlanWaterView
import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.domain.model.TodayModel

/**
 * What the Today tab renders (TODAY-09..TODAY-14, brief decision 13).
 *
 * Today belongs to no single feature: the ring and macros come from the meal log, the focused
 * container and next water from the structured day, the bucket from the supplement stack. It is
 * the derived "now" across all three — which is exactly why it is assembled here, in
 * presentation, rather than by inventing a domain aggregate that would have to know about every
 * feature the dashboard ever surfaces.
 *
 * [plan] is the SAME [PlanDay] value the plan screen renders. Not a copy, not a parallel
 * projection — the same derivation, so TODAY-13 holds by construction: the two surfaces cannot
 * disagree about whether lunch is next, because there is nothing for them to disagree with.
 */
data class TodayHighlights(
    val today: TodayModel,
    val plan: PlanDay,
    val supplements: SupplementBucket?,
) {
    /** The one container Today shows (TODAY-09) — or null on a day that is fully done. */
    val focus: PlanSlotView? get() = plan.focusedSlot

    /** The next water not yet drunk (TODAY-10) — null once all six are ticked. */
    val nextWater: PlanWaterView? get() = plan.water.firstOrNull { !it.done }
}

/**
 * The supplement highlight (TODAY-11). Bucket-based, not time-based: [Supplement] carries a
 * free-text `timing` and a `taken` flag and no clock time at all, so "the current bucket" is
 * genuinely underivable from the model. The honest summary is the first bucket with anything
 * outstanding — the next thing to take — falling back to the last bucket once the day is done.
 */
data class SupplementBucket(
    val name: String,
    val taken: Int,
    val total: Int,
)

/**
 * Pick the bucket to highlight (TODAY-11/SUPP-02). First bucket with something outstanding, in
 * the stack's own order; if everything is taken, the last one, so the highlight reads "4 of 4"
 * rather than vanishing the moment you finish — a highlight that disappears on success looks
 * like a bug.
 */
fun List<Supplement>.currentBucket(): SupplementBucket? {
    if (isEmpty()) return null
    // groupBy preserves first-seen order, and the repository returns the stack already ordered
    // by its timingOrder column — so bucket order here is the stack's order, not alphabetical.
    val buckets = groupBy { it.timing }
    val chosen = buckets.entries.firstOrNull { (_, items) -> items.any { !it.taken } }
        ?: buckets.entries.last()
    return SupplementBucket(
        name = chosen.key,
        taken = chosen.value.count { it.taken },
        total = chosen.value.size,
    )
}
