package com.kvdm.fuelled

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.PendingIntent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kvdm.fuelled.testing.AlarmAsserts
import com.kvdm.fuelled.testing.NotificationAsserts
import com.kvdm.fuelled.testing.TimeWarp
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The exemplar for the on-device behavior tier — and the proof that the seam works.
 *
 * This seam exists because platform behavior escapes every desktop tier; when your feature
 * touches alarms, notifications, or locks, its behavior test lives here. Desktop unit tests
 * run on a JVM, golden trees pin structure, the conformance suite is static, and the
 * Maestro smoke taps UI without asserting anything about the notification shade or the OS
 * alarm table — so a feature whose whole point is "the phone alerts" can ship fully green
 * and never alert. In two real apps built on this template, that exact class produced nine
 * escaped defects across multiple releases; the hand-built version of this source set
 * caught two more bugs the week it landed.
 *
 * What the template can honestly exemplify: the stamped app ships no notification or alarm
 * feature yet, so these tests assert universal facts through the app's REAL process on a
 * real device — the app boots (its Android DI graph resolves for real, which desktop fakes
 * cannot prove), and the tier's helpers observe true OS state (a posted notification is
 * seen in the shade, a scheduled alarm is seen in the alarm table, and the
 * PendingIntent-identity collision is demonstrated live). When your first feature posts
 * its own notification, replace the direct NotificationManager call below with your
 * feature's real path and keep the assertions — that is the whole pattern.
 *
 * Runs via `:composeApp:connectedDebugAndroidTest` — the verify lane's `androidChecks`
 * step (SKIPs when no device is attached; cite platform-behavior spec clauses from here).
 */
