package com.kvdm.fuelled.notification

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kvdm.fuelled.R
import com.kvdm.fuelled.domain.usecase.ArmMealRemindersUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Posts one reminder when its alarm fires, and re-arms the next day's (PLAN-07).
 *
 * The re-arm is why this receiver exists at all rather than a repeating alarm: repeating alarms
 * are inexact by definition on modern Android, and a daily one drifts. Arming the next
 * occurrence as each one fires keeps every reminder pinned to its wall-clock time — and it
 * re-reads the stored times, so a time changed at lunch is already in effect by dinner.
 */
class MealReminderReceiver : BroadcastReceiver(), KoinComponent {

    private val armReminders: ArmMealRemindersUseCase by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidReminderScheduler.ACTION_REMIND) return
        val title = intent.getStringExtra(AndroidReminderScheduler.EXTRA_TITLE) ?: return
        val key = intent.getStringExtra(AndroidReminderScheduler.EXTRA_KEY) ?: return

        postNotification(context, key, title)

        // goAsync keeps the process alive across the suspend boundary; without it the re-arm
        // races the receiver's return and silently loses tomorrow's alarms.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                armReminders()
            } finally {
                pending.finish()
            }
        }
    }

    private fun postNotification(context: Context, key: String, title: String) {
        // The permission can be revoked between arming and firing — check at post time, not
        // just at arm time, or this throws on Android 13+.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        manager.notify(
            key.hashCode(),
            NotificationCompat.Builder(context, AndroidReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText("Time to fuel.")
                .setAutoCancel(true)
                .build(),
        )
    }
}

/**
 * Re-arms every reminder after a reboot (PLAN-07).
 *
 * Android clears all alarms on reboot — without this the day's reminders quietly stop after any
 * restart, which is the single most common way a reminder feature is discovered to be broken
 * weeks later. `RECEIVE_BOOT_COMPLETED` plus this receiver is the whole fix.
 */
class BootReArmReceiver : BroadcastReceiver(), KoinComponent {

    private val armReminders: ArmMealRemindersUseCase by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                armReminders()
            } finally {
                pending.finish()
            }
        }
    }
}
