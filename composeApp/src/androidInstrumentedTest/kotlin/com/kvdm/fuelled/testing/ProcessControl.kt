package com.kvdm.fuelled.testing

import android.app.Activity
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue

/**
 * Process-death and activity-reclaim control for the on-device tier — the classic
 * Android state-loss class ("came back from the background and everything was gone"),
 * made a test input.
 *
 * THE TRAP this file must document rather than hide — you cannot kill the process your
 * test lives in: instrumentation runs INSIDE the app's process, and the OS pins an
 * instrumented process at foreground importance so the test infrastructure survives.
 * `am kill` — the command that models real low-memory reclaim, because it kills only
 * processes the OS itself considers reclaimable (background, no foreground activity) —
 * is therefore a NO-OP against the app under test while a test runs in it. Verified
 * live on a stock API 35 emulator: `am kill` against a foreground app left its pid
 * untouched; against the same app backgrounded, the pid died. (`am force-stop` is the
 * other command and the WRONG model twice over: it is the user's Settings "Force stop"
 * — kills every process unconditionally, cancels the app's alarms and jobs, and marks
 * the app stopped so nothing re-launches it — and in this seam it would take the test
 * down with the app.) So the honest primitive is not "kill my own process"; it is
 * making the OS destroy and rebuild what it actually destroys and rebuilds — the
 * ACTIVITY — through the same save/restore path a process death exercises.
 *
 * [withDontKeepActivities] brackets exactly that: `always_finish_activities 1` is the
 * developer setting Android ships for reproducing this bug class ("Don't keep
 * activities"), and under it a backgrounded activity is REALLY destroyed — not
 * config-change recreated: `isChangingConfigurations` is false, the ViewModelStore is
 * cleared, non-config instances are dropped, and the return trip rebuilds the activity
 * from its saved-instance-state Bundle. That is the state-restoration path a real
 * process death takes, minus two things the header must not let a green test overclaim:
 *  - the process itself survives (verified: same pid across the round trip), so statics,
 *    singletons, and Application state are NOT re-initialized — a bug hiding in "my
 *    repository cache is a static" needs a true cold start to show;
 *  - saved-instance state lives in the ActivityManager, not on disk — reboot loses it.
 * The true full-process rehearsal stays a two-command manual step, from OUTSIDE the
 * process: background the app, `adb shell am kill <pkg>`, relaunch — which is exactly
 * what this organ's in-process bracket cannot be, and says so.
 *
 * ONE MORE TRAP, verified live on a stock API 35 image: `settings put global
 * always_finish_activities 1` alone is a runtime no-op. ActivityTaskManagerService reads
 * that setting ONCE at boot; the Developer-options toggle works because it calls
 * `IActivityManager.setAlwaysFinish(..)`, which flips the live in-memory flag AND
 * persists the setting. Observed: with only the settings write in place, a freshly
 * launched activity backgrounded to STOPPED and sat there un-destroyed indefinitely.
 * [withDontKeepActivities] therefore makes the same binder call the toggle makes —
 * reflection under the shell's adopted permission identity (the shell uid holds
 * SET_ALWAYS_FINISH on stock images; verified granted on API 35) — and proves the call
 * landed by re-reading the setting the call itself writes.
 *
 * All commands verified root-free from the shell uid on a stock user-build image:
 * `input keyevent KEYCODE_HOME`, `am start -W -n <component>`, `am kill <pkg>`,
 * `settings get/put/delete global always_finish_activities` (restore bookkeeping only —
 * the live flag travels through the binder call above).
 */
object ProcessControl {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    /**
     * Run [block] with "Don't keep activities" on — the LIVE flag via
     * `IActivityManager.setAlwaysFinish` (see the header: the settings write alone is a
     * boot-time-only input), restoring both afterwards even when the block throws: the
     * live flag is switched back, then a prior persisted value is put back and an absent
     * one is deleted, not defaulted. SKIPs below API 29 (adoptShellPermissionIdentity is
     * the root-free way to hold SET_ALWAYS_FINISH). Inside the block, [backgroundApp]
     * destroys the foreground activity for real and [relaunchApp] brings it back through
     * the saved-instance-state path — see RuntimeStateSeamTest for the exemplar shape
     * (capture identity, plant a saved-state probe, background, await destruction,
     * relaunch, assert a NEW instance resumed with the probe restored).
     */
    fun <T> withDontKeepActivities(block: () -> T): T {
        Shell.assumeOnEmulator(
            "ProcessControl runs only on emulators (ro.kernel.qemu != 1 here). " +
                "Don't-keep-activities on a real phone destroys the owner's app state " +
                "in every backgrounded app — run this suite on a stock QA AVD instead.",
        )
        assumeTrue(
            "flipping the live always-finish flag needs adoptShellPermissionIdentity " +
                "(API 29+); device is API ${Build.VERSION.SDK_INT}",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
        )
        val prior = Shell.readSetting("global", "always_finish_activities")
        setAlwaysFinish(true)
        try {
            return block()
        } finally {
            setAlwaysFinish(prior == "1")
            Shell.restoreSetting("global", "always_finish_activities", prior)
        }
    }

