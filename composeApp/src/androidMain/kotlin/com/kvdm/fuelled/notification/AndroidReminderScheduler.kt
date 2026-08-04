package com.kvdm.fuelled.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.kvdm.fuelled.domain.model.MealReminder
import com.kvdm.fuelled.domain.model.ReminderCapability
import com.kvdm.fuelled.domain.model.ReminderChannel
import com.kvdm.fuelled.domain.model.ReminderLead
import com.kvdm.fuelled.domain.model.ReminderMode
import com.kvdm.fuelled.domain.model.ReminderTarget
import com.kvdm.fuelled.domain.notification.ReminderScheduler
import java.util.Calendar

/**
 * The Android half of PLAN-07 — meal, water, supplement and workout reminders on AlarmManager.
 *
 * **The clause's hard rule is "never silently nothing."** Android has made exact alarms a
 * scarce resource: `SCHEDULE_EXACT_ALARM` is not granted to an app like this one by default,
 * and the usual result is that a naive `setExactAndAllowWhileIdle` throws `SecurityException`
 * and the developer discovers it from a crash report. So the mode is decided from the OS's
 * *current* answer, and the inexact path is a first-class outcome rather than a fallback nobody
 * tested: a meal reminder that arrives within Doze's ~15-minute window is completely fine, and
 * infinitely better than one that never arrives.
 */
