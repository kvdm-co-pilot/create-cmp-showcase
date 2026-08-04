package com.kvdm.fuelled.presentation.today

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.domain.model.LogEntry
import com.kvdm.fuelled.domain.model.MacroProgress
import com.kvdm.fuelled.domain.model.MealGroup
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.TodayModel
import com.kvdm.fuelled.testing.todayViewModel
import com.kvdm.fuelled.testing.StructuralTree
import com.kvdm.fuelled.testing.awaitNode
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import java.io.File
import kotlin.test.Test
import kotlin.test.fail
import kotlinx.datetime.LocalDate

/**
 * Golden-tree structural baseline — SPEC: TODAY-06.
 *
 * Renders Today headlessly with FIXED fake data and diffs the semantics structure against the
 * committed baseline (`qa/golden/today.json`). No pixels, no flake: a failure means the
 * screen's STRUCTURE changed.
 *
 * Unintended drift → fix your change. Intended drift → regenerate the baseline explicitly and
 * declare it:
 *
 *   UPDATE_GOLDEN=1 ./gradlew :composeApp:desktopTest --tests "*GoldenTree*"
 */
@OptIn(ExperimentalTestApi::class)
class TodayGoldenTreeTest {

    private val baseline = File("../qa/golden/today.json")

    // Fixed dataset — golden renders must be deterministic; never use live/random data here.
    // The date is a FIXED LocalDate for the same reason: the model now carries the logical day
    // (TODAY-01), and "whatever day the run happened on" would make this baseline unstable.
    private val goldenDay = TodayModel(
        date = LocalDate(2026, 7, 22),
        consumedKcal = 535,
        targetKcal = 2400,
        protein = MacroProgress("Protein", 39, 180, "g"),
        carbs = MacroProgress("Carbs", 79, 260, "g"),
        fat = MacroProgress("Fat", 9, 70, "g"),
        meals = listOf(
            MealGroup(MealSlot.BREAKFAST, listOf(LogEntry("g1", "Golden oats", "80 g", 430, 38))),
            MealGroup(MealSlot.MORNING_SNACK, listOf(LogEntry("g2", "Golden banana", "1 medium", 105, 1))),
        ),
    )

    // SPEC: TODAY-06
    // SPEC: WORK-09 — the workout card is part of Today's committed structure.
    @Test
    fun `today structure matches the committed golden tree`() = runComposeUiTest {
        val repository = FakeTodayRepository().apply { summary = goldenDay }
        val viewModel = todayViewModel(today = repository)

        setContent {
            MaterialTheme {
                TodayRoute(viewModel = viewModel)
            }
        }
        awaitNode(hasTestTag("today_screen"))

        val rendered = StructuralTree.serialize(onRoot(useUnmergedTree = true).fetchSemanticsNode())

        if (System.getenv("UPDATE_GOLDEN") == "1") {
            baseline.parentFile.mkdirs()
            baseline.writeText(rendered)
            return@runComposeUiTest
        }

        if (!baseline.exists()) fail(
            "[TODAY-06] Golden baseline missing (qa/golden/today.json). " +
                "Generate it explicitly: UPDATE_GOLDEN=1 ./gradlew :composeApp:desktopTest --tests \"*GoldenTree*\"",
        )

        val expected = baseline.readText()
        if (rendered != expected) {
            val diffAt = rendered.zip(expected).indexOfFirst { (a, b) -> a != b }
                .let { if (it == -1) minOf(rendered.length, expected.length) else it }
            fail(
                "[TODAY-06] Today's rendered structure drifted from qa/golden/today.json (first diff at char $diffAt).\n" +
                    "If this drift is UNINTENDED: fix your change.\n" +
                    "If it is the intended change: regenerate with UPDATE_GOLDEN=1 and declare it.\n" +
                    "--- rendered (excerpt) ---\n${rendered.substring(maxOf(0, diffAt - 120), minOf(rendered.length, diffAt + 240))}\n" +
                    "--- baseline (excerpt) ---\n${expected.substring(maxOf(0, diffAt - 120), minOf(expected.length, diffAt + 240))}",
            )
        }
    }
}
