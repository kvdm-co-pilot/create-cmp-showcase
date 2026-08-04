package com.kvdm.fuelled.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

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

/**
 * What a reminder is for — the two rhythms the day has (PLAN-07/PLAN-08), plus the day's
 * bookend: the evening nudge when tomorrow is still unplanned (NOTIF-04).
 */
sealed interface ReminderTarget {
    data class Meal(val slot: MealSlot) : ReminderTarget
    data class Water(val index: Int) : ReminderTarget
    data object PlanTomorrow : ReminderTarget

    /** A dose that is not due every day (SUPP-12) — one target per rung of the ladder. */
    data class Supplement(val id: String, val lead: ReminderLead) : ReminderTarget

    /** A training day (WORK-06) — keyed by weekday, because the week is the schedule. */
    data class Workout(val day: DayOfWeek, val lead: ReminderLead) : ReminderTarget
}

/**
 * How far ahead of the thing itself a reminder lands (SUPP-12/WORK-06).
 *
 * A single alarm at the moment of the dose is the design that fails for anything you cannot
 * do instantly: an injection you need to warm, a gym session you have to travel to. So the
 * ladder is three rungs, each answering a different question — *plan for it*, *get ready*,
 * *now* — and each independently switchable, because which of the three actually helps
 * depends on the thing.
 *
 * [NIGHT_BEFORE] is deliberately NOT offered for a daily schedule: "tomorrow is creatine day"
 * is noise, while "tomorrow is injection day" is information. The rung's whole value is that
 * it names an exception.
 */
enum class ReminderLead(val label: String) {
    /** The evening before, at the same moment the plan-tomorrow nudge lands (NOTIF-04). */
    NIGHT_BEFORE("Night before"),

    /** Thirty minutes ahead — travel, warm-up, preparation. */
    THIRTY_MIN("30 min before"),

    /** The moment itself. */
    AT_TIME("At time"),
    ;

    companion object {
        /** What a newly-scheduled item arms when the user has not said otherwise. */
        val DEFAULT: Set<ReminderLead> = entries.toSet()

        /** Read a stored rung back, dropping anything a future build wrote (never throws). */
        fun of(name: String): ReminderLead? = entries.firstOrNull { it.name == name }
    }
}

/**
 * The prep lead (PLAN-07, daily-journeys decision 1): a meal reminder fires this many
 * minutes BEFORE its slot time — the moment a working user can still cook or fetch the
 * meal, not the moment it is already due. Water takes no lead: there is nothing to prep.
 *
 * SET-07 made it the per-user choice PLAN-07 always said it would become; the default lives
 * on [AppSettings] and travels in as an argument, so this policy stays pure and testable at
 * any lead including zero.
 */

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
    /**
     * The date this fires on, for reminders that are NOT daily (SUPP-12/WORK-06).
     *
     * Null keeps the original meaning and the original behaviour: a time of day, armed at its
     * next occurrence — today if it has not passed, otherwise tomorrow. Meals, water and the
     * nudge are all of that kind and stay untouched.
     *
     * A Monday-and-Thursday dose is not. "Next occurrence of 08:00" would ring it every
     * morning, so those reminders carry the exact date they belong to and the scheduler arms
     * that instant rather than guessing one from the clock.
     */
    val onDate: LocalDate? = null,
) {
    val key: String
        get() = when (target) {
            is ReminderTarget.Meal -> "meal_${target.slot.name}"
            is ReminderTarget.Water -> "water_${target.index}"
            is ReminderTarget.PlanTomorrow -> "plan_tomorrow"
            // The id and the rung both ride the key: three rungs of the same dose are three
            // separate alarms that must be replaceable and cancellable independently.
            is ReminderTarget.Supplement -> "supp_${target.id}_${target.lead.name}"
            is ReminderTarget.Workout -> "workout_${target.day.name}_${target.lead.name}"
        }

    /** Which channel this posts to (NOTIF-09) — decided here, so it is testable without an OS. */
    val channel: ReminderChannel
        get() = when (target) {
            is ReminderTarget.Supplement -> ReminderChannel.SUPPLEMENTS
            is ReminderTarget.Workout -> ReminderChannel.WORKOUTS
            else -> ReminderChannel.MEALS
        }
}

/**
 * The notification channels the app posts to (NOTIF-09).
 *
 * Three, not one, because the OS channel IS the off switch this app deliberately offers
 * instead of building its own (NOTIF-07) — and a single channel makes that switch useless:
 * silencing evening training nudges would silence every meal reminder with them.
 *
 * A domain enum rather than a string in the Android scheduler, so "each family has its own
 * channel" is a claim a pure test can hold the app to. The platform maps these to real channel
 * ids; the DECISION lives here.
 */
enum class ReminderChannel(val id: String, val displayName: String, val description: String) {
    MEALS("meal_reminders", "Meal reminders", "Your six meals and the water between them."),
    SUPPLEMENTS("supplement_reminders", "Supplement reminders", "Doses that are not due every day."),
    WORKOUTS("workout_reminders", "Workout reminders", "Your training week."),
}

