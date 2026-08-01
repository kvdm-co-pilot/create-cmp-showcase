package com.kvdm.fuelled.domain.model

import kotlinx.datetime.LocalTime

/**
 * What the app promises to remind you about, and how honestly it can (PLAN-07).
 *
 * The whole point of this file is that the *policy* — which reminders should exist, at what
 * time, in which delivery mode — is pure and testable, while the platform's alarm plumbing sits
 * behind a port. Notification behavior is otherwise the classic thing that only gets found
 * wrong on a real device three weeks later.
 */

/**
 * How a reminder will actually be delivered.
 *
 * [WINDOWED_INEXACT] is not a failure — Android reserves exact alarms for a short list of
 * genuine use cases and applies Doze batching to the rest, so a meal reminder that is allowed
 * to drift by a few minutes is the *normal* outcome, not the degraded one. What the clause
 * forbids is the third possibility that platforms make easy: arming nothing and saying nothing.
 */
enum class ReminderMode {
    /** The platform permits exact alarms and this one is armed exactly. */
    EXACT,

    /** Armed in a window; the OS may batch it (Doze drifts it by up to ~15 minutes). */
    WINDOWED_INEXACT,

    /** Notifications are denied outright — nothing will be delivered, and the sheet says so. */
    UNAVAILABLE,
}

/**
 * What the platform will currently let the app do. Read from the OS at the seam and passed in,
 * so the policy below stays pure.
 *
 * @param notificationsAllowed the runtime notification permission (Android 13+ can deny it, and
 *   a user can revoke it at any time afterwards).
 * @param exactAlarmsAllowed whether `SCHEDULE_EXACT_ALARM` is currently granted.
 */
data class ReminderCapability(
    val notificationsAllowed: Boolean,
    val exactAlarmsAllowed: Boolean,
) {
    /** The mode every armed reminder will use under this capability. */
    val mode: ReminderMode
        get() = when {
            !notificationsAllowed -> ReminderMode.UNAVAILABLE
            exactAlarmsAllowed -> ReminderMode.EXACT
            else -> ReminderMode.WINDOWED_INEXACT
        }
}

/** What a reminder is for — the two rhythms the day has (PLAN-07/PLAN-08). */
sealed interface ReminderTarget {
    data class Meal(val slot: MealSlot) : ReminderTarget
    data class Water(val index: Int) : ReminderTarget
}

/**
 * The prep lead (PLAN-07, daily-journeys decision 1): a meal reminder fires this many
 * minutes BEFORE its slot time — the moment a working user can still cook or fetch the
 * meal, not the moment it is already due. A named constant until the reminders settings
 * surface (usability-pass S5) makes it a per-user choice; water takes no lead.
 */
const val PREP_LEAD_MINUTES: Int = 30

/**
 * One armed daily reminder. [key] is stable across re-arms — derived from the target, never
 * from the time — so moving a meal time RE-arms that slot's reminder rather than adding a
 * second one at the new time and leaving the old one ringing (PLAN-06).
 *
 * [time] is when the reminder FIRES; [eventTime] is the moment it is ABOUT (the slot time
 * for a meal, the midpoint for water). For meals the two differ by the prep lead (PLAN-07),
 * and the notification says both: "Lunch at 12:00 — time to prep."
 */
data class MealReminder(
    val target: ReminderTarget,
    val time: LocalTime,
    val mode: ReminderMode,
    val eventTime: LocalTime = time,
) {
    val key: String
        get() = when (target) {
            is ReminderTarget.Meal -> "meal_${target.slot.name}"
            is ReminderTarget.Water -> "water_${target.index}"
        }
}

/** [PREP_LEAD_MINUTES] before a slot time, clamped at midnight — never wrapped to yesterday. */
internal fun LocalTime.minusPrepLead(): LocalTime =
    LocalTime.fromSecondOfDay((toSecondOfDay() - PREP_LEAD_MINUTES * 60).coerceAtLeast(0))

/**
 * The reminders that should currently be armed (PLAN-07).
 *
 * Six meals at their **prep lead** — [PREP_LEAD_MINUTES] before each slot time, carrying the
 * slot time as [MealReminder.eventTime] so the notification can name the meal's actual moment
 * — plus six waters at the derived midpoints, untouched by any lead (nothing to prep); minus
 * the meal slots already ticked done today, whose reminders are cancelled because a meal
 * already eaten is never announced. Water is deliberately unaffected by a meal tick: they are
 * separate rhythms, and ticking lunch says nothing about whether you drank.
 *
 * When notifications are denied every reminder still appears here, carrying
 * [ReminderMode.UNAVAILABLE]. That is on purpose: the list is what the app *intends*, so the
 * times sheet can state plainly that reminders are off (PLAN-07) instead of silently rendering
 * an empty schedule that looks like nothing was ever set up.
 */
fun remindersFor(
    times: MealTimes,
    doneSlots: Set<MealSlot>,
    capability: ReminderCapability,
): List<MealReminder> {
    val mode = capability.mode
    val meals = MealSlot.entries
        .filter { it !in doneSlots }
        .map {
            MealReminder(
                target = ReminderTarget.Meal(it),
                time = times[it].minusPrepLead(),
                mode = mode,
                eventTime = times[it],
            )
        }
    val waters = waterSchedule(times)
        .map { MealReminder(ReminderTarget.Water(it.index), it.time, mode) }
    return (meals + waters).sortedBy { it.time }
}
