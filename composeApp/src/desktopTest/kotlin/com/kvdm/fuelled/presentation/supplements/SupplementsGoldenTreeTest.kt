package com.kvdm.fuelled.presentation.supplements

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.domain.model.SupplementTiming
import com.kvdm.fuelled.domain.usecase.GetSupplementStackUseCase
import com.kvdm.fuelled.domain.usecase.SetSupplementTakenUseCase
import com.kvdm.fuelled.testing.StructuralTree
import com.kvdm.fuelled.testing.awaitNode
import com.kvdm.fuelled.testing.fakes.FakeSupplementRepository
import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Golden-tree structural baseline — SPEC: SUPP-06.
 *
 * Renders Supplements headlessly with FIXED fake data and diffs the semantics structure against
 * the committed baseline (`qa/golden/supplements.json`). No pixels, no flake: a failure means
 * the screen's STRUCTURE changed.
 *
 * Unintended drift → fix your change. Intended drift → regenerate the baseline explicitly and
 * declare it:
 *
 *   UPDATE_GOLDEN=1 ./gradlew :composeApp:desktopTest --tests "*GoldenTree*"
 */
@OptIn(ExperimentalTestApi::class)
class SupplementsGoldenTreeTest {

    private val baseline = File("../qa/golden/supplements.json")

    // Fixed dataset — golden renders must be deterministic; never use live/random data here.
    private val goldenStack = listOf(
        Supplement("1", "Golden creatine", "5 g", SupplementTiming.MORNING, taken = true),
        Supplement("2", "Golden omega", "1 g", SupplementTiming.MORNING, taken = false),
        Supplement("3", "Golden caffeine", "200 mg", SupplementTiming.PRE_WORKOUT, taken = false),
    )

    // SPEC: SUPP-06
    @Test
    fun `supplements structure matches the committed golden tree`() = runComposeUiTest {
        val repository = FakeSupplementRepository().apply { stack = goldenStack }
        val viewModel = SupplementsViewModel(
            GetSupplementStackUseCase(repository),
            SetSupplementTakenUseCase(repository),
        )

        setContent {
            MaterialTheme {
                SupplementsRoute(viewModel = viewModel)
            }
        }
        awaitNode(hasTestTag("supplements_screen"))

        val rendered = StructuralTree.serialize(onRoot(useUnmergedTree = true).fetchSemanticsNode())

        if (System.getenv("UPDATE_GOLDEN") == "1") {
            baseline.parentFile.mkdirs()
            baseline.writeText(rendered)
            return@runComposeUiTest
        }

        if (!baseline.exists()) fail(
            "[SUPP-06] Golden baseline missing (qa/golden/supplements.json). " +
                "Generate it explicitly: UPDATE_GOLDEN=1 ./gradlew :composeApp:desktopTest --tests \"*GoldenTree*\"",
        )

        val expected = baseline.readText()
        if (rendered != expected) {
            val diffAt = rendered.zip(expected).indexOfFirst { (a, b) -> a != b }
                .let { if (it == -1) minOf(rendered.length, expected.length) else it }
            fail(
                "[SUPP-06] Supplements' rendered structure drifted from qa/golden/supplements.json (first diff at char $diffAt).\n" +
                    "If this drift is UNINTENDED: fix your change.\n" +
                    "If it is the intended change: regenerate with UPDATE_GOLDEN=1 and declare it.\n" +
                    "--- rendered (excerpt) ---\n${rendered.substring(maxOf(0, diffAt - 120), minOf(rendered.length, diffAt + 240))}\n" +
                    "--- baseline (excerpt) ---\n${expected.substring(maxOf(0, diffAt - 120), minOf(expected.length, diffAt + 240))}",
            )
        }
    }
}