/** [leadMinutes] before a slot time, clamped at midnight — never wrapped to yesterday. */
internal fun LocalTime.minusPrepLead(leadMinutes: Int): LocalTime =
    LocalTime.fromSecondOfDay((toSecondOfDay() - leadMinutes * 60).coerceAtLeast(0))

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
    leadMinutes: Int = DEFAULT_PREP_LEAD_MINUTES,
): List<MealReminder> {
    val mode = capability.mode
    // SET-07: a lead outside the offered range can only arrive from a corrupted row or a
    // caller that skipped the guard. Clamping beats trusting it — a negative lead would arm
    // reminders AFTER the meal, which is the one thing this policy exists to prevent.
    val lead = leadMinutes.coerceIn(PREP_LEAD_RANGE)
    val meals = MealSlot.entries
        .filter { it !in doneSlots }
        .map {
            MealReminder(
                target = ReminderTarget.Meal(it),
                time = times[it].minusPrepLead(lead),
                mode = mode,
                eventTime = times[it],
            )
        }
    val waters = waterSchedule(times)
        .map { MealReminder(ReminderTarget.Water(it.index), it.time, mode) }
    return (meals + waters).sortedBy { it.time }
}

/** The nudge's offset after the day's last meal moment (NOTIF-04, brief D4). */
const val PLAN_NUDGE_OFFSET_MINUTES: Int = 45

/** The nudge's hard ceiling — it never lands later than 22:00 (NOTIF-04, brief D4). */
val PLAN_NUDGE_LATEST: LocalTime = LocalTime(22, 0)

/**
 * The end-of-day nudge, when there IS one (NOTIF-04/NOTIF-05).
 *
 * Null when tomorrow is planned — including when its plan could not be read, which the caller
 * folds into `tomorrowUnplanned = false`: a nudge fired on unknown state nags a user who may
 * have planned the whole week (NOTIF-05). When it exists it fires [PLAN_NUDGE_OFFSET_MINUTES]
 * after the evening snack, clamped to [PLAN_NUDGE_LATEST] — derived from the user's own times
 * like the water schedule (PLAN-09's discipline), so there is no eighth time setting to keep
 * in step. Denied notifications still return it, carrying [ReminderMode.UNAVAILABLE], for the
 * same honesty [remindersFor] keeps: the list is what the app intends.
 */
fun planTomorrowNudge(
    times: MealTimes,
    tomorrowUnplanned: Boolean,
    capability: ReminderCapability,
): MealReminder? {
    if (!tomorrowUnplanned) return null
    return MealReminder(
        target = ReminderTarget.PlanTomorrow,
        time = times.eveningNudgeTime,
        mode = capability.mode,
    )
}

/**
 * The evening moment this app uses to talk about TOMORROW (NOTIF-04, SUPP-12, WORK-06).
 *
 * [PLAN_NUDGE_OFFSET_MINUTES] after the evening snack, never later than [PLAN_NUDGE_LATEST] —
 * derived from the user's own meal times like the water schedule (PLAN-09's discipline), so
 * moving the evening snack moves it and there is no separate evening setting to keep in step.
 *
 * Extracted because the night-before rung of the reminder ladder needs exactly this instant.
 * Two evening times — one for the plan nudge and one for "tomorrow is injection day" — would
 * be two settings that drift apart and two notifications arriving minutes from each other.
 */
val MealTimes.eveningNudgeTime: LocalTime
    get() {
        val afterLastMeal = this[MealSlot.EVENING_SNACK].toSecondOfDay() +
            PLAN_NUDGE_OFFSET_MINUTES * 60
        return LocalTime.fromSecondOfDay(minOf(afterLastMeal, PLAN_NUDGE_LATEST.toSecondOfDay()))
    }

/** The lead the middle rung of the ladder fires at (SUPP-12/WORK-06). */
const val SHORT_LEAD_MINUTES: Int = 30

/**
 * The next moment [lead] fires for something due on [dueDates], strictly after [now].
 *
 * The heart of the non-daily ladder, and pure: given a way to enumerate due dates it answers
 * *when does this ring next*, with no clock, no alarm manager and no storage. Three things it
 * has to get right, each of which is a bug if it does not:
 *
 * - **Strictly after now.** An alarm armed in the past either fires instantly or never; both
 *   look like a broken app. A rung whose moment has already passed today rolls to the next
 *   due date rather than being dropped, so a dose taken every Monday still has a Monday
 *   reminder after Monday lunchtime — it is just next Monday's.
 * - **Already satisfied days are skipped.** [satisfied] holds the dates whose dose is taken or
 *   whose session is done; a reminder for something already finished is pure noise.
 * - **The night-before rung fires on the day BEFORE.** Which means it can also already have
 *   passed even when the due date has not, and the same roll-forward applies.
 *
 * Returns null when nothing in the lookahead window qualifies — an empty weekday set, or a
 * schedule whose only remaining occurrences are all satisfied.
 */
