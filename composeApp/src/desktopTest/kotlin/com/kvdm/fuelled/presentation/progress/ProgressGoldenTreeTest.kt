package com.kvdm.fuelled.presentation.progress

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.testing.StructuralTree
import com.kvdm.fuelled.testing.awaitNode
import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Golden-tree structural baseline — SPEC: JRN-03.
 *
 * Renders the Progress surface with its FIXED sample fixture and diffs the semantics structure
 * against the committed baseline (`qa/golden/progress.json`). No pixels, no flake: a failure means
 * the screen's STRUCTURE changed.
 *
 * Unintended drift → fix your change. Intended drift → regenerate the baseline explicitly and
 * declare it:
 *
 *   UPDATE_GOLDEN=1 ./gradlew :composeApp:desktopTest --tests "*GoldenTree*"
 */
@OptIn(ExperimentalTestApi::class)
class ProgressGoldenTreeTest {

    private val baseline = File("../qa/golden/progress.json")

    // SPEC: JRN-03
    @Test
    fun `progress structure matches the committed golden tree`() = runComposeUiTest {
        // The stateless screen with its fixed fixture — deterministic by construction (ARCH-12).
        setContent {
            MaterialTheme {
                ProgressScreen(progress = ProgressUi())
            }
        }
        awaitNode(hasTestTag("week_screen"))

        val rendered = StructuralTree.serialize(onRoot(useUnmergedTree = true).fetchSemanticsNode())

        if (System.getenv("UPDATE_GOLDEN") == "1") {
            baseline.parentFile.mkdirs()
            baseline.writeText(rendered)
            return@runComposeUiTest
        }
        if (!baseline.exists()) {
            fail("No golden baseline at ${baseline.path} — generate it with UPDATE_GOLDEN=1")
        }
        val expected = baseline.readText()
        if (expected != rendered) {
            fail(
                "[JRN-03] The week review's rendered structure drifted from qa/golden/progress.json.\n" +
                    "If this drift is UNINTENDED: fix your change.\n" +
                    "If it is the intended change: regenerate with UPDATE_GOLDEN=1 and declare it.",
            )
        }
    }
}
