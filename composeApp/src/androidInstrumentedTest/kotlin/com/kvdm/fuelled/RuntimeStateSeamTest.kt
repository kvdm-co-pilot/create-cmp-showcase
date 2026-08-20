package com.kvdm.fuelled

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import androidx.savedstate.SavedStateRegistryOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kvdm.fuelled.testing.AlarmAsserts
import com.kvdm.fuelled.testing.DozeControl
import com.kvdm.fuelled.testing.NotificationAsserts
import com.kvdm.fuelled.testing.PermissionControl
import com.kvdm.fuelled.testing.ProcessControl
import com.kvdm.fuelled.testing.Shell
import com.kvdm.fuelled.testing.TimeWarp
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The exemplars for RUNTIME STATE CONTROL — the seam's second organ family.
 *
 * One principle: a claim is provable when the test can put the system into the state the
 * claim is about. Most unprovable mobile claims are unprovable for exactly that reason —
 * the state is hard to reach. You'd have to wait hours for Doze, ship to a user who
 * denies the permission, or hope the OS reclaims your process while you watch. The
 * organs in `testing/` reach those states on demand and restore them in `finally`:
 * [TimeWarp] (the clock), [DozeControl] (device idle), [PermissionControl] (grants),
 * [ProcessControl] (activity reclaim), NetworkControl (offline), ConfigControl (dark
 * mode / font scale / locale). Emulator-only by guard, root-free by construction, and
 * composable — the flagship below nests two of them.
 *
 * These tests assert universal facts through the app's real process, like
 * [PlatformBehaviorSeamTest] does: the stamped app has no alarm or notification feature
 * yet, so the arrange steps talk to the OS directly. When your feature exists, its
 * behavior test replaces the arrange step with YOUR scheduling/posting/restoring path
 * and keeps the shape — state in, act, observe, state restored.
 *
 * Runs via `:composeApp:connectedDebugAndroidTest` — the verify lane's `androidChecks`
 * step (SKIPs when no device is attached).
 */
