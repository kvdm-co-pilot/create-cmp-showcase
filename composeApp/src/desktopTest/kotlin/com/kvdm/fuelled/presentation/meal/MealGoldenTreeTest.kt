package com.kvdm.fuelled.presentation.meal

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.usecase.AddLogEntriesUseCase
import com.kvdm.fuelled.domain.usecase.GetFoodsUseCase
import com.kvdm.fuelled.domain.usecase.SearchFoodsUseCase
import com.kvdm.fuelled.testing.StructuralTree
import com.kvdm.fuelled.testing.awaitNode
import com.kvdm.fuelled.testing.fakes.FakeFoodRepository
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import com.kvdm.fuelled.testing.fakes.FixedClock
import java.io.File
import kotlin.test.Test
import kotlin.test.fail
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Golden-tree structural baseline for the add-to-meal tray — SPEC: MEAL-12.
 *
 * Renders the tray headlessly through the REAL path (MealTrayRoute + MealTrayViewModel) with
 * FIXED fake data and a FIXED clock, ticks one item so the running total is a real total, and
 * diffs the semantics structure against the committed baseline (`qa/golden/meal.json`). No
 * pixels, no flake: a failure means the screen's STRUCTURE changed.
 *
 * The clock matters here in a way it does not for the other goldens: the tray's header states
 * its target date and slot (MEAL-10), so "whatever instant the run happened at" would rewrite
 * the baseline's text every afternoon.
 *
 * Unintended drift → fix your change. Intended drift → regenerate the baseline explicitly and
 * declare it:
 *
 *   UPDATE_GOLDEN=1 ./gradlew :composeApp:desktopTest --tests "*GoldenTree*"
 */
@OptIn(ExperimentalTestApi::class)
class MealGoldenTreeTest {

    private val baseline = File("../qa/golden/meal.json")

    private val zone = TimeZone.UTC

    // 12:30 on a fixed logical day. The target is now stated explicitly (MEAL-10, PLAN-04) —
    // Lunch, 2026-07-22 — rather than guessed from the clock; the clock still fixes `currentDay`.
    private val openedAt = LocalDateTime(2026, 7, 22, 12, 30)
    private val targetDate = LocalDate(2026, 7, 22)

    private val goldenFoods = listOf(
        Food("1", "Chicken breast", "Raw · skinless", "100 g", 165, 31, 0, 4),
        Food("2", "Rolled oats", "Quaker", "80 g", 303, 11, 54, 6),
    )

    // SPEC: MEAL-12
    @Test
    fun `meal tray structure matches the committed golden tree`() = runComposeUiTest {
        val foodRepository = FakeFoodRepository().apply { foods = goldenFoods }
        val clock = FixedClock(openedAt.toInstant(zone))
        val viewModel = MealTrayViewModel(
            getFoods = GetFoodsUseCase(foodRepository),
            searchFoods = SearchFoodsUseCase(foodRepository),
            addLogEntries = AddLogEntriesUseCase(FakeTodayRepository(), clock, zone, DEFAULT_DAY_START_HOUR),
            initialTarget = MealTrayInitialTarget(date = targetDate, slot = MealSlot.LUNCH),
            clock = clock,
            zone = zone,
            dayStartHour = DEFAULT_DAY_START_HOUR,
        )

        setContent {
            MaterialTheme {
                MealTrayRoute(viewModel = viewModel)
            }
        }
        awaitNode(hasTestTag("meal_tray_item_1"))

        // Tick one item the way a user does, so the baseline captures a tray with a real
        // running total rather than the zero state.
        onNodeWithTag("meal_tray_item_1").performClick()
        waitForIdle()

        val rendered = StructuralTree.serialize(onRoot(useUnmergedTree = true).fetchSemanticsNode())

        if (System.getenv("UPDATE_GOLDEN") == "1") {
            baseline.parentFile.mkdirs()
            baseline.writeText(rendered)
            return@runComposeUiTest
        }

        if (!baseline.exists()) fail(
            "[MEAL-12] Golden baseline missing (qa/golden/meal.json). " +
                "Generate it explicitly: UPDATE_GOLDEN=1 ./gradlew :composeApp:desktopTest --tests \"*GoldenTree*\"",
        )

        val expected = baseline.readText()
        if (rendered != expected) {
            val diffAt = rendered.zip(expected).indexOfFirst { (a, b) -> a != b }
                .let { if (it == -1) minOf(rendered.length, expected.length) else it }
            fail(
                "[MEAL-12] The meal tray's rendered structure drifted from qa/golden/meal.json (first diff at char $diffAt).\n" +
                    "If this drift is UNINTENDED: fix your change.\n" +
                    "If it is the intended change: regenerate with UPDATE_GOLDEN=1 and declare it.\n" +
                    "--- rendered (excerpt) ---\n${rendered.substring(maxOf(0, diffAt - 120), minOf(rendered.length, diffAt + 240))}\n" +
                    "--- baseline (excerpt) ---\n${expected.substring(maxOf(0, diffAt - 120), minOf(expected.length, diffAt + 240))}",
            )
        }
    }
}
