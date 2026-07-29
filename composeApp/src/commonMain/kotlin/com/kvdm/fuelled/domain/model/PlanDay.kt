package com.kvdm.fuelled.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * One day of the structured plan, fully resolved for rendering (specs/meal-plan.spec.md).
 *
 * This is the aggregate the plan screen and Today's highlights both read — the *same* value,
 * which is what makes TODAY-13 true by construction rather than by discipline: there is one
 * derived state and one write path, so the two surfaces cannot disagree about whether lunch is
 * next.
 *
 * Everything here except the entries and the ticks is DERIVED (see [buildPlanDay]). Nothing in
 * this file is stored.
 */

/** The method's rule: vegetables with at least two meals (PLAN-22). Surfaced, never enforced. */
const val VEG_MEAL_GOAL: Int = 2

/**
 * One meal container as rendered (PLAN-02). Always present, empty or not — a day is never a
 * blank page, so this is built from the [MealSlot] enum, not from whatever rows happen to exist.
 *
 * [focused], [late] and [missed] are punctuality claims and are therefore ALL false on any day
 * that is not the current logical day (PLAN-23): a Thursday being planned on Tuesday is not
 * running late, and last Friday's abandoned dinner is history, not an outstanding task.
 */
data class PlanSlotView(
    val slot: MealSlot,
    val time: LocalTime,
    val entries: List<LogEntry>,
    val done: Boolean,
    val focused: Boolean,
    val late: Boolean,
    val missed: Boolean,
    /**
     * PLAN-25: its time has arrived — the clock is at or past [time] on the current day.
     *
     * Distinct from [late], which only starts once the grace has run out: between those two
     * points a slot is due and not yet late, and BEFORE them it is the day's next meal but not
     * yet anything to act on. A surface with only `focused` and `late` cannot tell a 07:02
     * breakfast from a 09:30 snack seen at 07:02, which is how the plan came to announce a slot
     * two and a half hours early (observed on-device, 2026-07-29). Like every other punctuality
     * claim here it is false on any day but the current one (PLAN-23).
     */
    val due: Boolean = false,
) {
    /**
     * PLAN-14: ticked with nothing in it — eaten off-plan, or skipped and closed out. A real
     * outcome with no food attached, which is why done-ness is stored separately from entries.
     */
    val tickedEmpty: Boolean get() = done && entries.isEmpty()

    /** PLAN-21: planned, the day has moved on, and it was never logged. History, not a promise. */
    val stalePlan: Boolean get() = !done && entries.any { it.status == LogStatus.PLANNED }
}

/** One water container as rendered (PLAN-08/PLAN-10): its slot in the day, when, and whether drunk. */
data class PlanWaterView(
    val index: Int,
    val time: LocalTime,
    val done: Boolean,
)

/**
 * A day of the plan. [isCurrentDay] gates every punctuality claim (PLAN-23) — it is passed in
 * rather than computed here so the domain stays clock-free and every test is deterministic.
 */
data class PlanDay(
    val date: LocalDate,
    val isCurrentDay: Boolean,
    val slots: List<PlanSlotView>,
    val water: List<PlanWaterView>,
) {
    /** Millilitres drunk — 500 per ticked container (PLAN-10). */
    val waterMl: Int get() = water.count { it.done } * WATER_CONTAINER_ML

    /**
     * PLAN-22: how many meal containers hold at least one vegetable. Counted per CONTAINER, not
     * per food — three portions of broccoli at dinner is one meal with veg, which is what the
     * method's rule is actually about.
     */
    val vegMeals: Int get() = slots.count { view -> view.entries.any { it.veg } }

    /** The focused container, or null — no day but the current one has one (PLAN-15/PLAN-23). */
    val focusedSlot: PlanSlotView? get() = slots.firstOrNull { it.focused }
}

/**
 * Resolve a day into its rendered form (PLAN-02/PLAN-08/PLAN-15..PLAN-23).
 *
 * Pure: given the stored facts — which entries exist, which slots and waters are ticked, what
 * the slot times are — plus [now] and whether this is the current day, the whole surface falls
 * out. No clock is read here, so every derivation is testable at a chosen minute.
 *
 * @param now the current local time. Only consulted when [isCurrentDay] — a past or future day
 *   makes no punctuality claims at all, so its slots come back neither focused, late, nor
 *   missed no matter what the clock says (PLAN-23).
 */
fun buildPlanDay(
    date: LocalDate,
    isCurrentDay: Boolean,
    now: LocalTime,
    times: MealTimes,
    entriesBySlot: Map<MealSlot, List<LogEntry>>,
    doneSlots: Set<MealSlot>,
    waterTicks: Set<Int>,
): PlanDay {
    // Punctuality is a property of *now*, so it is derived once for the current day and simply
    // never asked for on any other (PLAN-23).
    val missed = if (isCurrentDay) missedSlots(doneSlots, now, times) else emptySet()
    val focus = if (isCurrentDay) focusFor(doneSlots, now, times) else null
    val focusedSlot = (focus as? DayFocus.Slot)

    return PlanDay(
        date = date,
        isCurrentDay = isCurrentDay,
        // Walk the ENUM, not the rows: all six containers render whether or not anything was
        // ever written to them (PLAN-02). This is why an unplanned day needs no stored skeleton.
        slots = MealSlot.entries.map { slot ->
            PlanSlotView(
                slot = slot,
                time = times[slot],
                entries = entriesBySlot[slot].orEmpty(),
                done = slot in doneSlots,
                focused = focusedSlot?.slot == slot,
                late = focusedSlot?.slot == slot && focusedSlot.late,
                missed = slot in missed,
                due = isCurrentDay && now >= times[slot],
            )
        },
        water = waterSchedule(times).map { container ->
            PlanWaterView(
                index = container.index,
                time = container.time,
                done = container.index in waterTicks,
            )
        },
    )
}