@RunWith(AndroidJUnit4::class)
class RuntimeStateSeamTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val notificationManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @After
    fun tearDown() {
        // Leave the device as found — real OS state was asserted, so real OS state is
        // cleaned up (the organs restore their own brackets; this catches the probes).
        notificationManager.cancelAll()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannel(PROBE_CHANNEL)
        }
        alarmManager.cancel(probePendingIntent())
    }

    @Test
    fun anExactAllowWhileIdleAlarmDeliversFromInsideForcedDeepIdle() {
        // THE FLAGSHIP. `setExactAndAllowWhileIdle` exists entirely to survive Doze, and
        // that half of its name had never been verified: registration was proven
        // (AlarmAsserts), delivery-on-an-awake-device was proven (TimeWarp), but
        // delivery FROM INSIDE the idle state — the production claim, the #1 escaped-bug
        // class in real apps on this template — required a device nobody was willing to
        // leave motionless on battery for an hour. Composed organs make it a
        // sixty-second test: force deep idle, warp the clock past the trigger, and the
        // OS either honors the API's promise or the test says it didn't. In your
        // feature's test, the arrange step becomes YOUR scheduling ladder; keep the
        // shape: schedule → assert registered → force idle → warp → await delivery.
        TimeWarp.assumeOnEmulator()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertTrue(
                "canScheduleExactAlarms() is false — the debug-manifest exact-alarm " +
                    "grant (androidDebug/AndroidManifest.xml) is missing; " +
                    "setExactAndAllowWhileIdle would throw SecurityException",
                alarmManager.canScheduleExactAlarms(),
            )
        }

        val delivered = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                delivered.countDown()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(ACTION_DOZE_PROBE),
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, IntentFilter(ACTION_DOZE_PROBE))
        }

        try {
            val target = System.currentTimeMillis() + 2 * 60_000L
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                target,
                probePendingIntent(),
            )
            AlarmAsserts.assertAlarmRegistered("the doze-probe exact alarm") {
                it.whenMs == target
            }

            // Doze outside, clock inside: the device is idle FIRST, then its trigger
            // time arrives — the same order the world runs in.
            DozeControl.withDeviceIdle(DozeControl.IdleMode.DEEP) {
                TimeWarp.withWarpedClock(target + 5_000L) {
                    assertTrue(
                        "the exact allow-while-idle alarm did not deliver within 60s of " +
                            "its trigger time arriving inside forced deep idle — the one " +
                            "job the API's name promises",
                        delivered.await(60, TimeUnit.SECONDS),
                    )
                }
            }

            // Delivery consumes the OS slot (bounded poll; the table can trail the
            // broadcast slightly).
            val goneDeadline = SystemClock.elapsedRealtime() + 10_000L
            while (SystemClock.elapsedRealtime() < goneDeadline &&
                AlarmAsserts.registeredAlarms().any { it.whenMs == target }
            ) {
                Thread.sleep(200)
            }
            assertTrue(
                "the doze-probe alarm fired but is still in the OS alarm table — a " +
                    "fired one-shot must be consumed, not re-armed",
                AlarmAsserts.registeredAlarms().none { it.whenMs == target },
            )
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    @Test
    fun aNotificationPostedWithoutTheGrantNeverReachesTheShade() {
        // The permission-denied exemplar: the fresh-install default on API 33+ is
        // DENIED, and in that state the OS drops every post silently — code runs, phone
        // stays dark, no error anywhere. This proves that drop mechanically: post under
        // denial, then hold assertNoNotification's full window. The bracket SKIPs when
        // the grant is already held (the seam's other suite takes it, and un-granting
        // in-process would kill this test — PermissionControl's header carries the
        // whole trap). In your feature's test, the act step becomes YOUR notification
        // path, and the assertion becomes "it degrades the way the spec says" —
        // re-prompt, in-app banner, queued — instead of "nothing happened".
        // The permission only exists — and only gates posting — from API 33; below that
        // a post lands regardless, so there is no denied state to prove.
        assumeTrue(
            "POST_NOTIFICATIONS gates posting only from API 33 " +
                "(device is ${Build.VERSION.SDK_INT})",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        )
        Shell.assumeOnEmulator(
            "this exemplar reads and posts against the real shade in a denied state — " +
                "stock QA AVDs only",
        )
        PermissionControl.withPermissionDenied("android.permission.POST_NOTIFICATIONS") {
            assertTrue(
                "areNotificationsEnabled() is true while POST_NOTIFICATIONS is denied — " +
                    "the two surfaces should agree on API 33+",
                !notificationManager.areNotificationsEnabled(),
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    PROBE_CHANNEL,
                    "Runtime-state probe",
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
            notificationManager.notify(
                PROBE_ID,
                Notification.Builder(context, PROBE_CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("runtime-state probe")
                    .build(),
            )
            NotificationAsserts.assertNoNotification { it.id == PROBE_ID }
        }
    }

    @Test
    fun theOsRebuildsTheActivityThroughTheSavedStatePathWhenItReclaimsIt() {
        // The process-death exemplar — with the seam's honesty about what "process
        // death" can mean in-process. Instrumentation lives in the app's process, and
        // the OS pins that process at foreground importance, so `am kill` cannot
        // reclaim it (asserted LIVE below, so the claim never rots into a stale
        // comment). What the OS genuinely destroys and rebuilds is the ACTIVITY:
        // under don't-keep-activities, backgrounding destroys it for real —
        // ViewModelStore cleared, non-config instances dropped — and the return trip
        // rebuilds it from saved-instance state, the same path a true process death
        // takes at the activity layer. In your feature's test, the assertions become
        // YOUR restored state: the half-typed form, the scroll position, the selection.
        ProcessControl.withDontKeepActivities {
            val first = ProcessControl.relaunchApp()
            val firstIdentity = System.identityHashCode(first)

            // The saved-state probe: a nonce registered into the FIRST instance's
            // SavedStateRegistry (MainActivity is a ComponentActivity, and everything
            // rememberSaveable/SavedStateHandle keeps rides this same registry). If the
            // rebuild really runs through the saved-instance-state path, the SECOND
            // instance restores it — asserted below, so "through the saved-state path"
            // is observed, not inferred from a new instance existing.
            assertTrue(
                "the launcher activity is not a SavedStateRegistryOwner — the " +
                    "template's MainActivity is a ComponentActivity, which is one; " +
                    "the saved-state probe needs that registry",
                first is SavedStateRegistryOwner,
            )
            val savedProbe = "probe-${SystemClock.elapsedRealtimeNanos()}"
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                (first as SavedStateRegistryOwner).savedStateRegistry
                    .registerSavedStateProvider(SAVED_STATE_PROBE_KEY) {
                        Bundle().apply { putString("token", savedProbe) }
                    }
            }

            ProcessControl.backgroundApp()
            // Backgrounding and destruction are separate asynchronous steps; relaunching
            // between them would resume the SAME instance and prove nothing.
            ProcessControl.awaitDestroyed(first)

            // The live no-op proof: am kill against the pinned instrumented process
            // must leave this very pid running (observed from the OS side, not from
            // hope). If an OS change ever breaks the pinning, this line is where the
            // suite says so.
            ProcessControl.killReclaimableProcesses()
            val pids = Shell.exec("pidof ${context.packageName}").trim()
                .split(Regex("\\s+")).filter { it.isNotEmpty() }
            assertTrue(
                "am kill reclaimed the instrumented process (pid ${Process.myPid()} " +
                    "not in '$pids') — the foreground pinning assumption no longer " +
                    "holds on this image; ProcessControl's header needs revisiting",
                pids.contains(Process.myPid().toString()),
            )

            val second = ProcessControl.relaunchApp()
            try {
                assertNotEquals(
                    "the same Activity instance came back after backgrounding under " +
                        "don't-keep-activities — nothing was destroyed, so nothing " +
                        "about state restoration was proven",
                    firstIdentity,
                    System.identityHashCode(second),
                )
                var restored: Bundle? = null
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    restored = (second as SavedStateRegistryOwner).savedStateRegistry
                        .consumeRestoredStateForKey(SAVED_STATE_PROBE_KEY)
                }
                assertEquals(
                    "a NEW instance resumed but the probe did not come back through " +
                        "its SavedStateRegistry — destruction happened, restoration " +
                        "did not, so the saved-state path is NOT proven",
                    savedProbe,
                    restored?.getString("token"),
                )
            } finally {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    second.finish()
                }
            }
        }
    }

    private fun probePendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_DOZE_PROBE,
            Intent(ACTION_DOZE_PROBE).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val PROBE_CHANNEL = "runtime_state_probe"
        const val PROBE_ID = 434_242
        const val ACTION_DOZE_PROBE = "cmp.seam.DOZE_PROBE_ALARM"
        const val REQUEST_DOZE_PROBE = 434_301
        const val SAVED_STATE_PROBE_KEY = "cmp.seam.savedStateProbe"
    }
}
