package com.kvdm.fuelled.testing

import android.os.SystemClock
import org.junit.Assert.assertEquals

/**
 * Doze (device idle) control for the on-device tier — put the device INTO the state the
 * claim is about, instead of shipping the claim and waiting for the field to test it.
 *
 * Why this exists: Doze is the #1 production cause of "the alarm never fired". A device
 * that sits still on battery drops into deep idle, and in that state regular alarms are
 * deferred to the next maintenance window — hours away — while `setExactAndAllowWhileIdle`
 * exists ENTIRELY to punch through it. That API's whole value is its behavior in a state
 * no test tier ever visited: the JVM tiers can't see the OS, [AlarmAsserts] proves the OS
 * holds the alarm, [TimeWarp] proves delivery on an awake device — and "delivers from
 * inside Doze", the actual production claim, shipped unverified every time. This organ
 * closes that: force the idle state, then let TimeWarp warp past the trigger, and the
 * delivery-from-Doze claim becomes a sixty-second test (see RuntimeStateSeamTest for the
 * composed exemplar).
 *
 * The sequence, each command verified root-free from the shell uid on a stock user-build
 * emulator image (API 35):
 *
 *   1. `dumpsys battery unplug` — fake "on battery". Real Doze never engages on charge;
 *      modern `force-idle` no longer strictly requires this, but older API levels did and
 *      the precondition is part of the state being modeled, so it stays.
 *   2. `dumpsys deviceidle force-idle deep|light` — teleport straight into the idle
 *      state ("Now forced in to deep idle mode"). Verified with `deviceidle get` — the
 *      command's own output is not trusted.
 *   3. the block — schedule/warp/assert.
 *   4. finally: `dumpsys deviceidle unforce` + `dumpsys battery reset`, then a bounded
 *      poll until the controller reports ACTIVE again. A test that leaves the device
 *      idle poisons every later test, so the restore runs even when the block throws.
 *
 * HONESTY — what forced idle does and does not reproduce. Overclaiming here would be
 * worse than the gap, so read this before citing a green test as proof:
 *
 *  REPRODUCED faithfully:
 *   - The idle STATE itself, as AlarmManager sees it: regular alarms deferred,
 *     `setAndAllowWhileIdle`/`setExactAndAllowWhileIdle` eligible to fire (throttled to
 *     one window per app per ~9 minutes — one alarm per test is inside the budget).
 *     This is the alarm-delivery policy, keyed on the DEVICE state, and it is exactly
 *     the claim the composed exemplar proves.
 *   - Light vs deep as distinct states (`IdleMode`): light restricts jobs/syncs; deep
 *     is the full alarm-deferral regime. An exact-alarm claim is about DEEP.
 *
 *  NOT reproduced:
 *   - The path INTO idle. `force-idle` skips the real ladder (screen-off, stationary,
 *     IDLE_PENDING → SENSING → LOCATING → IDLE), so motion-exit and the timing of the
 *     descent are untested here.
 *   - Maintenance windows. Forced idle HOLDS the state; real deep Doze alternates
 *     IDLE ↔ IDLE_MAINTENANCE. "My deferred work runs in the maintenance window" is a
 *     different claim and this organ does not prove it.
 *   - App-standby buckets. A separate throttling system (`am set-standby-bucket`),
 *     untouched by this organ.
 *   - The cached-process experience. Instrumentation pins the app's process at
 *     foreground oom-adj, so the network-cutoff and job-suspension that a CACHED process
 *     feels in deep Doze do not bite the app under test. The OS-side alarm policy above
 *     is unaffected by that pinning — which is why the alarm claim survives this caveat
 *     and a "my background sync pauses in Doze" claim does not.
 *   - OEM battery managers (aggressive kill lists, vendor "optimization"). Emulator-only
 *     by construction; the OEM half stays a documented manual tier.
 */
object DozeControl {

    /** The two real idle regimes. An exact-alarm claim is about [DEEP]. */
    enum class IdleMode(internal val arg: String) { LIGHT("light"), DEEP("deep") }

    /**
     * Run [block] with the device forced into [mode] idle, restoring everything
     * afterwards even when the block throws. Entry is VERIFIED (`deviceidle get` must
     * report IDLE) before the block runs — a bracket that silently failed to enter the
     * state would hand out green for a claim it never tested.
     *
     * Composes with the other organs by nesting; the canonical composition is
     * Doze outside, clock inside:
     *
     *   DozeControl.withDeviceIdle {
     *     TimeWarp.withWarpedClock(target + epsilon) { awaitDelivery() }
     *   }
     */
    fun <T> withDeviceIdle(mode: IdleMode = IdleMode.DEEP, block: () -> T): T {
        Shell.assumeOnEmulator(
            "DozeControl runs only on emulators (ro.kernel.qemu != 1 here). Forcing a " +
                "real phone into idle silences its owner's real alarms and messages — " +
                "run this suite on a stock QA AVD instead.",
        )
        Shell.exec("dumpsys battery unplug")
        Shell.exec("dumpsys deviceidle force-idle ${mode.arg}")
        try {
            assertEquals(
                "the device did not enter forced ${mode.arg} idle — without the state, " +
                    "nothing this block asserts is about Doze. On a stock emulator this " +
                    "sequence is verified to work; a changed image or API may need " +
                    "`dumpsys deviceidle help` re-read.",
                "IDLE",
                Shell.exec("dumpsys deviceidle get ${mode.arg}").trim(),
            )
            return block()
        } finally {
            Shell.exec("dumpsys deviceidle unforce")
            Shell.exec("dumpsys battery reset")
            // Bounded settle: on the verified image `unforce` returns to ACTIVE
            // immediately, but the next test deserves the wait, not the race.
            val deadline = SystemClock.elapsedRealtime() + 5_000L
            while (SystemClock.elapsedRealtime() < deadline &&
                Shell.exec("dumpsys deviceidle get ${mode.arg}").trim() != "ACTIVE"
            ) {
                Thread.sleep(100)
            }
        }
    }
}
