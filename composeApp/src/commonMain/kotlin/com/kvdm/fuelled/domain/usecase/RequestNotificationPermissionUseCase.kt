package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.notification.ReminderScheduler
import com.kvdm.fuelled.domain.repository.AppStateRepository
import com.kvdm.fuelled.domain.result.AppResult

/**
 * The once-ever permission ask (NOTIF-01) — the moment brief D2 names: the first arrival at
 * Today on an onboarded install, when reminders first matter.
 *
 * The WHOLE policy is here, so it is testable without a device: ask only when notifications
 * are currently denied, only after onboarding, and only if the dialog has never been shown —
 * then record the showing whatever the answer was, because a denial must never earn a second
 * dialog (the sanctioned second chance is NOTIF-03's settings tap-through). A grant re-arms
 * in the same act (NOTIF-02), so the day's schedule goes live at the moment of the yes.
 *
 * Safe to call on every Today open — that is the design: all the "at most once" lives in the
 * guards, not in the caller's memory.
 */
class RequestNotificationPermissionUseCase(
    private val appState: AppStateRepository,
    private val scheduler: ReminderScheduler,
    private val armReminders: ArmMealRemindersUseCase,
) {
    suspend operator fun invoke() {
        // A state read failure skips the ask entirely — a dialog we cannot record having
        // shown is a dialog that could show twice, which is the one thing NOTIF-01 forbids.
        val state = (appState.current() as? AppResult.Success)?.value ?: return
        if (!state.onboarded) return
        if (state.notifPromptShown) return
        if (scheduler.capability().notificationsAllowed) return

        // Null means no dialog was actually shown (no host for it, or the platform has none)
        // — an ask that never happened is not recorded, so the one real ask stays available.
        val granted = scheduler.requestPermission() ?: return
        appState.markNotifPromptShown()
        // NOTIF-02: the grant takes effect now — the armed set was built when notifications
        // were denied, so every reminder in it is UNAVAILABLE until this re-arm.
        if (granted) armReminders()
    }
}
