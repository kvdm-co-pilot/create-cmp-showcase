package com.kvdm.fuelled.presentation.foods

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.usecase.GetFoodsUseCase
import com.kvdm.fuelled.domain.usecase.SearchFoodsUseCase
import com.kvdm.fuelled.testing.StructuralTree
import com.kvdm.fuelled.testing.awaitNode
import com.kvdm.fuelled.testing.fakes.FakeFoodRepository
import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Golden-tree structural baseline — SPEC: FOODS-08.
 *
 * Renders Foods headlessly with FIXED fake data and diffs the semantics structure against the
 * committed baseline (`qa/golden/foods.json`). No pixels, no flake: a failure means the
 * screen's STRUCTURE changed.
 *
 * Unintended drift → fix your change. Intended drift → regenerate the baseline explicitly
 * and declare it in your summary/PR:
 *
 *   UPDATE_GOLDEN=1 ./gradlew :composeApp:desktopTest --tests "*GoldenTree*"
 */
@OptIn(ExperimentalTestApi::class)
class FoodsGoldenTreeTest {

    private val baseline = File("../qa/golden/foods.json")

    // Fixed dataset — golden renders must be deterministic; never use live/random data here.
    private val goldenFoods = listOf(
        Food("1", "Golden chicken", "Baseline", "100 g", 165, 31, 0, 4),
        Food("2", "Golden oats", "Baseline", "80 g", 303, 11, 54, 6),
    )

    // SPEC: FOODS-08
    @Test
    fun `foods structure matches the committed golden tree`() = runComposeUiTest {
        val repository = FakeFoodRepository().apply { foods = goldenFoods }
        val viewModel = FoodsViewModel(GetFoodsUseCase(repository), SearchFoodsUseCase(repository))

        setContent {
            MaterialTheme {
                FoodsRoute(onFoodClick = {}, viewModel = viewModel)
            }
        }
        awaitNode(hasText("Golden chicken"))

        val rendered = StructuralTree.serialize(onRoot(useUnmergedTree = true).fetchSemanticsNode())

        if (System.getenv("UPDATE_GOLDEN") == "1") {
            baseline.parentFile.mkdirs()
            baseline.writeText(rendered)
            return@runComposeUiTest
        }

        if (!baseline.exists()) fail(
            "[FOODS-08] Golden baseline missing (qa/golden/foods.json). " +
                "Generate it explicitly: UPDATE_GOLDEN=1 ./gradlew :composeApp:desktopTest --tests \"*GoldenTree*\"",
        )

        val expected = baseline.readText()
        if (rendered != expected) {
            val diffAt = rendered.zip(expected).indexOfFirst { (a, b) -> a != b }
                .let { if (it == -1) minOf(rendered.length, expected.length) else it }
            fail(
                "[FOODS-08] Foods' rendered structure drifted from qa/golden/foods.json (first diff at char $diffAt).\n" +
                    "If this drift is UNINTENDED: fix your change.\n" +
                    "If it is the intended change: regenerate with UPDATE_GOLDEN=1 and declare it.\n" +
                    "--- rendered (excerpt) ---\n${rendered.substring(maxOf(0, diffAt - 120), minOf(rendered.length, diffAt + 240))}\n" +
                    "--- baseline (excerpt) ---\n${expected.substring(maxOf(0, diffAt - 120), minOf(expected.length, diffAt + 240))}",
            )
        }
    }
}