private fun nextFire(
    dueDates: List<LocalDate>,
    lead: ReminderLead,
    remindAt: LocalTime,
    nightBefore: LocalTime,
    now: LocalDateTime,
    satisfied: Set<LocalDate>,
): LocalDateTime? = dueDates
    .asSequence()
    .filter { it !in satisfied }
    .mapNotNull { due ->
        when (lead) {
            ReminderLead.AT_TIME -> LocalDateTime(due, remindAt)
            ReminderLead.THIRTY_MIN -> LocalDateTime(due, remindAt.minusPrepLead(SHORT_LEAD_MINUTES))
            ReminderLead.NIGHT_BEFORE -> LocalDateTime(due.minus(1, DateTimeUnit.DAY), nightBefore)
        }
    }
    .firstOrNull { it > now }

/** Every date this schedule falls due within the lookahead window, in order. */
private fun SupplementSchedule.dueDatesFrom(from: LocalDate): List<LocalDate> {
    if (this is SupplementSchedule.OnDays && days.isEmpty()) return emptyList()
    // Two full cadences of lookahead: enough that the night-before rung of the SECOND
    // occurrence is reachable when the first is already taken, which is the case that decides
    // whether a Monday/Thursday dose still reminds after Monday's is ticked.
    return (0 until DUE_LOOKAHEAD_DAYS)
        .map { from.plus(it, DateTimeUnit.DAY) }
        .filter { isDueOn(it) }
}

private const val DUE_LOOKAHEAD_DAYS = 30

/**
 * The reminders a supplement stack should currently have armed (SUPP-12).
 *
 * One [MealReminder] per (supplement, rung), each carrying the exact date it fires on. Only
 * supplements with a [Supplement.remindAt] and at least one rung produce anything; a stack
 * where nobody set a time arms nothing, which is the correct behaviour and not a failure.
 *
 * [ReminderLead.NIGHT_BEFORE] is dropped for [SupplementSchedule.Daily] here rather than only
 * being hidden in the editor — a stored rung from a schedule that later became daily must not
 * keep ringing every single evening.
 *
 * Denied notifications still produce the full list carrying [ReminderMode.UNAVAILABLE], for
 * the same honesty [remindersFor] keeps: the list is what the app INTENDS, so a surface can
 * say plainly that reminders are off instead of rendering an empty schedule.
 */
fun supplementReminders(
    stack: List<Supplement>,
    today: LocalDate,
    now: LocalTime,
    nightBefore: LocalTime,
    capability: ReminderCapability,
    takenToday: Set<String> = emptySet(),
): List<MealReminder> {
    val mode = capability.mode
    val nowAt = LocalDateTime(today, now)
    return stack.flatMap { supplement ->
        val at = supplement.remindAt ?: return@flatMap emptyList()
        val dueDates = supplement.schedule.dueDatesFrom(today)
        // Today's dose is already swallowed — skip today's rungs, keep the next occurrence's.
        val satisfied = if (supplement.id in takenToday) setOf(today) else emptySet()
        supplement.leads
            .filterNot { it == ReminderLead.NIGHT_BEFORE && supplement.schedule is SupplementSchedule.Daily }
            .mapNotNull { lead ->
                nextFire(dueDates, lead, at, nightBefore, nowAt, satisfied)?.let { fire ->
                    MealReminder(
                        target = ReminderTarget.Supplement(supplement.id, lead),
                        time = fire.time,
                        mode = mode,
                        eventTime = at,
                        onDate = fire.date,
                    )
                }
            }
    }
}

/**
 * The reminders a training week should currently have armed (WORK-06).
 *
 * The weekday IS the schedule, so each training day is read as a one-day
 * [SupplementSchedule.OnDays] and run through the same [nextFire] the stack uses — one
 * policy, so "night before" cannot come to mean two different moments in two features.
 *
 * [doneDates] silences a session already marked done, exactly as a taken dose is silenced.
 */
fun workoutReminders(
    week: WorkoutWeek,
    today: LocalDate,
    now: LocalTime,
    nightBefore: LocalTime,
    capability: ReminderCapability,
    doneDates: Set<LocalDate> = emptySet(),
): List<MealReminder> {
    val mode = capability.mode
    val nowAt = LocalDateTime(today, now)
    return DayOfWeek.entries.flatMap { day ->
        val plan = week[day]
        val at = plan.remindAt ?: return@flatMap emptyList()
        if (!plan.isTraining) return@flatMap emptyList()
        val dueDates = SupplementSchedule.OnDays(setOf(day)).dueDatesFrom(today)
        plan.leads.mapNotNull { lead ->
            nextFire(dueDates, lead, at, nightBefore, nowAt, doneDates)?.let { fire ->
                MealReminder(
                    target = ReminderTarget.Workout(day, lead),
                    time = fire.time,
                    mode = mode,
                    eventTime = at,
                    onDate = fire.date,
                )
            }
        }
    }
}
