package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.domain.model.MealReminder
import com.kvdm.fuelled.domain.model.ReminderCapability
import com.kvdm.fuelled.domain.notification.ReminderScheduler

/**
 * Hand-written fake for the reminder port (PLAN-07).
 *
 * [capability] is settable so a test can drive the three platform answers the clause cares
 * about — exact allowed, exact denied, notifications denied — without an emulator. [armed] is
 * the last set handed to [arm], which is what the clause's assertions are actually about: which
 * reminders exist, at what time, in which mode.
 */
class FakeReminderScheduler(
    var capability: ReminderCapability = ReminderCapability(
        notificationsAllowed = true,
        exactAlarmsAllowed = true,
    ),
) : ReminderScheduler {

    /** The most recent armed set — [arm] REPLACES, so this is the whole current schedule. */
    var armed: List<MealReminder> = emptyList()
        private set

    var armCount: Int = 0
        private set

    var cancelAllCount: Int = 0
        private set

    override suspend fun capability(): ReminderCapability = capability

    override suspend fun arm(reminders: List<MealReminder>) {
        armed = reminders
        armCount++
    }

    override suspend fun cancelAll() {
        armed = emptyList()
        cancelAllCount++
    }

    /** The dialog's scripted answer (NOTIF-01): null = no dialog could be shown. */
    var permissionAnswer: Boolean? = null

    var requestPermissionCount: Int = 0
        private set

    var openSettingsCount: Int = 0
        private set

    override suspend fun requestPermission(): Boolean? {
        requestPermissionCount++
        // A granted dialog changes what the OS will allow — mirror that, so a re-arm after
        // the grant sees the capability the real platform would report (NOTIF-02).
        if (permissionAnswer == true) capability = capability.copy(notificationsAllowed = true)
        return permissionAnswer
    }

    override fun openNotificationSettings() {
        openSettingsCount++
    }
}
