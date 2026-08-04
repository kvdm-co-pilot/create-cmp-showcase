package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.repository.SupplementRepository
import com.kvdm.fuelled.domain.result.AppResult

// The tap-to-take business action: persist one supplement's taken state. A mutation use case
// mirrors the read use cases' shape — the typed AppResult passes through untouched, never
// unwrapped into an exception (ARCH-06).
//
// SUPP-12: a dose that has been swallowed re-arms, so today's remaining rungs of its ladder
// are dropped and the next occurrence's are put in their place. The same trigger discipline
// PLAN-07 uses for a ticked meal slot — the write is what makes the schedule true, so the write
// is what re-arms. Delivery asks again anyway (NOTIF-08), because an alarm already sitting in
// the OS cannot be reasoned about from here; this is the cheap half of a belt-and-braces pair,
// and the reason the expensive half exists is that this one cannot cover a killed process.
class SetSupplementTakenUseCase(
    private val repository: SupplementRepository,
    private val armReminders: ArmMealRemindersUseCase? = null,
) {
    suspend operator fun invoke(id: String, taken: Boolean): AppResult<Unit> {
        val result = repository.setTaken(id, taken)
        // Only on success: a failed write changed nothing, so re-arming would spend an alarm
        // replace on state that did not move.
        if (result is AppResult.Success) armReminders?.invoke()
        return result
    }
}
