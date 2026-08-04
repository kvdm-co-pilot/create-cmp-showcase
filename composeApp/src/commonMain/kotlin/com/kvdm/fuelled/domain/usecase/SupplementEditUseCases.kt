package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.domain.model.WorkoutDayPlan
import com.kvdm.fuelled.domain.repository.SupplementRepository
import com.kvdm.fuelled.domain.repository.WorkoutRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.datetime.DayOfWeek

/**
 * SET-04/SET-05: the stack becomes the user's.
 *
 * The guards live HERE, before the write (MEAL-11's stance): a supplement with no name is
 * unfindable in its own list, and one with no dose is a reminder to take an unspecified
 * amount of something. A refused save leaves the form exactly as typed — nothing is silently
 * corrected and nothing is written.
 *
 * Takes the whole [Supplement] rather than its fields (SUPP-08/SUPP-12): the row now carries a
 * schedule and a reminder ladder, and a four-argument signature that grew to eight would be a
 * positional trap where `timing` and `schedule` are one transposition apart.
 */
class SaveSupplementUseCase(
    private val repository: SupplementRepository,
    /** SUPP-12: a changed schedule or time must take effect now, not at the next app open. */
    private val armReminders: ArmMealRemindersUseCase? = null,
) {
    suspend operator fun invoke(supplement: Supplement): AppResult<Unit> {
        if (supplement.name.isBlank() || supplement.dose.isBlank()) return AppResult.Success(Unit)
        val result = repository.save(
            supplement.copy(
                name = supplement.name.trim(),
                dose = supplement.dose.trim(),
                // Whether it has been taken TODAY is a fact about the day, held in its own
                // table (SUPP-07). Editing the catalog row cannot assert anything about it,
                // so this carries the only honest value: the write path ignores it entirely.
                taken = false,
                // SUPP-12: a time with no rungs, or rungs with no time, is half a reminder —
                // and half a reminder is one that never fires while the row still says
                // "reminds 08:00". Normalised at the ONE write, so no surface has to remember.
                leads = if (supplement.remindAt == null) emptySet() else supplement.leads,
                remindAt = if (supplement.leads.isEmpty()) null else supplement.remindAt,
            ),
        )
        if (result is AppResult.Success) armReminders?.invoke()
        return result
    }
}

/**
 * SET-05: drop it from the stack. Past doses stand — see [SupplementRepository.delete].
 *
 * SUPP-12: the re-arm is what actually SILENCES a deleted supplement's alarms. Without it the
 * OS keeps ringing for a dose that no longer exists, and by then there is nothing left in the
 * database to derive a cancellation from.
 */
class DeleteSupplementUseCase(
    private val repository: SupplementRepository,
    private val armReminders: ArmMealRemindersUseCase? = null,
) {
    suspend operator fun invoke(id: String): AppResult<Unit> {
        val result = repository.delete(id)
        if (result is AppResult.Success) armReminders?.invoke()
        return result
    }
}

/**
 * WORK-07: set one day of the training week.
 *
 * Clearing the label IS how a day becomes a rest day, so a blank name is a valid save here —
 * unlike a supplement, where a nameless row would be unfindable in its own list.
 */
class SaveWorkoutDayUseCase(
    private val repository: WorkoutRepository,
    private val armReminders: ArmMealRemindersUseCase? = null,
) {
    suspend operator fun invoke(day: DayOfWeek, plan: WorkoutDayPlan): AppResult<Unit> {
        val label = plan.label?.trim()?.ifBlank { null }
        // A rest day has nothing to be reminded of, so it keeps neither a time nor a rung.
        // Dropping these HERE rather than only at the Room mapper matters: the mapper's guard
        // covers what is stored, and this covers what every in-memory consumer sees the moment
        // the save returns — otherwise a day that just stopped training still reads as one
        // with a live alarm on it.
        val silent = label == null || plan.remindAt == null || plan.leads.isEmpty()
        val result = repository.saveDay(
            day,
            plan.copy(
                label = label,
                remindAt = if (silent) null else plan.remindAt,
                leads = if (silent) emptySet() else plan.leads,
            ),
        )
        if (result is AppResult.Success) armReminders?.invoke()
        return result
    }
}
