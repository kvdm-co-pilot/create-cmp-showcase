package com.kvdm.fuelled.presentation.motion

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.presentation.theme.FuelledTheme
import com.kvdm.fuelled.presentation.theme.MotionScheme
import com.kvdm.fuelled.testing.awaitNode
import kotlin.test.Test
import kotlin.test.assertEquals

/** The ignition's guarantees (MOTION-15): it exists, it ends, and it ends exactly once. */
@OptIn(ExperimentalTestApi::class)
class IntroScreenTest {

    // SPEC: MOTION-15
    @Test
    fun `under Instant the ignition is over on the first frame, and done fires exactly once`() = runComposeUiTest {
        var done = 0
        setContent { MaterialTheme { IntroScreen(onDone = { done++ }) } }
        awaitNode(hasTestTag("intro_screen"))
        waitForIdle()
        assertEquals(1, done, "Instant: done on frame 0, once")
        onNodeWithTag("intro_screen").performClick()
        waitForIdle()
        assertEquals(1, done, "a tap after it ended is not a second ending")
    }

    // SPEC: MOTION-15
    @Test
    fun `a tap skips the ignition, and the choreography's own ending does not fire again`() = runComposeUiTest {
        var done = 0
        setContent {
            FuelledTheme(motion = MotionScheme.Full) { IntroScreen(onDone = { done++ }) }
        }
        awaitNode(hasTestTag("intro_screen"))
        onNodeWithTag("intro_screen").performClick()
        waitForIdle()
        assertEquals(1, done, "the tap ends it")
        // Let the choreography run to its natural end: it must not call done a second time.
        mainClock.advanceTimeBy(3_000)
        waitForIdle()
        assertEquals(1, done, "the ending is exactly once")
    }
}
