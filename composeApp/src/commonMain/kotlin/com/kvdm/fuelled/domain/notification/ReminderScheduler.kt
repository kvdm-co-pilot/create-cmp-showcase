package com.kvdm.fuelled.domain.notification

import com.kvdm.fuelled.domain.model.MealReminder
import com.kvdm.fuelled.domain.model.ReminderCapability

/**
 * The platform port for daily reminders (PLAN-07). Pure interface, no framework types
 * (ARCH-02) — Android binds an AlarmManager-backed implementation; desktop and iOS bind a
 * no-op, because iOS notifications are deliberately outside this contract (brief decision 9)
 * and the dev-client window has nothing to ring.
 *
 * Deliberately NOT in `domain.repository`: a repository answers questions about stored state
 * and every one of its verbs returns `AppResult` (ARCH-06). This is an outbound port to the OS
 * — "arm these alarms" has no typed domain failure to report, only a platform capability read
 * back through [capability]. Putting it under the repository package would have forced a
 * meaningless `AppResult<Unit>` on every call just to satisfy a rule aimed at a different kind
 * of boundary.
 *
 * The decision of *what* to arm is not here — it is [com.kvdm.fuelled.domain.model.remindersFor],
 * which is pure and tested. This port only carries it out.
 */
interface ReminderScheduler {

    /** What the OS will currently allow, read fresh — permissions can be revoked at any time. */
    suspend fun capability(): ReminderCapability

    /**
     * Replace the armed set with exactly [reminders] (PLAN-07). Replace, not add: re-arming is
     * how a changed meal time takes effect, and an additive API would leave the old alarm
     * ringing at the old time. Keys are stable per target, so this is idempotent — which is
     * what makes "re-arm on boot and on app open" safe to call as often as it likes.
     */
    suspend fun arm(reminders: List<MealReminder>)

    /** Drop every armed reminder — used when the user turns the feature off wholesale. */
    suspend fun cancelAll()

    /**
     * Show the OS notification-permission dialog (NOTIF-01). `true`/`false` is the user's
     * answer; `null` means NO dialog was shown (the platform has none, or no Activity could
     * host it) — and a dialog that was never shown must not count against the once-ever ask,
     * so callers record nothing on `null`. WHEN to ask is not decided here — that is
     * [com.kvdm.fuelled.domain.usecase.RequestNotificationPermissionUseCase]'s policy; this
     * only shows the dialog.
     */
    suspend fun requestPermission(): Boolean?

    /**
     * Open the system's notification settings for this app (NOTIF-03) — the sanctioned second
     * chance after a denial, because the app itself never re-prompts (NOTIF-01).
     */
    fun openNotificationSettings()
}

/**
 * The binding for platforms this contract does not cover: desktop (nothing to ring) and iOS
 * (brief decision 9 — deliberately unpromised).
 *
 * It reports no capability rather than pretending, so the times sheet on those platforms tells
 * the same truth it would on an Android device with notifications denied. A silent no-op that
 * claimed [ReminderCapability.notificationsAllowed] would make the UI lie.
 */
class NoOpReminderScheduler : ReminderScheduler {
    override suspend fun capability(): ReminderCapability =
        ReminderCapability(notificationsAllowed = false, exactAlarmsAllowed = false)

    override suspend fun arm(reminders: List<MealReminder>) = Unit

    override suspend fun cancelAll() = Unit

    /** No dialog exists here — nothing was shown, and nothing should be recorded as shown. */
    override suspend fun requestPermission(): Boolean? = null

    override fun openNotificationSettings() = Unit
}
