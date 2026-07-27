package com.kvdm.fuelled.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.kvdm.fuelled.domain.model.MealReminder
import com.kvdm.fuelled.domain.model.ReminderCapability
import com.kvdm.fuelled.domain.model.ReminderMode
import com.kvdm.fuelled.domain.model.ReminderTarget
import com.kvdm.fuelled.domain.notification.ReminderScheduler
import java.util.Calendar

/**
 * The Android half of PLAN-07 — daily meal and water reminders on AlarmManager.
 *
 * **The clause's hard rule is "never silently nothing."** Android has made exact alarms a
 * scarce resource: `SCHEDULE_EXACT_ALARM` is not granted to an app like this one by default,
 * and the usual result is that a naive `setExactAndAllowWhileIdle` throws `SecurityException`
 * and the developer discovers it from a crash report. So the mode is decided from the OS's
 * *current* answer, and the inexact path is a first-class outcome rather than a fallback nobody
 * tested: a meal reminder that arrives within Doze's ~15-minute window is completely fine, and
 * infinitely better than one that never arrives.
 *
 * The keys are stable per target, so [arm] genuinely replaces rather than accumulates — which
 * is what lets the boot receiver and every app open call it freely.
 */
class AndroidReminderScheduler(
    private val context: Context,
) : ReminderScheduler {

    override suspend fun capability(): ReminderCapability = ReminderCapability(
        notificationsAllowed = notificationsAllowed(),
        exactAlarmsAllowed = exactAlarmsAllowed(),
    )

    override suspend fun arm(reminders: List<MealReminder>) {
        ensureChannel()
        // Always clear first. A reminder whose time moved keeps its key, so an upsert would be
        // enough — but a reminder that was CANCELLED (its slot got ticked done) is absent from
        // the new list entirely, and only a clear-then-set actually silences it.
        cancelAll()
        if (reminders.isEmpty()) return

        val manager = alarmManager ?: return
        val exact = exactAlarmsAllowed()
        for (reminder in reminders) {
            // UNAVAILABLE means the OS will deliver nothing. Arming anyway would burn alarms to
            // fire notifications that get dropped; the honest surface is the times sheet saying
            // reminders are off, which the domain's list already carries the mode for.
            if (reminder.mode == ReminderMode.UNAVAILABLE) continue
            val triggerAt = nextOccurrenceMillis(reminder)
            val pending = pendingIntentFor(reminder)
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                // A one-hour window: long enough that the OS can batch it with other wakeups
                // (which is the point of inexact scheduling) and short enough that a lunch
                // reminder still lands near lunch.
                manager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, WINDOW_MILLIS, pending)
            }
        }
    }

    override suspend fun cancelAll() {
        val manager = alarmManager ?: return
        for (key in allKeys()) {
            manager.cancel(pendingIntentForKey(key, mutable = false))
        }
    }

    private fun notificationsAllowed(): Boolean =
        // POST_NOTIFICATIONS only exists from API 33; before that, notifications are allowed
        // unless the user disabled them for the whole app, which areNotificationsEnabled covers.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            notificationManager?.areNotificationsEnabled() ?: false
        }

    private fun exactAlarmsAllowed(): Boolean =
        // Read fresh every time: the user can revoke "Alarms & reminders" in Settings while the
        // app is running, and a cached answer would keep us calling the exact API until crash.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }

    /**
     * The next wall-clock occurrence of a daily reminder. Deliberately wall-clock (`Calendar`)
     * rather than "now + delta": these are times of day, so across a DST transition the meal
     * still belongs at 07:00 local, not at whatever 07:00 minus 24 hours became.
     */
    private fun nextOccurrenceMillis(reminder: MealReminder): Long {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, reminder.time.hour)
            set(Calendar.MINUTE, reminder.time.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (next.timeInMillis <= now.timeInMillis) next.add(Calendar.DAY_OF_YEAR, 1)
        return next.timeInMillis
    }

    private fun pendingIntentFor(reminder: MealReminder): PendingIntent {
        val intent = Intent(context, MealReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_KEY, reminder.key)
            putExtra(EXTRA_TITLE, titleFor(reminder))
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** The cancel-side twin: same requestCode and action, so it matches the armed intent. */
    private fun pendingIntentForKey(key: String, mutable: Boolean): PendingIntent {
        val intent = Intent(context, MealReminderReceiver::class.java).apply { action = ACTION_REMIND }
        return PendingIntent.getBroadcast(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun titleFor(reminder: MealReminder): String = when (val target = reminder.target) {
        is ReminderTarget.Meal -> mealTitle(target)
        is ReminderTarget.Water -> "Water — 500 ml"
    }

    private fun mealTitle(target: ReminderTarget.Meal): String = target.slot.name
        .split('_')
        .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = notificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Meal reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Your six meals and the water between them."
            },
        )
    }

    private val alarmManager: AlarmManager?
        get() = ContextCompat.getSystemService(context, AlarmManager::class.java)

    private val notificationManager: NotificationManager?
        get() = ContextCompat.getSystemService(context, NotificationManager::class.java)

    internal companion object {
        const val CHANNEL_ID = "meal_reminders"
        const val ACTION_REMIND = "com.kvdm.fuelled.action.MEAL_REMINDER"
        const val EXTRA_KEY = "reminder_key"
        const val EXTRA_TITLE = "reminder_title"

        /** The inexact window — batched by the OS, still recognisably "around lunch". */
        private const val WINDOW_MILLIS = 60L * 60L * 1000L

        /**
         * Every key the app ever arms — six meals and six waters. Enumerated rather than
         * remembered, so a cancel-all after a process death still finds them all; a stored list
         * of "what we armed" would be exactly the kind of second truth that goes stale.
         */
        fun allKeys(): List<String> =
            com.kvdm.fuelled.domain.model.MealSlot.entries.map { "meal_${it.name}" } +
                (1..6).map { "water_$it" }
    }
}
