package com.kvdm.fuelled.presentation.workouts

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.testing.StructuralTree
import com.kvdm.fuelled.testing.awaitNode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Golden-tree structural baseline for the Training tab — SPEC: NAV-06.
 *
 * The tab shipped without one: navigation-ia added a screen and its ViewModel test but no
 * structural baseline, so nothing would have caught a silent change to the week's shape.
 *
 * Renders the STATELESS screen against its fixed sample (no VM, no clock) — the sample is
 * date-pinned by construction (ARCH-12), so this cannot drift by calendar day the way the
 * Supplements golden once did.
 *
 * Unintended drift → fix your change. Intended drift → regenerate and declare it:
 *
 *   UPDATE_GOLDEN=1 ./gradlew :composeApp:desktopTest --tests "*GoldenTree*"
 */
@OptIn(ExperimentalTestApi::class)
class WorkoutWeekGoldenTreeTest {

    private val baseline = File("../qa/golden/training.json")

    // SPEC: NAV-06
    @Test
    fun `training structure matches the committed golden tree`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                WorkoutWeekScreen(model = sampleWorkoutWeek)
            }
        }
        awaitNode(hasTestTag("training_screen"))

        val rendered = StructuralTree.serialize(onRoot(useUnmergedTree = true).fetchSemanticsNode())

        if (System.getenv("UPDATE_GOLDEN") == "1") {
            baseline.parentFile.mkdirs()
            baseline.writeText(rendered)
            return@runComposeUiTest
        }

        if (!baseline.exists()) fail(
            "[NAV-06] Golden baseline missing (qa/golden/training.json). " +
                "Generate it explicitly: UPDATE_GOLDEN=1 ./gradlew :composeApp:desktopTest --tests \"*GoldenTree*\"",
        )

        val expected = baseline.readText()
        if (rendered != expected) {
            val diffAt = rendered.zip(expected).indexOfFirst { (a, b) -> a != b }
                .let { if (it == -1) minOf(rendered.length, expected.length) else it }
            fail(
                "[NAV-06] Training's rendered structure drifted from qa/golden/training.json (first diff at char $diffAt).\n" +
                    "If this drift is UNINTENDED: fix your change.\n" +
                    "If it is the intended change: regenerate with UPDATE_GOLDEN=1 and declare it.\n" +
                    "--- rendered (excerpt) ---\n${rendered.substring(maxOf(0, diffAt - 120), minOf(rendered.length, diffAt + 240))}\n" +
                    "--- baseline (excerpt) ---\n${expected.substring(maxOf(0, diffAt - 120), minOf(expected.length, diffAt + 240))}",
            )
        }
    }
}

/**
 * The summary line's copy rules (NAV-06) — a pure function, so it is tested as one rather than
 * through a rendered screen.
 */
class WorkoutWeekSummaryTest {

    // SPEC: NAV-06
    @Test
    fun `mid-week reads as kept and still to come, never as missed`() {
        // Mon/Tue kept, Wed today, Thu-Sat ahead, Sun rest.
        assertEquals("2 kept · 4 to come", sampleWorkoutWeek.summaryLabel())
    }

    // SPEC: NAV-06
    @Test
    fun `a clean week names no misses at all`() {
        val perfect = sampleWorkoutWeek.copy(
            days = sampleWorkoutWeek.days.map { if (it.plan.isTraining) it.copy(done = true) else it },
            today = kotlinx.datetime.LocalDate(2026, 7, 27),
        )
        assertEquals("6 kept", perfect.summaryLabel())
    }

    // SPEC: NAV-06
    @Test
    fun `a finished week with a gap names the miss`() {
        val past = sampleWorkoutWeek.copy(today = kotlinx.datetime.LocalDate(2026, 7, 27))
        assertEquals("2 kept · 4 missed", past.summaryLabel())
    }
}
