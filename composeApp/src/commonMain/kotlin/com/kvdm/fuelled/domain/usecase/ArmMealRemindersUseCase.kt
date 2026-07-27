package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.MealReminder
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.remindersFor
import com.kvdm.fuelled.domain.repository.MealPlanRepository
import com.kvdm.fuelled.domain.notification.ReminderScheduler
import com.kvdm.fuelled.domain.result.AppResult

/**
 * Re-arm the day's reminders from the current stored state (PLAN-07).
 *
 * Deliberately a full REPLACE rather than an incremental update, and therefore safe to call
 * from every trigger the clause names: on app open, after a slot time changes, after a slot is
 * ticked done, and from the boot receiver. Re-arming is idempotent because reminder keys are
 * derived from their target, not their time — so calling this too often costs nothing and
 * calling it too rarely is the only real failure mode.
 *
 * Returns the reminders that were armed so the caller can *show* them honestly: when the
 * platform denies notifications they come back with [com.kvdm.fuelled.domain.model.ReminderMode.UNAVAILABLE],
 * which is what lets the meal-times sheet state plainly that reminders are off instead of
 * rendering a schedule that will never fire.
 */
class ArmMealRemindersUseCase(
    private val repository: MealPlanRepository,
    private val scheduler: ReminderScheduler,
) {
    /**
     * @param doneSlots slots already ticked on the current logical day — their reminders are
     *   cancelled, because a meal already eaten is never announced. Water is untouched by a
     *   meal tick (PLAN-07): the two rhythms are independent.
     */
    suspend operator fun invoke(doneSlots: Set<MealSlot> = emptySet()): AppResult<List<MealReminder>> =
        when (val times = repository.mealTimes()) {
            // A failure to READ the times is passed through untouched (ARCH-06/ARCH-07) rather
            // than swallowed into "armed nothing": arming the Body-for-LIFE defaults over a
            // storage error would quietly ring at times the user had changed months ago.
            is AppResult.Failure -> times
            is AppResult.Success -> {
                val reminders = remindersFor(times.value, doneSlots, scheduler.capability())
                scheduler.arm(reminders)
                AppResult.Success(reminders)
            }
        }
}