    /**
     * The Developer-options toggle's own path: `IActivityManager.setAlwaysFinish`, called
     * with the shell's adopted permission identity (shell holds SET_ALWAYS_FINISH). This
     * is what actually updates ActivityTaskManagerService's in-memory flag at runtime —
     * the settings provider write alone is read once at boot and never again (header).
     * The call also persists the setting, which is the echo this method re-reads to prove
     * the binder call landed rather than trusting it.
     */
    private fun setAlwaysFinish(enabled: Boolean) {
        val uiAutomation = instrumentation.uiAutomation
        uiAutomation.adoptShellPermissionIdentity("android.permission.SET_ALWAYS_FINISH")
        try {
            val service = Class.forName("android.app.ActivityManager")
                .getMethod("getService")
                .invoke(null)
            Class.forName("android.app.IActivityManager")
                .getMethod("setAlwaysFinish", java.lang.Boolean.TYPE)
                .invoke(service, enabled)
        } catch (e: ReflectiveOperationException) {
            fail(
                "IActivityManager.setAlwaysFinish is unreachable on this image " +
                    "($e) — the hidden-API surface moved, so the don't-keep bracket " +
                    "cannot flip the live flag; ProcessControl's header needs revisiting",
            )
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
        val echoed = Shell.readSetting("global", "always_finish_activities")
        val expected = if (enabled) "1" else "0"
        if (echoed != expected) {
            fail(
                "setAlwaysFinish($enabled) did not echo into the persisted setting " +
                    "(read '$echoed') — the binder call did not reach the " +
                    "ActivityTaskManager",
            )
        }
    }

    /**
     * Send the app to the background (HOME key via the shell — the user's own gesture),
     * then wait (bounded) until no activity of the app is in the RESUMED stage. Under
     * [withDontKeepActivities] this is the destruction trigger.
     */
    fun backgroundApp(timeoutMs: Long = 10_000L) {
        Shell.exec("input keyevent KEYCODE_HOME")
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (resumedActivity() == null) return
            Thread.sleep(100)
        }
        fail("the app still has a RESUMED activity ${timeoutMs}ms after HOME")
    }

    /**
     * Relaunch the app's launcher activity (`am start -W -n`, resolved from the app's own
     * launch intent — never hardcoded) and return the newly RESUMED activity instance.
     * The instance is how the exemplar proves destruction happened: same instance back
     * means nothing was destroyed and the bracket proved nothing.
     */
    fun relaunchApp(timeoutMs: Long = 10_000L): Activity {
        val component = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.component
            ?: fail("no launch intent for ${context.packageName} — nothing to relaunch")
                .let { error("unreachable") }
        Shell.exec("am start -W -n ${component.flattenToShortString()}")
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            resumedActivity()?.let { return it }
            Thread.sleep(100)
        }
        fail("no activity reached RESUMED within ${timeoutMs}ms of am start")
        error("unreachable")
    }

    /**
     * Wait (bounded) until [activity] is genuinely destroyed. Backgrounding and
     * destruction are two separate asynchronous steps — relaunching between them brings
     * the SAME instance back and the bracket proves nothing, so the exemplar waits for
     * this before relaunching.
     */
    fun awaitDestroyed(activity: Activity, timeoutMs: Long = 10_000L) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            var destroyed = false
            instrumentation.runOnMainSync { destroyed = activity.isDestroyed }
            if (destroyed) return
            Thread.sleep(100)
        }
        fail(
            "the backgrounded activity was not destroyed within ${timeoutMs}ms — is " +
                "don't-keep-activities actually on (withDontKeepActivities), and was " +
                "the app really backgrounded first?",
        )
    }

    /** The app's currently RESUMED activity, or null (read on the main thread, as the monitor requires). */
    fun resumedActivity(): Activity? {
        var current: Activity? = null
        instrumentation.runOnMainSync {
            current = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).firstOrNull()
        }
        return current
    }

    /**
     * `am kill` — the low-memory-reclaim model, exposed for the one thing it can honestly
     * do in this seam: kill the app's AUXILIARY processes (components declared with
     * `android:process`), which are not pinned the way the instrumented main process is.
     * Against the main process it is a no-op while the test runs (header); the exemplar
     * asserts that fact live, so the day an OS change breaks the assumption, the suite
     * says so instead of this comment silently rotting.
     */
    fun killReclaimableProcesses(packageName: String = context.packageName) {
        Shell.exec("am kill $packageName")
    }
}
