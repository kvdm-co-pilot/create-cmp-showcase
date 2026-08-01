package com.kvdm.fuelled.domain.model

import kotlinx.datetime.LocalDate

/**
 * The look back beyond a week (specs/history.spec.md HIST-01/HIST-05).
 *
 * The week review answers "how was this week?"; this answers "am I getting anywhere?" — the
 * question people install a tracker for and the one seven days structurally cannot answer.
 *
 * Like [WeekReview], nothing here is stored and nothing is a second read path: a [History] is
 * a fold over the same [WeekDay] rows the day cards render, which are themselves folds over
 * the same [PlanDay] derivation every other surface reads. One derivation, several
 * projections — so the trend and the day it is drawn from can never disagree (history
 * decision D5).
 */

/** How many weeks the trend spans. */
const val TREND_WEEKS: Int = 4

/** The window the history stream covers, today included. */
const val TREND_DAYS: Int = TREND_WEEKS * WEEK_REVIEW_DAYS

/**
 * One week of the trend. Its numbers mirror [WeekReview]'s so a week row and the day cards
 * below it are the same arithmetic — the trend is a smaller rendering of the review, never a
 * second opinion about it.
 */
data class WeekTrend(val days: List<WeekDay>) {
    val start: LocalDate get() = days.first().date
    val end: LocalDate get() = days.last().date

    /**
     * HIST-05/decision D6: a week you had not started tracking yet has NO data — it must
     * never render as a bar at zero. "You averaged 0 kcal" is a false statement about a week
     * the app was not installed for, and a trend that opens with two empty weeks reads as
     * failure rather than as absence.
     */
    val hasData: Boolean get() = days.any { it.consumedKcal > 0 }

    /** Averaged over STARTED days only, for the same reason [WeekReview] is. */
    val avgConsumedKcal: Int
        get() = days.filter { it.consumedKcal > 0 }.let { started ->
            if (started.isEmpty()) 0 else started.sumOf { it.consumedKcal } / started.size
        }

    /** The target these days were judged against (goals are undated — see [WeekDay]). */
    val targetKcal: Int get() = days.lastOrNull()?.targetKcal ?: 0

    val proteinDaysHit: Int get() = days.count { it.proteinDayHit }

    /** Days that can be judged: past days always, the current day only once already at goal. */
    val proteinDaysJudged: Int get() = days.count { !it.isToday || it.proteinDayHit }

    val mealsDone: Int get() = days.sumOf { it.slotsDone }
    val mealsTotal: Int get() = days.sumOf { it.slotsTotal }
}

/**
 * [TREND_DAYS] of results, ascending by date with the current day last.
 *
 * Both projections come off this one list: [week] is the seven-day verdict (JRN-01) and
 * [weeks] is the four-week trend (HIST-05). That is the whole point of the shape — the
 * surface renders two time-scales of one truth.
 */
data class History(val days: List<WeekDay>) {
    /** The last seven days — the verdict surface's existing contract, unchanged. */
    val week: WeekReview get() = WeekReview(days.takeLast(WEEK_REVIEW_DAYS))

    /** Oldest week first, the current (still in flight) week last. */
    val weeks: List<WeekTrend> get() = days.chunked(WEEK_REVIEW_DAYS).map(::WeekTrend)
}
