package com.kvdm.fuelled.presentation.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.onRoot
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.presentation.navigation.AppTab
import com.kvdm.fuelled.presentation.theme.FuelledTheme
import com.kvdm.fuelled.presentation.theme.LocalMotion
import com.kvdm.fuelled.presentation.theme.MotionScheme
import com.kvdm.fuelled.testing.awaitNode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The motion primitives' guarantees (`specs/motion.spec.md`): what the semantics tree shows
 * under the schemes the tests and goldens run under. Every screen's choreography is built
 * from these, so this is where "motion never changes what a test can see" is proven.
 */
@OptIn(ExperimentalTestApi::class)
class MotionPrimitivesTest {

    /** A recording haptic seam — the fake in place of the platform's. */
    private class RecordingHaptics : HapticFeedback {
        val performed = mutableListOf<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            performed += hapticFeedbackType
        }
    }

    private fun hasRange(fraction: Float) = SemanticsMatcher("progress $fraction") {
        it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.current == fraction
    }

    // SPEC: MOTION-02
    @Test
    fun `with no FuelledTheme above the scheme is Instant, and the theme provides what it is given`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Text("bare:${LocalMotion.current}", Modifier.semantics { testTag = "bare" })
                FuelledTheme(motion = MotionScheme.Reduced) {
                    Text("themed:${LocalMotion.current}", Modifier.semantics { testTag = "themed" })
                }
            }
        }
        awaitNode(hasTestTag("bare"))
        onNodeWithTag("bare").assertTextEquals("bare:Instant")
        onNodeWithTag("themed").assertTextEquals("themed:Reduced")
    }

    // SPEC: MOTION-05
    @Test
    fun `the bottom bar's selected item reports selected, only the items are children, and a tap moves selection`() = runComposeUiTest {
        var selected by mutableStateOf(0)
        val tabs = listOf(
            AppTab("Today", Icons.Filled.Today) {},
            AppTab("Week", Icons.Filled.Today) {},
            AppTab("Meals", Icons.Filled.Today) {},
        )
        setContent {
            MaterialTheme { AppBottomBar(tabs = tabs, selectedIndex = selected, onSelect = { selected = it }) }
        }
        awaitNode(hasTestTag("nav_today"))
        onNodeWithTag("nav_today").assertIsSelected()
        onNodeWithTag("nav_week").assertIsNotSelected()
        // The pill is drawn, never composed: the bar's children are exactly the items.
        onNodeWithTag("app_bottom_nav").onChildren().assertCountEquals(3)
        onNodeWithTag("nav_week").performClick()
        awaitNode(hasTestTag("nav_week") and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        onNodeWithTag("nav_week").assertIsSelected()
        onNodeWithTag("nav_today").assertIsNotSelected()
        assertEquals(1, selected)
    }

    // SPEC: MOTION-07
    @Test
    fun `an AnimatedNumber shows its target value on the first frame and follows a change`() = runComposeUiTest {
        var value by mutableStateOf(1461)
        setContent {
            MaterialTheme {
                AnimatedNumber(value = value, countFrom = 0, format = { "$it kcal" }, modifier = Modifier.semantics { testTag = "n" })
            }
        }
        awaitNode(hasTestTag("n"))
        onNodeWithTag("n").assertTextEquals("1461 kcal")
        value = 1697
        awaitNode(hasTestTag("n") and hasText("1697 kcal"))
    }

    // SPEC: MOTION-08
    @Test
    fun `a ring and a bar report the target fraction, clamped`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ProgressRing(progress = 0.61f, sweepFrom = 0f, modifier = Modifier.semantics { testTag = "ring" })
                StatBar(progress = 1.7f, fillFrom = 0f, modifier = Modifier.semantics { testTag = "bar" })
            }
        }
        awaitNode(hasTestTag("ring"))
        onNodeWithTag("ring").assert(hasRange(0.61f))
        onNodeWithTag("bar").assert(hasRange(1f))
    }

    // SPEC: MOTION-09
    // SPEC: MOTION-11
    @Test
    fun `a TickButton keeps AppIconButton's tree in both states and hums once when it turns on`() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var checked by mutableStateOf(false)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    TickButton(
                        icon = Icons.Filled.Check,
                        checked = checked,
                        contentDescription = if (checked) "Undo Lunch done" else "Mark Lunch done",
                        onClick = { checked = !checked },
                        modifier = Modifier.semantics { testTag = "tick" },
                    )
                }
            }
        }
        awaitNode(hasTestTag("tick"))
        onNodeWithTag("tick").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        onNodeWithTag("tick", useUnmergedTree = true).onChildren().assertCountEquals(1)
        onNodeWithTag("tick", useUnmergedTree = true).onChildren()[0].assert(hasContentDescription("Mark Lunch done"))
        assertEquals(emptyList(), haptics.performed, "first composition unchecked: silent")

        onNodeWithTag("tick").performClick()
        awaitNode(hasTestTag("tick") and hasContentDescription("Undo Lunch done"))
        onNodeWithTag("tick", useUnmergedTree = true).onChildren().assertCountEquals(1)
        assertEquals(listOf(HapticFeedbackType.ToggleOn), haptics.performed, "turning on hums once")

        onNodeWithTag("tick").performClick()
        awaitNode(hasTestTag("tick") and hasContentDescription("Mark Lunch done"))
        assertEquals(listOf(HapticFeedbackType.ToggleOn), haptics.performed, "un-ticking is quiet")
    }

    // SPEC: MOTION-11
    @Test
    fun `a TickButton composed already checked does not hum`() = runComposeUiTest {
        val haptics = RecordingHaptics()
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    TickButton(icon = Icons.Filled.Check, checked = true, contentDescription = "Undo", onClick = {}, modifier = Modifier.semantics { testTag = "tick" })
                }
            }
        }
        awaitNode(hasTestTag("tick"))
        assertEquals(emptyList(), haptics.performed)
    }

    // SPEC: MOTION-10
    // SPEC: MOTION-11
    @Test
    fun `the goal bloom hums Confirm once per trigger and stays quiet for null`() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var trigger by mutableStateOf<String?>(null)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    Text("protein", Modifier.goalBloom(trigger).semantics { testTag = "card" })
                }
            }
        }
        awaitNode(hasTestTag("card"))
        assertEquals(emptyList(), haptics.performed, "no goal, no bloom")
        trigger = "2026-07-22"
        waitForIdle()
        assertEquals(listOf(HapticFeedbackType.Confirm), haptics.performed, "reaching the goal blooms once")
        trigger = "2026-07-22"
        waitForIdle()
        assertEquals(1, haptics.performed.size, "the same day never re-fires")
        trigger = "2026-07-23"
        waitForIdle()
        assertEquals(2, haptics.performed.size, "a new logical day fires again")
    }

    // SPEC: MOTION-12
    @Test
    fun `a state container renders the new arm immediately when the state changes`() = runComposeUiTest {
        var state by mutableStateOf<ContentUiState<String>>(ContentUiState.Loading)
        setContent {
            MaterialTheme {
                ContentStateContainer(state = state, screenTag = "demo") { data ->
                    Text(data, Modifier.semantics { testTag = "demo_content" })
                }
            }
        }
        awaitNode(hasTestTag("demo_loading"))
        state = ContentUiState.Content("loaded")
        awaitNode(hasTestTag("demo_content"))
        onNodeWithTag("demo_content").assertTextEquals("loaded")
        state = ContentUiState.Empty
        awaitNode(hasTestTag("demo_empty"))
        state = ContentUiState.Error("boom")
        awaitNode(hasTestTag("demo_error"))
        onNodeWithText("boom").assertExists()
    }

    /**
     * The content check MOTION-14 exists for: motion may nest content deeper, but it may never
     * change the content itself. This is the mechanical form of the by-hand golden diff — and
     * the check that would have caught the contract's first draft, which promised node counts
     * it could not keep.
     */
    private fun androidx.compose.ui.semantics.SemanticsNode.content(): List<String> {
        val out = mutableListOf<String>()
        fun walk(n: androidx.compose.ui.semantics.SemanticsNode) {
            val tag = n.config.getOrNull(SemanticsProperties.TestTag)
            val role = n.config.getOrNull(SemanticsProperties.Role)?.toString()
            val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            val desc = n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
            if (tag != null || role != null || text != null || desc != null) {
                out += "tag=$tag role=$role text=$text desc=$desc"
            }
            n.children.forEach(::walk)
        }
        walk(this)
        return out
    }

    // SPEC: MOTION-14
    @Test
    fun `the primitives change where content sits, never the content a test selects on`() {
        fun render(withMotion: Boolean): List<String> {
            var captured: List<String> = emptyList()
            runComposeUiTest {
                setContent {
                    MaterialTheme {
                        // Instant everywhere, so this compares SHAPE at rest, not a frame in flight.
                        val rise = if (withMotion) Modifier.enterRise(2) else Modifier
                        val press = if (withMotion) Modifier.pressable(remember { MutableInteractionSource() }) else Modifier
                        val bloom = if (withMotion) Modifier.goalBloom(null) else Modifier
                        ContentStateContainer(
                            state = ContentUiState.Content("loaded"),
                            screenTag = "demo",
                        ) { data ->
                            Column(rise.then(bloom)) {
                                Text(data, Modifier.semantics { testTag = "demo_value" })
                                TickButton(
                                    icon = Icons.Filled.Check,
                                    checked = true,
                                    contentDescription = "Undo Lunch done",
                                    onClick = {},
                                    modifier = press.semantics { testTag = "demo_tick" },
                                )
                                ProgressRing(progress = 0.5f, sweepFrom = if (withMotion) 0f else null)
                                StatBar(progress = 0.5f, label = "Protein", valueText = "121 / 180g")
                            }
                        }
                    }
                }
                awaitNode(hasTestTag("demo_value"))
                captured = onRoot(useUnmergedTree = true).fetchSemanticsNode().content()
            }
            return captured
        }
        assertEquals(
            render(withMotion = false),
            render(withMotion = true),
            "motion may nest content deeper, but every tag, role, text and description must survive it",
        )
    }
}
