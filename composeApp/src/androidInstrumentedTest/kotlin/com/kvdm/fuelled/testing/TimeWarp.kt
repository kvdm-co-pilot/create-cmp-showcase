package com.kvdm.fuelled.testing

import android.os.SystemClock

/**
 * Device-clock manipulation for the on-device tier — the primitive that turns "the alarm
 * ladder is unit-tested to the minute" into "we watched the alarm actually arrive".
 *
 * Why this exists: scheduled delivery is the one claim the other helpers still could not
 * close. [AlarmAsserts] proves REGISTRATION (the OS holds the alarm), unit tests prove the
 * ladder's arithmetic, and yet "the notification arrives at 08:00" shipped untested every
 * time — nobody waits two days inside a test run. Warping the device clock past the
 * trigger time makes delivery observable in seconds: schedule for T, warp to T+ε, and the
 * OS either fires the alarm or it doesn't. The same mechanism makes DST transitions and
 * date rollovers ([withTimeZone]) a test input instead of a twice-a-year production
 * surprise.
 *
 * EMULATOR-ONLY, NON-NEGOTIABLE — every entry point runs [assumeOnEmulator] first:
 * warping a real phone's clock corrupts the owner's world, not just the test's — alarms
 * re-fire or vanish, TLS certificates fall outside their validity window, auth tokens
 * expire or refuse to, and messaging apps reorder history. A QA emulator is disposable
 * state; a person's device never is. On a non-emulator the guard SKIPs (assumption
 * violation), never fails — the suite stays honest about where it can run.
 *
 * Constraints the code can't show:
 *  - No root involved. `adb root` is refused on production (user-build) images, including
 *    the stock Play-image AVDs — but `cmd alarm set-time` / `set-timezone` work from the
 *    SHELL uid, which is exactly what [Shell.exec]'s UiAutomation channel provides.
 *    Time-warp proofs therefore run on any stock emulator, no special image.
 *  - Network time sync fights the warp: with `auto_time` on, the device re-corrects the
 *    clock underneath the test. Every warp is bracketed — sync off, warp, run, restore,
 *    sync back on — and the restore runs in `finally`, so a crashed block still leaves
 *    the device usable for the next test.
 *  - The restore is not "set the clock back to what it was": real time keeps passing
 *    while the block runs, and restoring the ORIGINAL instant would leave the clock
 *    slow by the block's duration (compounding across a suite). The restore target is
 *    `original + elapsed`, with elapsed measured on [SystemClock.elapsedRealtime] —
 *    the monotonic clock, immune to the warp itself.
 *  - Inside a warped block, bound your waits on monotonic time (`CountDownLatch.await`,
 *    [SystemClock.elapsedRealtime]), never on `System.currentTimeMillis()` — wall-clock
 *    deadlines computed before the warp are nonsense after it.
 */
object TimeWarp {

    /**
     * SKIPs the test unless it is running on an emulator (`ro.kernel.qemu == 1`).
     * Called by every warp entry point; call it yourself at the top of a warp test so
     * the skip names the test, not a helper frame. See the header for why a real
     * device is refused: a warped personal phone breaks the owner's alarms, tokens,
     * and messaging — QA emulators only.
     */
    fun assumeOnEmulator() {
        Shell.assumeOnEmulator(
            "TimeWarp runs only on emulators (ro.kernel.qemu != 1 here). Warping a real " +
                "device's clock corrupts its owner's alarms, TLS validity, and auth " +
                "tokens — run this suite on a stock QA AVD instead.",
        )
    }

    /**
     * Run [block] with the device wall clock set to [targetEpochMillis], restoring
     * everything afterwards even when the block throws:
     *
     *   1. `auto_time` off — otherwise network time sync re-corrects mid-block;
     *   2. `cmd alarm set-time target` — the warp (shell uid, root-free);
     *   3. the block — schedule/await/assert;
     *   4. finally: `set-time (original + elapsed)` then `auto_time` back on.
     *
     * The restore adds the block's real duration (monotonic-measured) to the original
     * instant so the device clock never falls behind true time — see the header.
     */
    fun <T> withWarpedClock(targetEpochMillis: Long, block: () -> T): T {
        assumeOnEmulator()
        val autoTimeBefore = readGlobal("auto_time")
        val originalEpochMillis = System.currentTimeMillis()
        val startElapsed = SystemClock.elapsedRealtime()
        Shell.exec("settings put global auto_time 0")
        try {
            Shell.exec("cmd alarm set-time $targetEpochMillis")
            return block()
        } finally {
            val elapsed = SystemClock.elapsedRealtime() - startElapsed
            Shell.exec("cmd alarm set-time ${originalEpochMillis + elapsed}")
            Shell.exec("settings put global auto_time ${autoTimeBefore ?: "1"}")
        }
    }

    /**
     * Run [block] with the device in time zone [olsonId] (e.g. `"Pacific/Chatham"`,
     * `"America/New_York"`), restoring the original zone and `auto_time_zone` afterwards
     * even when the block throws. This is the DST/date-rollover assertion primitive:
     * combine with [withWarpedClock] to place the device just before a transition and
     * watch what a "tomorrow at 08:00" schedule actually does. No elapsed-time math here
     * — a zone, unlike a clock, does not drift while the block runs.
     */
    fun <T> withTimeZone(olsonId: String, block: () -> T): T {
        assumeOnEmulator()
        val autoZoneBefore = readGlobal("auto_time_zone")
        val originalZone = Shell.exec("getprop persist.sys.timezone").trim()
        Shell.exec("settings put global auto_time_zone 0")
        try {
            Shell.exec("cmd alarm set-timezone $olsonId")
            return block()
        } finally {
            if (originalZone.isNotEmpty()) {
                Shell.exec("cmd alarm set-timezone $originalZone")
            }
            Shell.exec("settings put global auto_time_zone ${autoZoneBefore ?: "1"}")
        }
    }

    /** A global setting's current value, or null when unset — see [Shell.readSetting]. */
    private fun readGlobal(key: String): String? = Shell.readSetting("global", key)
}
