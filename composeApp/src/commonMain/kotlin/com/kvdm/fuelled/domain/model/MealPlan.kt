package com.kvdm.fuelled.domain.model

import kotlinx.datetime.LocalTime

/**
 * The structured day's domain models (see specs/meal-plan.spec.md). Pure Kotlin, no framework
 * types (ARCH-02) — the six containers, when they are due, when their water is due, and which
 * one the day is currently aimed at.
 *
 * Everything in this file is **derived, never stored**: given the slot times and which slots
 * are done, focus, lateness, and every water time are pure functions of those inputs. That is
 * the point — a stored "current meal" is a second truth that goes stale the moment the clock
 * moves or the user ticks something, which is exactly the drift this shape avoids.
 */

/** One 500 ml water container (PLAN-08). Six a day; the goal is the six of them. */
const val WATER_CONTAINER_ML: Int = 500

/**
 * The six containers' day goal in millilitres — 3.0 L (PLAN-08). Derived from the slot count,
 * so the goal cannot drift out of step with the number of containers actually on screen.
 */
val WATER_DAY_GOAL_ML: Int = WATER_CONTAINER_ML * MealSlot.entries.size

/** How long after a slot's time it stays merely *next* before it reads as late (PLAN-16). */
const val LATE_GRACE_MINUTES: Int = 30

/** The last water reminder's offset after the evening snack — there is no "next meal" (PLAN-08). */
private const val TRAILING_WATER_OFFSET_MINUTES: Int = 75

private const val SECONDS_PER_MINUTE = 60

/** Two slots can sit no closer than this — keeps the timetable strictly ascending (PLAN-06). */
private const val MIN_SLOT_GAP_SECONDS = 15 * SECONDS_PER_MINUTE

/** The day's hard ceiling for derived times — the last reminder never wraps (PLAN-08). */
private val END_OF_DAY = LocalTime(23, 59)

/**
 * The Body-for-LIFE rhythm as shipped defaults (PLAN-05): six meals roughly 2–3 hours apart.
 * A profile that has never opened the times sheet uses exactly these, and is never prompted.
 */
val DEFAULT_MEAL_TIMES: Map<MealSlot, LocalTime> = mapOf(
    MealSlot.BREAKFAST to LocalTime(7, 0),
    MealSlot.MORNING_SNACK to LocalTime(9, 30),
    MealSlot.LUNCH to LocalTime(12, 0),
    MealSlot.AFTERNOON_SNACK to LocalTime(14, 30),
    MealSlot.DINNER to LocalTime(17, 0),
    MealSlot.EVENING_SNACK to LocalTime(19, 30),
)

/**
 * When each container is due (PLAN-05/PLAN-06). Stored per slot; a slot with no stored time
 * falls back to its default, so a partially-filled settings row can never leave a container
 * timeless — every container always has a time to show and to arm a notification at.
 */
data class MealTimes(private val overrides: Map<MealSlot, LocalTime> = emptyMap()) {
    operator fun get(slot: MealSlot): LocalTime =
        overrides[slot] ?: DEFAULT_MEAL_TIMES.getValue(slot)

    /** Every slot's time in slot order — the day as a timetable. */
    fun inSlotOrder(): List<Pair<MealSlot, LocalTime>> = MealSlot.entries.map { it to this[it] }

    /**
     * The window a slot's time may occupy: strictly between its neighbours (PLAN-06,
     * decision 15). Slot ORDER is fixed by the enum; a "dinner at 05:00" would silently
     * break every derivation that assumes the day runs forwards — water midpoints go
     * negative, focus order stops matching clock order — so the ordering is a domain
     * invariant, not a UI nicety. The times sheet constrains its picker to this window.
     */
    fun validTimeRange(slot: MealSlot): ClosedRange<LocalTime> {
        val i = slot.ordinal
        val floor = if (i == 0) LocalTime(0, 0)
        else LocalTime.fromSecondOfDay(this[MealSlot.entries[i - 1]].toSecondOfDay() + MIN_SLOT_GAP_SECONDS)
        val ceil = if (i == MealSlot.entries.size - 1) LocalTime(23, 59)
        else LocalTime.fromSecondOfDay(this[MealSlot.entries[i + 1]].toSecondOfDay() - MIN_SLOT_GAP_SECONDS)
        return floor..ceil
    }

    /**
     * PLAN-06: one slot's time changes; no other slot's time moves — and the change is
     * COERCED into [validTimeRange], so no sequence of writes can ever produce a
     * non-ascending timetable. The sheet should never offer an out-of-range value; this
     * coercion is the domain's own guarantee, not the UI's.
     */
    fun withTime(slot: MealSlot, time: LocalTime): MealTimes {
        val range = validTimeRange(slot)
        val coerced = when {
            time < range.start -> range.start
            time > range.endInclusive -> range.endInclusive
            else -> time
        }
        return MealTimes(overrides + (slot to coerced))
    }
}

