package com.kvdm.fuelled.presentation.today

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.LogEntry
import com.kvdm.fuelled.domain.model.LogStatus
import com.kvdm.fuelled.domain.model.MacroProgress
import com.kvdm.fuelled.domain.model.MealGroup
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.TodayModel
import com.kvdm.fuelled.domain.usecase.GetTodaySummaryUseCase
import com.kvdm.fuelled.presentation.navigation.Routes
import com.kvdm.fuelled.testing.StructuralTree
import com.kvdm.fuelled.testing.awaitNode
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import com.kvdm.fuelled.testing.fakes.FixedClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

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
    fun `renders the logical day's date, the calorie ring's remaining, and no error`() = runComposeUiTest {
        // consumed 535 of 2400 → 1865 remaining. The model carries a LocalDate (the logical
        // day, MEAL-01) and the screen formats it — there is no stored label to render.
        repository.summary = FakeTodayRepository.populatedDay.copy(date = LocalDate(2026, 7, 22))

        setContent {
            MaterialTheme { TodayRoute(viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("today_screen"))
        onNodeWithTag("today_title", useUnmergedTree = true).assertExists()
        onAllNodesWithText("WEDNESDAY, JUL 22").assertCountEquals(1)
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
    fun `groups the log by meal slot in slot order with each meal's total calories`() = runComposeUiTest {
        repository.summary = TodayModel(
            date = LocalDate(2026, 7, 22),
            // 535 LOGGED (430 + 105); the planned 116 kcal snack is NOT part of it.
            consumedKcal = 535,
            targetKcal = 2400,
            protein = MacroProgress("Protein", 39, 180, "g"),
            carbs = MacroProgress("Carbs", 79, 260, "g"),
            fat = MacroProgress("Fat", 9, 70, "g"),
            meals = listOf(
                // Two entries → the meal total (535) is distinct from any single entry's kcal.
                MealGroup(MealSlot.BREAKFAST, listOf(
                    LogEntry("b1", "Rolled oats & whey", "80 g · 1 scoop", 430, 38),
                    LogEntry("b2", "Banana", "1 medium", 105, 1),
                )),
                MealGroup(MealSlot.DINNER, listOf(LogEntry("d1", "Salmon fillet", "180 g", 360, 40))),
                MealGroup(MealSlot.SNACK, listOf(
                    LogEntry("s1", "Almonds", "20 g", 116, 4, status = LogStatus.PLANNED),
                )),
            ),
        )

        setContent {
            MaterialTheme { TodayRoute(viewModel = viewModel()) }
        }

        awaitNode(hasText("Breakfast"))
        // The slot's LABEL is rendered from the closed enum (MEAL-03) — no free-text meal name.
        onAllNodesWithText("Dinner").assertCountEquals(1)
        onAllNodesWithText("Snack").assertCountEquals(1)
        onAllNodesWithText("Rolled oats & whey").assertCountEquals(1)
        onAllNodesWithText("535 kcal").assertCountEquals(1) // Breakfast meal total = 430 + 105

        // The PLANNED entry is still rendered in its meal group — it is scheduled, not hidden.
        onAllNodesWithText("Almonds").assertCountEquals(1)
        // …but it is not eaten: the ring reads 2400 - 535, with the planned 116 kcal excluded.
        onAllNodesWithText("1865").assertCountEquals(1)

        // Slot ORDER, asserted structurally: Breakfast before Dinner before Snack, top to
        // bottom in the rendered tree — not the order they were handed to the screen by name.
        val tree = StructuralTree.serialize(onRoot(useUnmergedTree = true).fetchSemanticsNode())
        val breakfastAt = tree.indexOf(""""text": "Breakfast"""")
        val dinnerAt = tree.indexOf(""""text": "Dinner"""")
        val snackAt = tree.indexOf(""""text": "Snack"""")
        assertTrue(breakfastAt in 0 until dinnerAt, "Breakfast renders before Dinner")
        assertTrue(dinnerAt < snackAt, "Dinner renders before Snack")
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

    // SPEC: TODAY-07
    @Test
    fun `every meal slot on the day carries its own add control`() = runComposeUiTest {
        repository.summary = FakeTodayRepository.populatedDay // Breakfast + Snack

        setContent {
            MaterialTheme { TodayRoute(viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("today_screen"))
        onNodeWithTag("today_add_breakfast", useUnmergedTree = true).assertExists()
        onNodeWithTag("today_add_snack", useUnmergedTree = true).assertExists()
        // A slot the day has no card for offers no control — the affordance belongs to the card.
        onNodeWithTag("today_add_lunch").assertDoesNotExist()
    }

    // SPEC: TODAY-07
    @Test
    fun `a meal's add control opens the tray targeted at that logical day and that slot`() = runComposeUiTest {
        repository.summary = FakeTodayRepository.populatedDay.copy(
            date = LocalDate(2026, 7, 22),
            meals = listOf(
                MealGroup(MealSlot.BREAKFAST, listOf(LogEntry("b1", "Rolled oats", "80 g", 430, 38))),
                MealGroup(MealSlot.DINNER, listOf(LogEntry("d1", "Salmon fillet", "180 g", 360, 40))),
            ),
        )
        // What the tap ASKS FOR: the navigation request the shell would issue, built by the
        // same Routes function the nav graph uses — not just the callback's arguments.
        var requested: String? = null

        setContent {
            MaterialTheme {
                TodayRoute(
                    viewModel = viewModel(),
                    onAddToMeal = { date, slot -> requested = Routes.mealTray(date, slot) },
                )
            }
        }

        awaitNode(hasTestTag("today_add_dinner"))
        onNodeWithTag("today_add_dinner").performClick()
        assertEquals("meal/2026-07-22/DINNER", requested)

        // The second tap is the actual proof of TODAY-07: a control that defaulted (to the
        // day's first slot, or to a clock-derived one) would answer both taps identically.
        onNodeWithTag("today_add_breakfast").performClick()
        assertEquals("meal/2026-07-22/BREAKFAST", requested)
    }

    // SPEC: TODAY-08
    @Test
    fun `the empty state's add control opens the tray on the current day at the slot for the time`() =
        runComposeUiTest {
            repository.summary = FakeTodayRepository.emptyDay.copy(date = LocalDate(2026, 7, 22))
            var requested: String? = null

            setContent {
                MaterialTheme {
                    TodayRoute(
                        viewModel = viewModel(),
                        onAddToMeal = { date, slot -> requested = Routes.mealTray(date, slot) },
                        // 19:00 sits in the Dinner window (MEAL-04). Fixed here because the
                        // production seam reads the wall clock, and a test that did too would
                        // assert a different slot depending on the hour it ran.
                        slotForNow = {
                            currentMealSlot(
                                clock = FixedClock(LocalDateTime(2026, 7, 22, 19, 0).toInstant(TimeZone.UTC)),
                                zone = TimeZone.UTC,
                            )
                        },
                    )
                }
            }

            awaitNode(hasTestTag("today_empty_add"))
            onNodeWithTag("today_empty_add").performClick()
            assertEquals("meal/2026-07-22/DINNER", requested)
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
