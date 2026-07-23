package com.kvdm.fuelled.presentation.today

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.LogEntry
import com.kvdm.fuelled.domain.model.MacroProgress
import com.kvdm.fuelled.domain.model.MealGroup
import com.kvdm.fuelled.domain.model.TodayModel
import com.kvdm.fuelled.domain.usecase.GetTodaySummaryUseCase
import com.kvdm.fuelled.testing.awaitNode
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import kotlin.test.Test

/**
 * Durable screen tests — first-party Compose UI Test, spec-cited, testTag selectors. Each test
 * verifies a clause from `specs/today.spec.md` against the VM-backed [TodayRoute] wired to a
 * hand-written fake repository (mirrors FoodsScreenTest).
 */
@OptIn(ExperimentalTestApi::class)
class TodayScreenTest {

    private val repository = FakeTodayRepository()

    private fun viewModel() = TodayViewModel(GetTodaySummaryUseCase(repository))

    // SPEC: TODAY-01
    @Test
    fun `renders the date, the calorie ring's remaining, and no error`() = runComposeUiTest {
        // consumed 535 of 2400 → 1865 remaining.
        repository.summary = FakeTodayRepository.populatedDay

        setContent {
            MaterialTheme { TodayRoute(viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("today_screen"))
        onNodeWithTag("today_title", useUnmergedTree = true).assertExists()
        onAllNodesWithText("WEDNESDAY, JUL 23").assertCountEquals(1)
        onAllNodesWithText("1865").assertCountEquals(1) // kcal remaining
        onAllNodesWithText(DomainError.Network.toUserMessage()).assertCountEquals(0)
    }

    // SPEC: TODAY-02
    @Test
    fun `surfaces protein as the focus with grams remaining to goal`() = runComposeUiTest {
        repository.summary = FakeTodayRepository.populatedDay // protein 39 / 180 → 141 to go

        setContent {
            MaterialTheme { TodayRoute(viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("today_screen"))
        onAllNodesWithText("141g").assertCountEquals(1)
        onAllNodesWithText("to go").assertCountEquals(1)
    }

    // SPEC: TODAY-02
    @Test
    fun `shows a goal-met state when protein reaches its target`() = runComposeUiTest {
        repository.summary = FakeTodayRepository.populatedDay.copy(
            protein = MacroProgress("Protein", 190, 180, "g"),
        )

        setContent {
            MaterialTheme { TodayRoute(viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("today_screen"))
        onAllNodesWithText("goal hit").assertCountEquals(1)
    }

    // SPEC: TODAY-03
    @Test
    fun `groups the log by meal with each meal's total calories`() = runComposeUiTest {
        repository.summary = TodayModel(
            dateLabel = "Wednesday, Jul 23",
            consumedKcal = 535,
            targetKcal = 2400,
            protein = MacroProgress("Protein", 39, 180, "g"),
            carbs = MacroProgress("Carbs", 79, 260, "g"),
            fat = MacroProgress("Fat", 9, 70, "g"),
            meals = listOf(
                // Two entries → the meal total (535) is distinct from any single entry's kcal.
                MealGroup("Breakfast", listOf(
                    LogEntry("Rolled oats & whey", "80 g · 1 scoop", 430, 38),
                    LogEntry("Banana", "1 medium", 105, 1),
                )),
                MealGroup("Snack", listOf(LogEntry("Almonds", "20 g", 116, 4))),
            ),
        )

        setContent {
            MaterialTheme { TodayRoute(viewModel = viewModel()) }
        }

        awaitNode(hasText("Breakfast"))
        onAllNodesWithText("Snack").assertCountEquals(1)
        onAllNodesWithText("Rolled oats & whey").assertCountEquals(1)
        onAllNodesWithText("535 kcal").assertCountEquals(1) // Breakfast meal total = 430 + 105
    }

    // SPEC: TODAY-04
    @Test
    fun `shows the empty log affordance with the ring reading the full target and no error`() = runComposeUiTest {
        repository.summary = FakeTodayRepository.emptyDay // consumed 0 of 2400

        setContent {
            MaterialTheme { TodayRoute(viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("today_empty"))
        onAllNodesWithText("2400").assertCountEquals(1) // full target remaining
        onNodeWithTag("today_error").assertDoesNotExist()
    }

    // SPEC: TODAY-05
    @Test
    fun `shows presentation-mapped error copy and retry when loading fails`() = runComposeUiTest {
        repository.failure = DomainError.Network

        setContent {
            MaterialTheme { TodayRoute(viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("today_error"))
        onAllNodesWithText(DomainError.Network.toUserMessage()).assertCountEquals(1)
        onNodeWithTag("today_retry", useUnmergedTree = true).assertExists()
    }

    // SPEC: TODAY-05
    @Test
    fun `tapping retry after a failure reloads and shows the recovered summary`() = runComposeUiTest {
        repository.failure = DomainError.Network
        val vm = viewModel()

        setContent {
            MaterialTheme { TodayRoute(viewModel = vm) }
        }

        awaitNode(hasTestTag("today_error"))

        repository.failure = null
        repository.summary = FakeTodayRepository.populatedDay
        onNodeWithTag("today_retry").performClick()

        awaitNode(hasTestTag("today_screen"))
    }
}