/**
 * One water container: its position in the day (1..6), the meal it follows, and when it is due
 * (PLAN-08). Derived from [MealTimes] on every read — see [waterSchedule].
 */
data class WaterContainer(
    val index: Int,
    val afterSlot: MealSlot,
    val time: LocalTime,
) {
    val millilitres: Int get() = WATER_CONTAINER_ML
}

/**
 * The day's six water reminders (PLAN-08/PLAN-09): each sits at the **midpoint** between its
 * meal and the next, and the last sits 75 minutes after the evening snack, which has no next
 * meal to split the difference with.
 *
 * Deriving rather than storing is what makes PLAN-09 true for free: move a meal time and the
 * water on either side of it moves with it, because there was never a separate water setting
 * to leave behind. Water times are never asked for and never edited directly.
 */
fun waterSchedule(times: MealTimes = MealTimes()): List<WaterContainer> {
    val slots = MealSlot.entries
    return slots.mapIndexed { i, slot ->
        val thisSecond = times[slot].toSecondOfDay()
        val second = if (i + 1 < slots.size) {
            (thisSecond + times[slots[i + 1]].toSecondOfDay()) / 2
        } else {
            // The evening snack has no next meal to split the difference with: 75 minutes
            // after it, CLAMPED to 23:59 (PLAN-08) — a 23:30 snack must not push its water
            // past midnight, where it would sort to the top of the day it belongs to the
            // bottom of. Midpoints cannot overflow (times are strictly ascending, PLAN-06);
            // only this trailing offset can.
            minOf(thisSecond + TRAILING_WATER_OFFSET_MINUTES * SECONDS_PER_MINUTE, END_OF_DAY.toSecondOfDay())
        }
        WaterContainer(index = i + 1, afterSlot = slot, time = LocalTime.fromSecondOfDay(second))
    }
}

/**
 * Which container the current logical day is aimed at (PLAN-15/PLAN-16/PLAN-17). A day is
 * either pointed at one of its own slots — next, or late once the grace has run out — or it is
 * behind you (everything done or missed) and pointed at tomorrow's breakfast. There is no
 * "nothing to show" arm: a day that pointed at nothing is precisely the dead end the fixed
 * structure exists to remove.
 */
sealed interface DayFocus {
    /** The earliest not-done, not-missed slot of the day. [late] once past [LATE_GRACE_MINUTES]. */
    data class Slot(val slot: MealSlot, val late: Boolean) : DayFocus

    /** Every slot is done or missed — the day has advanced (PLAN-17). */
    data object NextDayBreakfast : DayFocus
}

/**
 * The slots the day has moved past without eating (PLAN-19, decision 14): not done, and the
 * NEXT slot's time has arrived. Missing a meal is ROUTINE in this method — the 09:30 snack
 * at work, the weekly free day — so a missed slot is a quiet third state, not an error: it
 * stops competing for focus (the fix for "Snack, late since 09:30" still nagging at 19:00)
 * but stays fully back-fillable. The last slot has no successor and never reads missed —
 * the day's own end rolls focus forward instead (MEAL-02).
 */
fun missedSlots(doneSlots: Set<MealSlot>, now: LocalTime, times: MealTimes = MealTimes()): Set<MealSlot> {
    val slots = MealSlot.entries
    return slots.filterIndexed { i, slot ->
        slot !in doneSlots && i + 1 < slots.size && now >= times[slots[i + 1]]
    }.toSet()
}

/**
 * The focused container for a day (PLAN-15/PLAN-16/PLAN-17), given which of its slots are
 * already done and what time it is now: the earliest slot that is neither done nor missed.
 * Lateness is therefore naturally bounded — a slot can be late for at most the gap to the
 * next meal, after which it is missed and focus moves on (PLAN-16).
 *
 * Only ever called for the **current** logical day: focus, lateness, and missed-ness are
 * properties of now (PLAN-23), so a day being planned three days ahead has no focus at all
 * (PLAN-15) — callers rendering another day simply do not ask.
 */
fun focusFor(doneSlots: Set<MealSlot>, now: LocalTime, times: MealTimes = MealTimes()): DayFocus {
    val missed = missedSlots(doneSlots, now, times)
    val next = MealSlot.entries.firstOrNull { it !in doneSlots && it !in missed }
        ?: return DayFocus.NextDayBreakfast
    val graceSecond = times[next].toSecondOfDay() + LATE_GRACE_MINUTES * SECONDS_PER_MINUTE
    return DayFocus.Slot(slot = next, late = now.toSecondOfDay() > graceSecond)
}
