package com.kvdm.fuelled.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/**
 * One entry in the day's supplement stack: what to take, how much, when in the day, on WHICH
 * days, and whether it has been taken today. The canonical domain model for the Supplements
 * feature — pure Kotlin, no framework types, the shape the presentation renders and the data
 * layer maps its `SupplementEntity` rows into.
 *
 * `timing` is the grouping key the display buckets on (e.g. "Morning", "Pre-workout",
 * "Evening"); the stable ORDER of those buckets is a data concern (the DAO's `timingOrder`
 * column), so it never surfaces on this model — the repository returns the stack already
 * ordered and grouping preserves that first-seen order (mirrors the Today log's meal order).
 *
 * `timing` and [schedule] answer two DIFFERENT questions and are deliberately separate fields:
 * timing is *when in the day*, schedule is *which days*. Folding them together would make a
 * Monday-only evening dose unrepresentable without inventing a timing bucket per weekday.
 *
 * [taken] is a fact about the CURRENT logical day, joined in at the repository seam
 * (SUPP-07) — never a stored flag on this row.
 */
data class Supplement(
    val id: String,
    val name: String,
    val dose: String,
    val timing: SupplementTiming,
    val taken: Boolean,
    /** SUPP-08: which days this is due. Everything was [SupplementSchedule.Daily] before v15. */
    val schedule: SupplementSchedule = SupplementSchedule.Daily,
    /** SUPP-12: the clock time its reminders hang off. Null means this one never reminds. */
    val remindAt: LocalTime? = null,
    /** SUPP-12: which rungs of the ladder are armed. Empty means none, even with [remindAt] set. */
    val leads: Set<ReminderLead> = emptySet(),
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

/**
 * Which days a supplement is due (SUPP-08).
 *
 * A CLOSED set for the same reason [SupplementTiming] is one: due-ness drives the day screen,
 * the summary denominator and every armed alarm, so it must never be expressible as free text
 * a typo can corrupt.
 *
 * **Due-ness is DERIVED, never stored.** [isDueOn] is a pure function of the schedule and a
 * date — there is no "due today" column, no nightly job that marks rows, and therefore nothing
 * that can be stale when the app is opened after a week away. Same discipline as the meal-plan
 * grid coming from the enum rather than from stored rows (PLAN-02) and the logical day being
 * re-derived rather than rolled over (MEAL-02).
 */
sealed interface SupplementSchedule {

    /** Every day. The pre-v15 behaviour, and still the default for a new supplement. */
    data object Daily : SupplementSchedule

    /**
     * Fixed weekdays — the injection-protocol case (Mon & Thu).
     *
     * An EMPTY set is representable and means *never due*. That is deliberate: the editor can
     * hold a half-made choice without the model lying about it, and [nextDueOnOrAfter] answers
     * null rather than looping forever.
     */
    data class OnDays(val days: Set<DayOfWeek>) : SupplementSchedule

    /**
     * Every [n] days from [anchor] — the every-other-day pen.
     *
     * **The anchor does not move when a dose is missed.** Skipping Tuesday on an every-2-days
     * pen leaves Thursday due, exactly as it was; the cadence is a property of the protocol,
     * not of the user's compliance with it. The alternative — restarting the cycle from
     * whenever a dose was actually taken — would make due-ness depend on stored history that
     * can be edited, deleted or absent, and a schedule that cannot be re-derived from its own
     * definition is a schedule that drifts.
     */
    data class EveryNDays(val n: Int, val anchor: LocalDate) : SupplementSchedule

    companion object {
        /** The widest cadence the editor offers — a fortnight. Guards [EveryNDays] arithmetic. */
        val CADENCE_RANGE: IntRange = 2..14
    }
}

/** True when this schedule falls due on [date] (SUPP-08). */
fun SupplementSchedule.isDueOn(date: LocalDate): Boolean = when (this) {
    is SupplementSchedule.Daily -> true
    is SupplementSchedule.OnDays -> date.dayOfWeek in days
    is SupplementSchedule.EveryNDays -> {
        // Guard a corrupted row rather than dividing by zero: a stored n outside the offered
        // range can only come from a hand-edited database or a future build, and reading it as
        // "due every day" is louder — and safer — than a crash on the Supplements tab.
        val step = n.coerceIn(SupplementSchedule.CADENCE_RANGE)
        // floorMod, not %: dates BEFORE the anchor give a negative difference, and Kotlin's %
        // keeps the sign — so an anchor set today would read as not-due for every past day the
        // history screen asks about, at a cadence that is off by one.
        val delta = date.toEpochDays() - anchor.toEpochDays()
        ((delta % step) + step) % step == 0L
    }
}

/**
 * The first date on or after [from] this schedule falls due, or null when it never does
 * (an empty [SupplementSchedule.OnDays]).
 *
 * Bounded at a fortnight of lookahead — the widest [SupplementSchedule.CADENCE_RANGE] cadence,
 * so any schedule that CAN come due does so inside the window and the loop is total by
 * construction rather than by hoping the data is sane.
 */
fun SupplementSchedule.nextDueOnOrAfter(from: LocalDate): LocalDate? {
    if (this is SupplementSchedule.OnDays && days.isEmpty()) return null
    var day = from
    repeat(LOOKAHEAD_DAYS) {
        if (isDueOn(day)) return day
        day = day.plus(1, DateTimeUnit.DAY)
    }
    return null
}

/** The lookahead [nextDueOnOrAfter] searches — one day wider than the widest cadence. */
private const val LOOKAHEAD_DAYS = 15

/**
 * The schedule in the user's words — the caption the day screen and the editor both show
 * (SUPP-09). Derived from the schedule itself so the two surfaces cannot describe the same
 * row differently.
 */
val SupplementSchedule.label: String
    get() = when (this) {
        is SupplementSchedule.Daily -> "Daily"
        is SupplementSchedule.OnDays -> when {
            days.isEmpty() -> "No days set"
            days.size == 7 -> "Daily"
            else -> DayOfWeek.entries
                .filter { it in days }
                .joinToString(" & ") { it.shortLabel }
        }
        is SupplementSchedule.EveryNDays -> "Every $n days"
    }

/** "Mon", "Thu" — the three-letter form both the caption and the day strip use. */
val DayOfWeek.shortLabel: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