@RunWith(AndroidJUnit4::class)
class PlatformBehaviorSeamTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val notificationManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @Before
    fun setUp() {
        // A fresh install has no POST_NOTIFICATIONS grant on API 33+, and without it every
        // post is dropped before it reaches the shade — the notification test would then
        // fail about the harness, not the app. The grant is taken, never hoped for.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                    context.packageName,
                    "android.permission.POST_NOTIFICATIONS",
                )
            }
        }
    }

    @After
    fun tearDown() {
        // Leave the device as found: this tier asserts real OS state, so real OS state
        // must be cleaned up — a leaked notification or alarm pollutes the NEXT test run.
        notificationManager.cancelAll()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannel(PROBE_CHANNEL)
        }
        listOf(REQUEST_A, REQUEST_B, REQUEST_SHARED).forEach { code ->
            alarmManager.cancel(probePendingIntent(code, ACTION_PROBE))
        }
        alarmManager.cancel(probePendingIntent(REQUEST_WARP, ACTION_WARP))
    }

    @Test
    fun theRealAppBootsOnTheDevice() {
        // Not a tautology: this launches MainActivity in the app's real process with the
        // real AppApplication — Koin's ANDROID graph resolves for real (desktop DI
        // substitutes fakes, so an androidMain-only wiring bug is invisible to every other
        // tier; a real app on this template shipped exactly that crash). If the app cannot
        // boot, nothing else in this tier means anything, so this is the tier's smoke.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertNotNull(scenario.state)
        }
    }

    @Test
    fun aPostedNotificationIsVisibleToTheShadeAndItsChannelHoldsItsImportance() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        // The template has no notification feature yet, so the post goes through
        // NotificationManager directly — proving the helpers see true OS state. In your
        // feature's test, this arrange step becomes a call into YOUR notification path.
        notificationManager.createNotificationChannel(
            NotificationChannel(PROBE_CHANNEL, "Seam probe", NotificationManager.IMPORTANCE_HIGH),
        )
        notificationManager.notify(
            PROBE_ID,
            Notification.Builder(context, PROBE_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("seam probe")
                .build(),
        )

        // The assertions cross the process boundary: the OS accepted the channel at the
        // importance the app created it with, and the notification actually reached the
        // shade — the two facts that silently broke in production, repeatedly.
        NotificationAsserts.assertChannelExists(
            PROBE_CHANNEL,
            importanceFloor = NotificationManager.IMPORTANCE_HIGH,
        )
        NotificationAsserts.awaitNotification(id = PROBE_ID)
    }

    @Test
    fun twoLogicalAlarmsNeedTwoPendingIntentIdentities() {
        val base = System.currentTimeMillis() + HOUR_MS

        // Distinct request codes -> distinct PendingIntent identities -> two rows in the
        // OS alarm table. This is the correct shape for "N independent schedules".
        alarmManager.set(AlarmManager.RTC, base, probePendingIntent(REQUEST_A, ACTION_PROBE))
        alarmManager.set(AlarmManager.RTC, base + 60_000, probePendingIntent(REQUEST_B, ACTION_PROBE))
        AlarmAsserts.assertDistinctAlarms(
            expected = 2,
            what = "seam-probe alarms (distinct request codes)",
        ) { it.whenMs == base || it.whenMs == base + 60_000 }

        // The trap, demonstrated live: same request code + filter-equal intent (extras do
        // NOT count) is ONE identity, so the second set() silently REPLACES the first —
        // no error, no log, and only this table shows it. This exact shape shipped as two
        // "independent" alarms sharing a slot.
        val t3 = base + HOUR_MS
        alarmManager.set(AlarmManager.RTC, t3, probePendingIntent(REQUEST_SHARED, ACTION_PROBE))
        alarmManager.set(AlarmManager.RTC, t3 + 60_000, probePendingIntent(REQUEST_SHARED, ACTION_PROBE))
        AlarmAsserts.assertDistinctAlarms(
            expected = 1,
            what = "seam-probe alarms (shared identity — the later set replaces the earlier)",
        ) { it.whenMs == t3 || it.whenMs == t3 + 60_000 }
    }

    @Test
    fun anExactAlarmScheduledForTheFutureActuallyDeliversWhenItsTimeArrives() {
        // The proof shape for "my scheduled thing actually fires": registration says the
        // OS *holds* the alarm ([twoLogicalAlarmsNeedTwoPendingIntentIdentities] stops
        // there); this test warps the clock past the trigger time and watches DELIVERY —
        // the onReceive that no other tier can observe, in seconds instead of hours. In
        // your feature's test, the arrange step becomes YOUR scheduling path (the ladder
        // that computes "tomorrow at 08:00") and the receiver becomes YOUR receiver —
        // keep the shape: schedule → assert registered → warp past T → await delivery →
        // assert the OS slot is consumed. Emulator-only via TimeWarp's guard.
        TimeWarp.assumeOnEmulator()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Exact alarms are permission-gated from API 31. The debug manifest declares
            // SCHEDULE_EXACT_ALARM (≤32, granted by default there) and USE_EXACT_ALARM
            // (33+, granted at install) — see androidDebug/AndroidManifest.xml for why
            // they live in the debug manifest and not the shipped one.
            assertTrue(
                "canScheduleExactAlarms() is false — the debug-manifest exact-alarm " +
                    "grant (androidDebug/AndroidManifest.xml) is missing or was removed; " +
                    "setExactAndAllowWhileIdle would throw SecurityException",
                alarmManager.canScheduleExactAlarms(),
            )
        }

        val delivered = CountDownLatch(1)
        val deliveredAtElapsed = AtomicLong(0)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                deliveredAtElapsed.set(SystemClock.elapsedRealtime())
                delivered.countDown()
            }
        }
        // Dynamic registration: the receiver lives exactly as long as the test, no
        // manifest entry to leak. NOT_EXPORTED is correct because AlarmManager sends the
        // PendingIntent's broadcast with this app's own identity (API 33+ requires the
        // exported flag to be stated for context-registered receivers).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(ACTION_WARP),
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, IntentFilter(ACTION_WARP))
        }

        try {
            // Far enough ahead that nothing fires before the warp; close enough that a
            // registration-time sanity read stays cheap.
            val target = System.currentTimeMillis() + 2 * 60_000L
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                target,
                probePendingIntent(REQUEST_WARP, ACTION_WARP),
            )
            AlarmAsserts.assertAlarmRegistered("the warp-probe exact alarm") {
                it.whenMs == target
            }

            TimeWarp.withWarpedClock(target + 5_000L) {
                val warpedAtElapsed = SystemClock.elapsedRealtime()
                // Bounded on monotonic time (latch.await is nanoTime-based), so the
                // warped wall clock cannot distort the timeout.
                assertTrue(
                    "the exact alarm did not deliver within 60s of the clock warping " +
                        "past its trigger time — scheduled for $target, clock warped to " +
                        "${target + 5_000L}",
                    delivered.await(60, TimeUnit.SECONDS),
                )
                Log.i(
                    "PlatformBehaviorSeam",
                    "warp-probe alarm delivered ${deliveredAtElapsed.get() - warpedAtElapsed}ms " +
                        "after the clock warp",
                )
            }

            // Delivery consumes the OS slot: the registry entry must be gone. Bounded
            // poll on monotonic time — the table update can trail the broadcast slightly.
            val goneDeadline = SystemClock.elapsedRealtime() + 10_000L
            while (SystemClock.elapsedRealtime() < goneDeadline &&
                AlarmAsserts.registeredAlarms().any { it.whenMs == target }
            ) {
                Thread.sleep(200)
            }
            assertTrue(
                "the warp-probe alarm fired but its entry is still in the OS alarm table " +
                    "— a fired one-shot alarm must be consumed, not re-armed",
                AlarmAsserts.registeredAlarms().none { it.whenMs == target },
            )
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    private fun probePendingIntent(requestCode: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(action).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val PROBE_CHANNEL = "seam_probe"
        const val PROBE_ID = 424_242
        const val ACTION_PROBE = "cmp.seam.PROBE_ALARM"
        const val REQUEST_A = 424_301
        const val REQUEST_B = 424_302
        const val REQUEST_SHARED = 424_303
        const val ACTION_WARP = "cmp.seam.WARP_ALARM"
        const val REQUEST_WARP = 424_304
        const val HOUR_MS = 60L * 60 * 1000
    }
}
