package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.MealTimes
import com.kvdm.fuelled.domain.repository.MealPlanRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.datetime.LocalTime

/**
 * Move one slot's time (PLAN-06), and re-arm the reminders that depend on it.
 *
 * Re-arming belongs HERE rather than in the sheet: the clause says a changed time re-arms its
 * notification, and a UI that had to remember to call a second thing afterwards is a UI that
 * will eventually forget. Because [ArmMealRemindersUseCase] replaces the whole armed set, this
 * also moves the two WATER reminders either side of the slot (PLAN-09) without anyone
 * computing which ones they were.
 *
 * The coercion into the neighbouring window happens in the domain ([MealTimes.withTime]) and
 * again nothing here has to remember it — the value that comes back is the value that was
 * stored, which may not be the value that was asked for.
 */
class SetMealTimeUseCase(
    private val repository: MealPlanRepository,
    private val armReminders: ArmMealRemindersUseCase,
) {
    /**
     * @param doneSlots slots already ticked today, so the re-arm keeps their reminders
     *   cancelled (PLAN-07) instead of resurrecting a reminder for a meal already eaten.
     */
    suspend operator fun invoke(
        slot: MealSlot,
        time: LocalTime,
        doneSlots: Set<MealSlot> = emptySet(),
    ): AppResult<MealTimes> {
        val stored = repository.setMealTime(slot, time)
        // Only re-arm once the write succeeded: arming against times that were never persisted
        // would leave the alarms and the sheet disagreeing until the next app open.
        if (stored is AppResult.Success) armReminders(doneSlots)
        return stored
    }
}
