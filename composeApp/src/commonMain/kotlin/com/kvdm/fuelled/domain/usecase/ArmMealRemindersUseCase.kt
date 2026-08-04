package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR
import com.kvdm.fuelled.core.time.RealTimeSignal
import com.kvdm.fuelled.core.time.TimeSignal
import com.kvdm.fuelled.core.time.currentDay
import com.kvdm.fuelled.domain.model.DEFAULT_PREP_LEAD_MINUTES
import com.kvdm.fuelled.domain.model.MealReminder
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.eveningNudgeTime
import com.kvdm.fuelled.domain.model.planTomorrowNudge
import com.kvdm.fuelled.domain.model.remindersFor
import com.kvdm.fuelled.domain.model.supplementReminders
import com.kvdm.fuelled.domain.model.workoutReminders
import com.kvdm.fuelled.domain.repository.AppStateRepository
import com.kvdm.fuelled.domain.repository.MealPlanRepository
import com.kvdm.fuelled.domain.repository.SupplementRepository
import com.kvdm.fuelled.domain.repository.WorkoutRepository
import com.kvdm.fuelled.domain.notification.ReminderScheduler
import com.kvdm.fuelled.domain.model.ReminderCapability
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Re-arm every reminder the app owns, from the current stored state (PLAN-07, SUPP-12,
 * WORK-06).
 *
 * Deliberately a full REPLACE rather than an incremental update, and therefore safe to call
 * from every trigger the clauses name: on app open, after a slot time changes, after a slot is
 * ticked done, after a dose is taken or a schedule edited, after a session is marked done, and
 * from the boot receiver. Re-arming is idempotent because reminder keys are derived from their
 * target, not their time — so calling this too often costs nothing and calling it too rarely is
 * the only real failure mode.
 *
 * **One arm, every feature.** The three reminder families ride the same replace on purpose: the
 * scheduler's contract is "the armed set is exactly this list", so a partial arm that knew only
 * about meals would CANCEL every supplement and workout alarm as a side effect of ticking
 * breakfast. Anything added here in future must join this list, not get its own scheduler call.
 *
 * Returns the reminders that were armed so the caller can *show* them honestly: when the
 * platform denies notifications they come back with [com.kvdm.fuelled.domain.model.ReminderMode.UNAVAILABLE],
 * which is what lets the meal-times sheet state plainly that reminders are off instead of
 * rendering a schedule that will never fire.
 */
class ArmMealRemindersUseCase(
    private val repository: MealPlanRepository,
    private val scheduler: ReminderScheduler,
    private val appState: AppStateRepository,
    private val tomorrowUnplanned: TomorrowUnplannedUseCase,
    /**
     * The stack and the training week. Null means this instance manages only the meal contract
     * — used by the meal-focused tests, never in the wired app, where omitting them would
     * silently cancel two features' alarms on every tick (see the replace note above).
     */
    private val supplements: SupplementRepository? = null,
    private val workouts: WorkoutRepository? = null,
    private val time: TimeSignal = RealTimeSignal(),
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val dayStartHour: Int = DEFAULT_DAY_START_HOUR,
) {
    /**
     * @param doneSlots slots already ticked on the current logical day — their reminders are
     *   cancelled, because a meal already eaten is never announced. Water is untouched by a
     *   meal tick (PLAN-07): the two rhythms are independent.
     *
     *   **Null means "read them", and is the default.** It used to be an empty set, which
     *   quietly made every caller that did not have them to hand — the alarm receiver's
     *   re-arm, the boot receiver, the permission grant — assert that NOTHING was done today.
     *   The result was a meal ticked done in the morning getting its reminder re-armed by the
     *   next alarm to fire, and announcing a meal already eaten. Only a caller that genuinely
     *   knows the day is untouched should pass an empty set.
     */
    suspend operator fun invoke(doneSlots: Set<MealSlot>? = null): AppResult<List<MealReminder>> =
        when (val times = repository.mealTimes()) {
            // A failure to READ the times is passed through untouched (ARCH-06/ARCH-07) rather
            // than swallowed into "armed nothing": arming the Body-for-LIFE defaults over a
            // storage error would quietly ring at times the user had changed months ago.
            is AppResult.Failure -> times
            is AppResult.Success -> {
                // SET-07: the user's lead. A failure to read it falls back to the DEFAULT
                // rather than failing the arm — reminders at 30 minutes are a far better
                // outcome than a day with no reminders because one setting would not load.
                val lead = (appState.current() as? AppResult.Success)?.value?.settings?.prepLeadMinutes
                    ?: DEFAULT_PREP_LEAD_MINUTES
                val capability = scheduler.capability()
                val today = time.currentDay(dayStartHour, zone)
                val now = time.now().toLocalDateTime(zone).time
                val done = doneSlots ?: readDoneSlots()
                // NOTIF-04/NOTIF-05: the evening nudge rides the same replace — so every
                // trigger that re-arms the meals (a plan write, a tick, boot, app open) also
                // arms or silences the nudge, and planning tomorrow cancels it at once.
                val nudge = planTomorrowNudge(times.value, tomorrowUnplanned(), capability)
                // SUPP-12/WORK-06: the night-before rung lands at the SAME evening moment the
                // nudge does, derived from the user's own meal times — one evening, not three.
                val nightBefore = times.value.eveningNudgeTime
                val reminders = remindersFor(times.value, done, capability, lead) +
                    listOfNotNull(nudge) +
                    supplementLadder(today, now, nightBefore, capability) +
                    workoutLadder(today, now, nightBefore, capability)
                scheduler.arm(reminders)
                AppResult.Success(reminders)
            }
        }

    /**
     * The slots ticked done on the current logical day.
     *
     * A read failure yields an empty set rather than failing the whole arm: the cost of being
     * wrong here is one reminder for a meal already eaten, and the cost of failing is a day
     * with no reminders at all.
     */
    private suspend fun readDoneSlots(): Set<MealSlot> {
        val today = time.currentDay(dayStartHour, zone)
        val now = time.now().toLocalDateTime(zone).time
        val plan = repository.planDay(today, today, now)
        return (plan as? AppResult.Success)?.value?.slots
            .orEmpty()
            .filter { it.done }
            .map { it.slot }
            .toSet()
    }

    private suspend fun supplementLadder(
        today: LocalDate,
        now: LocalTime,
        nightBefore: LocalTime,
        capability: ReminderCapability,
    ): List<MealReminder> {
        val repo = supplements ?: return emptyList()
        val stack = (repo.getStack() as? AppResult.Success)?.value ?: return emptyList()
        val taken = (repo.takenOn(today) as? AppResult.Success)?.value.orEmpty()
        return supplementReminders(stack, today, now, nightBefore, capability, taken)
    }

    private suspend fun workoutLadder(
        today: LocalDate,
        now: LocalTime,
        nightBefore: LocalTime,
        capability: ReminderCapability,
    ): List<MealReminder> {
        val repo = workouts ?: return emptyList()
        val week = (repo.week() as? AppResult.Success)?.value ?: return emptyList()
        // A week and a day of lookahead, so a session already done cannot be re-announced and
        // next week's night-before rung is still reachable.
        val horizon = today.plus(WORKOUT_HORIZON_DAYS, DateTimeUnit.DAY)
        val done = (repo.doneBetween(today, horizon) as? AppResult.Success)?.value.orEmpty()
        return workoutReminders(week, today, now, nightBefore, capability, done)
    }

    private companion object {
        const val WORKOUT_HORIZON_DAYS = 8
    }
}
