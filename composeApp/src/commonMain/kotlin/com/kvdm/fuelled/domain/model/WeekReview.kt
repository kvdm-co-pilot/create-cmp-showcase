package com.kvdm.fuelled.domain.model

import kotlinx.datetime.LocalDate

/**
 * The week in review (specs/daily-journeys.spec.md JRN-01) — the holistic look back the
 * journeys walkthrough found missing: seven logical days of results, derived entirely from
 * the SAME aggregates every other surface reads ([PlanDay], [TodayModel]). Nothing here is
 * stored and nothing is a second read path — the data always existed; only the surface was
 * missing.
 */

/** How many logical days the review shows, today included. */
const val WEEK_REVIEW_DAYS: Int = 7

/**
 * A protein day is HIT at ≥95% of goal. 176 g against a 180 g goal is not a failed day to
 * any human who trains — a binary at-goal count graded a strong week "1/6" (seen on the
 * rendered surface, 2026-08-01) and a review that scolds near-misses teaches people to stop
 * opening it. The day cards still show exact grams; only the week's verdict is tolerant.
 */
const val PROTEIN_DAY_TOLERANCE: Double = 0.95

/**
 * One day's results row. Consumed values count only `LOGGED` entries (TODAY-03) — a
 * `PLANNED` entry on a day that never happened is a stale plan, not food eaten.
 *
 * [targetKcal] and [proteinGoalG] are the CURRENT goals: goals are not yet dated
 * (usability-pass S1 owns that decision), and the contract states it so nobody mistakes a
 * seven-day-constant target for a bug.
 */
data class WeekDay(
    val date: LocalDate,
    val isToday: Boolean,
    val consumedKcal: Int,
    val targetKcal: Int,
    val proteinG: Int,
    val proteinGoalG: Int,
    val slotsDone: Int,
    val slotsTotal: Int,
    val waterMl: Int,
    val vegMeals: Int,
) {
    /** This day's protein verdict, at the week's tolerance ([PROTEIN_DAY_TOLERANCE]). */
    val proteinDayHit: Boolean
        get() = proteinGoalG > 0 && proteinG >= proteinGoalG * PROTEIN_DAY_TOLERANCE
}

/**
 * The seven rows, ascending by date, today last (JRN-01) — plus the week's HEADLINE,
 * derived: the answer to "how am I doing?" must be readable before a single day card is.
 * Days still in flight count honestly: today's protein can't be "hit" at breakfast, so the
 * hit-count excludes today unless it is already at goal — a review must never mark the
 * current day as a failure in progress.
 */
data class WeekReview(val days: List<WeekDay>) {
    /** Days at or above [PROTEIN_DAY_TOLERANCE] of goal. Today counts only once already there. */
    val proteinDaysHit: Int
        get() = days.count { it.proteinDayHit }

    /** Days judged for the headline: past days always; today only once it can score. */
    val proteinDaysJudged: Int
        get() = days.count { !it.isToday || it.proteinDayHit }

    val mealsDone: Int get() = days.sumOf { it.slotsDone }
    val mealsTotal: Int get() = days.sumOf { it.slotsTotal }

    /** Average consumed over the days that have anything logged — an empty day is unstarted, not zero-scoring. */
    val avgConsumedKcal: Int
        get() = days.filter { it.consumedKcal > 0 }.let { started ->
            if (started.isEmpty()) 0 else started.sumOf { it.consumedKcal } / started.size
        }
}

/** Fold one [PlanDay] into its results row — pure, testable at any chosen day. */
fun weekDayOf(plan: PlanDay, isToday: Boolean, targetKcal: Int, proteinGoalG: Int): WeekDay {
    val logged = plan.slots.flatMap { it.entries }.filter { it.status == LogStatus.LOGGED }
    return WeekDay(
        date = plan.date,
        isToday = isToday,
        consumedKcal = logged.sumOf { it.kcal },
        targetKcal = targetKcal,
        proteinG = logged.sumOf { it.proteinG },
        proteinGoalG = proteinGoalG,
        slotsDone = plan.slots.count { it.done },
        slotsTotal = MealSlot.entries.size,
        waterMl = plan.waterMl,
        vegMeals = plan.vegMeals,
    )
}
