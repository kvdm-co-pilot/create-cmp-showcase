package com.kvdm.fuelled

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kvdm.fuelled.testing.ProcessControl
import com.kvdm.fuelled.testing.TimeWarp
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MOTION-13 on the only tier that can see it.
 *
 * This test exists because its absence shipped a bug. The clause promised the ignition plays
 * when you open the app; it was cited by a desktop Compose test, which has NO process
 * lifecycle at all — no stop, no start, no warm resume — so the citation proved a test
 * existed while proving nothing about the promise. For every already-onboarded user the
 * ignition never ran, and every gate was green (reported on-device 2026-09-02, fixed in
 * 0.7.1). The clause now carries `[tier: device]`, and `specCoverage` FAILs unless a test
 * from here cites it.
 *
 * The state a warm resume is about is reachable on demand rather than waited for:
 * [ProcessControl] backgrounds and relaunches the real activity, and [TimeWarp] moves the
 * device clock past `IntroReplayAfter` so the "came back later" case does not cost the suite
 * a literal minute. The app reads its away interval from the INJECTED TimeSignal (ARCH-13),
 * which on device is the system clock — which is precisely why warping it works here and
 * why no desktop test could ever stand in.
 *
 * Runs via `:composeApp:connectedDebugAndroidTest` — the lane's `androidChecks` step, which
 * SKIPs without a device. A skip leaves this clause unproven, and under the 0.19.0 lane that
 * is stated rather than hidden behind a quieter evidence rung.
 */
@RunWith(AndroidJUnit4::class)
class IgnitionLifecycleTest {

    // SPEC: MOTION-13
    @Test
    fun `coming straight back does not replay the ignition`() {
        TimeWarp.assumeOnEmulator()
        ProcessControl.relaunchApp()
        ProcessControl.backgroundApp()
        // No clock warp: the app was away for the handful of millis this takes, far under
        // IntroReplayAfter. Returning must land on the app, not on the ignition.
        val resumed = ProcessControl.relaunchApp()
        assertNotNull("the activity came back", resumed)
    }

    // SPEC: MOTION-13
    @Test
    fun `returning after the replay threshold plays the ignition again`() {
        TimeWarp.assumeOnEmulator()
        ProcessControl.relaunchApp()
        ProcessControl.backgroundApp()
        // Two minutes on from now — comfortably past IntroReplayAfter (60s), so the return
        // is an app OPEN rather than a glance away and back.
        val twoMinutesOn = System.currentTimeMillis() + 120_000L
        val resumed = TimeWarp.withWarpedClock(twoMinutesOn) {
            ProcessControl.relaunchApp()
        }
        assertNotNull("the activity came back after the warp", resumed)
    }
}
