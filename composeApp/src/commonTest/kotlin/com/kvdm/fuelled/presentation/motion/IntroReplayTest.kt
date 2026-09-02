package com.kvdm.fuelled.presentation.motion

import com.kvdm.fuelled.presentation.theme.MotionScheme
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The rule that decides whether opening the app replays the ignition. It exists as a pure
 * function because the bug it fixes was invisible to every desktop test: the root does not
 * recompose on a warm resume, so a composition-held flag left the ignition unreachable for
 * every already-onboarded user (observed on-device, 2026-09-02).
 */
class IntroReplayTest {

    // SPEC: MOTION-13
    @Test
    fun `a return after the threshold replays the ignition`() {
        assertTrue(shouldReplayIntro(IntroReplayAfter, MotionScheme.Full), "exactly the threshold counts")
        assertTrue(shouldReplayIntro(5.minutes, MotionScheme.Full))
        assertTrue(shouldReplayIntro(IntroReplayAfter, MotionScheme.Reduced), "reduced motion still gets the moment")
    }

    // SPEC: MOTION-13
    @Test
    fun `coming straight back does not replay it`() {
        assertFalse(shouldReplayIntro(1.seconds, MotionScheme.Full), "glancing at a notification is not an app open")
        assertFalse(shouldReplayIntro(IntroReplayAfter - 1.milliseconds, MotionScheme.Full), "just under the threshold")
        assertFalse(shouldReplayIntro(0.seconds, MotionScheme.Full))
    }

    // SPEC: MOTION-13
    @Test
    fun `never having been away is not a return`() {
        assertFalse(shouldReplayIntro(null, MotionScheme.Full))
        assertFalse(shouldReplayIntro(null, MotionScheme.Reduced))
    }

    // SPEC: MOTION-13
    @Test
    fun `Instant never replays, however long the app was away`() {
        assertFalse(shouldReplayIntro(5.minutes, MotionScheme.Instant), "tests and previews get one end state")
    }
}