class AndroidReminderScheduler(
    private val context: Context,
) : ReminderScheduler {

    override suspend fun capability(): ReminderCapability = ReminderCapability(
        notificationsAllowed = notificationsAllowed(),
        exactAlarmsAllowed = exactAlarmsAllowed(),
    )

    override suspend fun arm(reminders: List<MealReminder>) {
        ensureChannels()
        // Always clear first. A reminder whose time moved keeps its key, so an upsert would be
        // enough — but a reminder that was CANCELLED (its slot got ticked done, its supplement
        // was deleted) is absent from the new list entirely, and only a clear-then-set actually
        // silences it.
        cancelAll()
        if (reminders.isEmpty()) return

        val manager = alarmManager ?: return
        val exact = exactAlarmsAllowed()
        val armed = mutableSetOf<String>()
        for (reminder in reminders) {
            // UNAVAILABLE means the OS will deliver nothing. Arming anyway would burn alarms to
            // fire notifications that get dropped; the honest surface is the times sheet saying
            // reminders are off, which the domain's list already carries the mode for.
            if (reminder.mode == ReminderMode.UNAVAILABLE) continue
            val triggerAt = nextOccurrenceMillis(reminder) ?: continue
            val pending = pendingIntentFor(reminder, triggerAt)
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                manager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, reminder.windowMillis, pending)
            }
            armed += reminder.key
        }
        rememberArmed(armed)
    }

    override suspend fun cancelAll() {
        val manager = alarmManager ?: return
        for (key in allKeys()) {
            // FLAG_NO_CREATE: if no such alarm exists there is nothing to cancel, and creating
            // one purely to cancel it is a pointless allocation. Null simply means "already
            // gone", which is the normal case for most keys on most calls.
            pendingIntentForKey(key, create = false)?.let(manager::cancel)
        }
        rememberArmed(emptySet())
    }

    override suspend fun requestPermission(): Boolean? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The dialog needs an Activity; the bridge holds whichever one is alive. Null when
            // none is — an ask that could not be shown is not an ask (NOTIF-01).
            NotificationPermissionBridge.request()
        } else {
            // No runtime dialog exists before API 33 — notifications are on unless the user
            // switched the app off in settings, and THAT road back is NOTIF-03's tap-through.
            null
        }

    override fun openNotificationSettings() {
        // NOTIF-03: the app-level notification settings screen. NEW_TASK because this is
        // launched from an application context, not an Activity.
        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
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
     * The wall-clock instant this reminder should fire, or null when that moment has already
     * gone.
     *
     * Two shapes, because the app has two kinds of reminder:
     *
     * - **Daily** (`onDate == null`) — meals, water, the nudge. A time of day, armed at its next
     *   occurrence: today if it has not passed, otherwise tomorrow.
     * - **Dated** (`onDate` set) — a Monday-and-Thursday dose, a Saturday session. "Next
     *   occurrence of 08:00" would ring these EVERY morning, so the domain hands over the exact
     *   date and this arms that instant and nothing else. A dated moment already in the past is
     *   dropped rather than rolled forward: rolling it would invent a day the schedule never
     *   claimed, and the policy that owns due dates has already picked the right one.
     *
     * Deliberately wall-clock (`Calendar`) rather than "now + delta": these are times of day, so
     * across a DST transition the meal still belongs at 07:00 local, not at whatever 07:00 minus
     * 24 hours became.
     */
    private fun nextOccurrenceMillis(reminder: MealReminder): Long? {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            reminder.onDate?.let { date ->
                set(Calendar.YEAR, date.year)
                // Calendar months are 0-based; kotlinx-datetime's are 1-based.
                set(Calendar.MONTH, date.monthNumber - 1)
                set(Calendar.DAY_OF_MONTH, date.day)
            }
            set(Calendar.HOUR_OF_DAY, reminder.time.hour)
            set(Calendar.MINUTE, reminder.time.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (next.timeInMillis > now.timeInMillis) return next.timeInMillis
        // Past. A daily reminder rolls to tomorrow; a dated one is simply over.
        if (reminder.onDate != null) return null
        next.add(Calendar.DAY_OF_YEAR, 1)
        return next.timeInMillis
    }

    /**
     * The inexact window this reminder tolerates.
     *
     * An hour is right for a meal — the OS gets room to batch the wakeup and "around lunch" is
     * still lunch. It is WRONG for a lead-time reminder: a "30 minutes before" alarm with an
     * hour of slack can land half an hour AFTER the thing it was warning about, which is worse
     * than not firing. Lead-time rungs therefore get a quarter of the slack.
     */
    private val MealReminder.windowMillis: Long
        get() = when (target) {
            is ReminderTarget.Supplement ->
                if (target.lead == ReminderLead.AT_TIME) WINDOW_MILLIS else SHORT_WINDOW_MILLIS
            is ReminderTarget.Workout ->
                if (target.lead == ReminderLead.AT_TIME) WINDOW_MILLIS else SHORT_WINDOW_MILLIS
            else -> WINDOW_MILLIS
        }

    /**
     * The alarm intent for one reminder.
     *
     * **The `data` Uri is load-bearing, not decoration.** `PendingIntent` identity is
     * `requestCode` plus `Intent.filterEquals`, and `filterEquals` explicitly ignores EXTRAS.
     * These intents differed only in extras, so their whole identity rested on
     * `key.hashCode()` as the request code — fine while the keys were thirteen compile-time
     * constants, and unsound the moment supplement ids became user-supplied strings: two keys
     * that hash alike would share one alarm slot, and `FLAG_UPDATE_CURRENT` would silently
     * overwrite the first with the second. A per-key `data` Uri makes every intent genuinely
     * distinct, so a collision costs nothing.
     */
    private fun pendingIntentFor(reminder: MealReminder, triggerAt: Long): PendingIntent {
        val intent = alarmIntent(reminder.key).apply {
            putExtra(EXTRA_KEY, reminder.key)
            putExtra(EXTRA_TITLE, titleFor(reminder))
            putExtra(EXTRA_BODY, bodyFor(reminder))
            putExtra(EXTRA_CHANNEL, reminder.channel.id)
            // NOTIF-08: the logical day this reminder is ABOUT, so delivery can re-ask whether
            // it is still wanted — the dose may have been swallowed since it was armed.
            reminder.onDate?.let { putExtra(EXTRA_DUE_DATE, it.toString()) }
            // PLAN-26: the moment this alarm is FOR, carried to the moment it actually fires.
            // The two are not the same — Doze holds inexact alarms, and a device that was off
            // is handed everything it missed at once — so the receiver needs the intended
            // instant to tell a reminder from an echo of one.
            putExtra(EXTRA_AT, triggerAt)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** The cancel-side twin: same request code, action and data, so it matches the armed intent. */
    private fun pendingIntentForKey(key: String, create: Boolean): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            alarmIntent(key),
            PendingIntent.FLAG_IMMUTABLE or
                if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE,
        )

    private fun alarmIntent(key: String) = Intent(context, MealReminderReceiver::class.java).apply {
        action = ACTION_REMIND
        data = Uri.parse("$REMINDER_SCHEME://reminder/$key")
    }

    // PLAN-07: a meal reminder fires at the prep lead, so its copy names the meal's actual
    // moment and asks for the prep — "Lunch at 12:00 — time to prep", not a bare "Lunch"
    // arriving half an hour early with no explanation. Supplement and workout copy follows the
    // same rule: the rung is WHY it arrived now, so the rung is what the line says.
    private fun titleFor(reminder: MealReminder): String = when (val target = reminder.target) {
        is ReminderTarget.Meal -> "${mealTitle(target)} at ${reminder.eventTime.clock()} — time to prep"
        is ReminderTarget.Water -> "Water — 500 ml"
        is ReminderTarget.PlanTomorrow -> "Nothing planned for tomorrow"
        is ReminderTarget.Supplement -> when (target.lead) {
            ReminderLead.NIGHT_BEFORE -> "Tomorrow is a dose day"
            ReminderLead.THIRTY_MIN -> "Dose in 30 minutes"
            ReminderLead.AT_TIME -> "Dose due — ${reminder.eventTime.clock()}"
        }
        is ReminderTarget.Workout -> when (target.lead) {
            ReminderLead.NIGHT_BEFORE -> "Training tomorrow"
            ReminderLead.THIRTY_MIN -> "Training in 30 minutes"
            ReminderLead.AT_TIME -> "Training now — ${reminder.eventTime.clock()}"
        }
    }

    private fun bodyFor(reminder: MealReminder): String = when (reminder.target) {
        is ReminderTarget.Meal, is ReminderTarget.Water -> "Time to fuel."
        is ReminderTarget.PlanTomorrow -> "Take a minute tonight to set up tomorrow's six meals."
        // The name is deliberately NOT in the copy. A notification is readable on a lock screen
        // by anyone holding the phone, and what someone injects is nobody else's business —
        // the app knows which dose it means, and the user does too.
        is ReminderTarget.Supplement -> "Open Fuelled to see which."
        is ReminderTarget.Workout -> "Open Fuelled for today's session."
    }

    private fun mealTitle(target: ReminderTarget.Meal): String = target.slot.name
        .split('_')
        .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }

    private fun kotlinx.datetime.LocalTime.clock(): String =
        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    /**
     * NOTIF-09: one OS channel per [ReminderChannel]. Enumerated from the domain enum, so a
     * family added there cannot ship without the channel it posts to.
     */
    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = notificationManager ?: return
        for (channel in ReminderChannel.entries) {
            if (manager.getNotificationChannel(channel.id) != null) continue
            manager.createNotificationChannel(
                NotificationChannel(channel.id, channel.displayName, NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = channel.description },
            )
        }
    }

    /**
     * Record which keys are currently armed.
     *
     * The fixed thirteen can be enumerated from the enums that define them, and are. Supplement
     * and workout keys cannot: they carry user-supplied ids, so the set of alarms the OS is
     * holding is not derivable from anything still in the database once a supplement is
     * DELETED — and its alarm would then ring forever, for a dose that no longer exists.
     *
     * This is not the "stored list of what we armed" the enumeration comment rejects. That
     * would be a second, stale answer to *what SHOULD be armed*, which is still derived fresh
     * from the stack on every call. This is a record of what the OS was actually handed, kept
     * only so it can be handed back — rewritten inside the same act that arms, and floored by
     * the enumerable keys so a wiped preferences file still cancels everything fixed.
     */
    private fun rememberArmed(keys: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(PREF_ARMED_KEYS, keys)
            .apply()
    }

    private fun allKeys(): Set<String> = fixedKeys() + context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getStringSet(PREF_ARMED_KEYS, emptySet())
        .orEmpty()

    private val alarmManager: AlarmManager?
        get() = ContextCompat.getSystemService(context, AlarmManager::class.java)

    private val notificationManager: NotificationManager?
        get() = ContextCompat.getSystemService(context, NotificationManager::class.java)

    internal companion object {
        /** The default channel for a reminder whose intent lost its channel extra. */
        val CHANNEL_ID: String get() = ReminderChannel.MEALS.id
        const val ACTION_REMIND = "com.kvdm.fuelled.action.MEAL_REMINDER"
        const val EXTRA_KEY = "reminder_key"
        const val EXTRA_TITLE = "reminder_title"
        const val EXTRA_BODY = "reminder_body"
        const val EXTRA_CHANNEL = "reminder_channel"

        /** The nudge's stable key (NOTIF-04) — mirrors [ReminderTarget.PlanTomorrow]'s. */
        const val PLAN_TOMORROW_KEY = "plan_tomorrow"

        /** The wall-clock instant this alarm was armed FOR, in epoch millis (PLAN-26). */
        const val EXTRA_AT = "reminder_at"

        /** The logical day this alarm is ABOUT, ISO-8601 (NOTIF-08). Absent on daily reminders. */
        const val EXTRA_DUE_DATE = "reminder_due_date"

        private const val REMINDER_SCHEME = "fuelled"
        private const val PREFS = "fuelled_reminders"
        private const val PREF_ARMED_KEYS = "armed_keys"

        /**
         * One request code for every alarm — identity comes from the intent's per-key `data`
         * Uri instead. Hashing the key into a request code is what made two unrelated
         * reminders able to share one alarm slot.
         */
        private const val REQUEST_CODE = 1

        /** The inexact window — batched by the OS, still recognisably "around lunch". */
        private const val WINDOW_MILLIS = 60L * 60L * 1000L

        /** The window a lead-time rung tolerates before it stops meaning "before". */
        private const val SHORT_WINDOW_MILLIS = 15L * 60L * 1000L

        /**
         * The keys that can be derived rather than remembered — six meals, six waters and the
         * nudge. Kept enumerable so a cancel-all after a preferences wipe still finds them all.
         */
        fun fixedKeys(): Set<String> =
            (com.kvdm.fuelled.domain.model.MealSlot.entries.map { "meal_${it.name}" } +
                (1..6).map { "water_$it" } +
                PLAN_TOMORROW_KEY).toSet()
    }
}
