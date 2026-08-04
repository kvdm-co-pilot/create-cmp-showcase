package com.kvdm.fuelled.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Training — the day's sixth pillar (WORK-01).
 *
 * Body-for-LIFE was never only about food: the day's verdict already carries calories,
 * protein, meals kept, water and veg (JRN-01), and training is the one discipline the app
 * asked about nowhere. This model is deliberately the SMALLEST thing that closes that gap —
 * a labelled week, a per-day reminder time, and a done-mark per logical day.
 *
 * **What this is not.** No exercises, sets, reps, loads, durations or progression. Those are
 * a training log, which is a different product with a different data model, and building a
 * hollow version of one is worse than not having it. The done-mark is keyed by logical date,
 * so detail can hang off it later without changing what a "done day" already means.
 */

/**
 * One day of the training week (WORK-02): what it is, and when to be reminded.
 *
 * A null [label] IS the rest day — rest is a first-class part of a training week, not the
 * absence of a plan, so it needs no separate flag. [remindAt] is PER DAY on purpose: a
 * weekday session after work and a Saturday morning session are the normal shape of a real
 * week, and one time for all seven would be wrong on most of them.
 *
 * [remindAt] on a rest day is meaningless and is dropped at the data seam rather than
 * carried — there is nothing to be reminded of.
 */
data class WorkoutDayPlan(
    val label: String? = null,
    val remindAt: LocalTime? = null,
    /** WORK-06: which rungs of the ladder are armed for this day. */
    val leads: Set<ReminderLead> = emptySet(),
) {
    /** A training day, as opposed to a rest day. */
    val isTraining: Boolean get() = label != null
}

/**
 * The training week (WORK-02) — seven days, always all seven.
 *
 * Held as a total map rather than a list of training days for the same reason the meal plan
 * renders all six containers whatever is stored (PLAN-02): the WEEK is the grid, and a day
 * missing from a collection is indistinguishable from a day nobody has set up yet. [get]
 * therefore always answers, defaulting to rest.
 */
data class WorkoutWeek(
    val days: Map<DayOfWeek, WorkoutDayPlan> = emptyMap(),
) {
    operator fun get(day: DayOfWeek): WorkoutDayPlan = days[day] ?: WorkoutDayPlan()

    /** The plan for [date]'s weekday — the day screen's question, asked in date terms. */
    fun on(date: LocalDate): WorkoutDayPlan = get(date.dayOfWeek)

    /** How many days of this week are training days — the week summary's denominator. */
    val trainingDays: Int get() = DayOfWeek.entries.count { get(it).isTraining }

    companion object {
        /**
         * The classic Body-for-LIFE split, seeded on first run (WORK-08).
         *
         * Upper/lower alternating with cardio between and Sunday free — the programme's own
         * shape, so a fresh install shows a real week rather than seven blanks that make the
         * feature look broken. Times are seeded empty: a reminder at a time nobody chose is
         * exactly the kind of unasked-for alarm that gets an app's notifications switched off.
         */
        val DEFAULT: WorkoutWeek = WorkoutWeek(
            mapOf(
                DayOfWeek.MONDAY to WorkoutDayPlan("Upper body"),
                DayOfWeek.TUESDAY to WorkoutDayPlan("Cardio"),
                DayOfWeek.WEDNESDAY to WorkoutDayPlan("Lower body"),
                DayOfWeek.THURSDAY to WorkoutDayPlan("Cardio"),
                DayOfWeek.FRIDAY to WorkoutDayPlan("Upper body"),
                DayOfWeek.SATURDAY to WorkoutDayPlan("Cardio"),
                DayOfWeek.SUNDAY to WorkoutDayPlan(),
            ),
        )
    }
}

/**
 * A training day as the day screen and the week strip see it (WORK-03/WORK-05).
 *
 * [done] is a fact about a logical day, stored as its own row exactly as a supplement dose is
 * (SUPP-07) and a water tick is (PLAN-10) — so a new day starts undone because it has no row,
 * not because anything reset it.
 */
data class WorkoutDay(
    val date: LocalDate,
    val plan: WorkoutDayPlan,
    val done: Boolean,
) {
    /** Whether this day asks anything of the user at all. */
    val isTraining: Boolean get() = plan.isTraining

    /**
     * The state the week strip renders (WORK-05). Ordering matters: a REST day is never
     * MISSED, and today is never MISSED either — a day still in progress has not been failed.
     */
    fun state(today: LocalDate): WorkoutDayState = when {
        !isTraining -> WorkoutDayState.REST
        done -> WorkoutDayState.DONE
        date >= today -> WorkoutDayState.PENDING
        else -> WorkoutDayState.MISSED
    }
}

/** How one day of the training week reads at a glance (WORK-05). */
enum class WorkoutDayState { DONE, MISSED, PENDING, REST }
