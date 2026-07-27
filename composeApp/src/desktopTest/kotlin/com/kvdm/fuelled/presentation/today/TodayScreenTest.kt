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
import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.testing.fakes.FakeMealPlanRepository
import com.kvdm.fuelled.testing.fakes.FakeSupplementRepository
import com.kvdm.fuelled.testing.todayViewModel
import com.kvdm.fuelled.presentation.navigation.Routes
import com.kvdm.fuelled.testing.StructuralTree
import com.kvdm.fuelled.testing.awaitNode
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/**
 * Durable screen tests — first-party Compose UI Test, spec-cited, testTag selectors. Each test
 * verifies a clause from `specs/today.spec.md` against the VM-backed [TodayRoute] wired to a
 * hand-written fake repository (mirrors FoodsScreenTest).
 */
@OptIn(ExperimentalTestApi::class)
class TodayScreenTest {

    private val repository = FakeTodayRepository()

    /** The logical day every test here sits in — the fixture's frozen 2026-07-22 12:45 UTC. */
    private val TEST_DATE = LocalDate(2026, 7, 22)

    private fun viewModel() = todayViewModel(today = repository)

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

    // SPEC: TODAY-04
    @Test
    fun `shows an empty day with the ring reading the full target and no error`() = runComposeUiTest {
        repository.summary = FakeTodayRepository.emptyDay // consumed 0 of 2400, no meal cards

        setContent {
            MaterialTheme { TodayRoute(viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("today_screen"))
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

    // SPEC: TODAY-07, TODAY-09
    @Test
    fun `today shows exactly one meal container - the focused one - with its own add control`() =
        runComposeUiTest {
            // Frozen at 12:45 with breakfast and the morning snack ticked: lunch is the earliest
            // slot neither done nor missed, so it holds focus and nothing else is on screen.
            val plan = FakeMealPlanRepository().apply {
                doneSlots[TEST_DATE] = mutableSetOf(MealSlot.BREAKFAST, MealSlot.MORNING_SNACK)
            }

            setContent {
                MaterialTheme { TodayRoute(viewModel = todayViewModel(today = repository, plan = plan)) }
            }

            awaitNode(hasTestTag("today_screen"))
            onNodeWithTag("today_slot_lunch", useUnmergedTree = true).assertExists()
            onNodeWithTag("today_add_lunch", useUnmergedTree = true).assertExists()
            // EXACTLY one: Today is the highlights, not the day. Every other container — done,
            // missed, or still upcoming — lives on the plan screen (TODAY-12).
            onNodeWithTag("today_slot_breakfast").assertDoesNotExist()
            onNodeWithTag("today_slot_dinner").assertDoesNotExist()
        }

    // SPEC: TODAY-07
    @Test
    fun `the focused container's add control opens the tray targeted at that day and that slot`() =
        runComposeUiTest {
            val plan = FakeMealPlanRepository().apply {
                doneSlots[TEST_DATE] = mutableSetOf(MealSlot.BREAKFAST, MealSlot.MORNING_SNACK)
            }
            // What the tap ASKS FOR: the navigation request the shell would issue, built by the
            // same Routes function the nav graph uses — not just the callback's arguments.
            var requested: String? = null

            setContent {
                MaterialTheme {
                    TodayRoute(
                        viewModel = todayViewModel(today = repository, plan = plan),
                        onAddToMeal = { date, slot -> requested = Routes.mealTray(date, slot) },
                    )
                }
            }

            awaitNode(hasTestTag("today_add_lunch"))
            onNodeWithTag("today_add_lunch").performClick()
            // The target is the FOCUSED container's, carried from the tap — not the day's first
            // slot and not a clock guess (MEAL-10, PLAN-04).
            assertEquals("meal/2026-07-22/LUNCH", requested)
        }

    // SPEC: TODAY-09
    @Test
    fun `ticking the focused container from today advances the focus without leaving the screen`() =
        runComposeUiTest {
            val plan = FakeMealPlanRepository().apply {
                doneSlots[TEST_DATE] = mutableSetOf(MealSlot.BREAKFAST, MealSlot.MORNING_SNACK)
            }

            setContent {
                MaterialTheme { TodayRoute(viewModel = todayViewModel(today = repository, plan = plan)) }
            }

            awaitNode(hasTestTag("today_done_lunch"))
            onNodeWithTag("today_done_lunch").performClick()

            // Self-advancing: lunch is recorded done and the container ON SCREEN becomes the
            // afternoon snack — the next slot neither done nor missed at 12:45.
            awaitNode(hasTestTag("today_slot_afternoon_snack"))
            onNodeWithTag("today_slot_lunch").assertDoesNotExist()
            assertTrue(MealSlot.LUNCH in plan.doneSlots.getValue(TEST_DATE))
        }

    // SPEC: TODAY-10
    @Test
    fun `today shows the next unticked water and ticking it raises the day's litres`() =
        runComposeUiTest {
            val plan = FakeMealPlanRepository().apply {
                waterTicks[TEST_DATE] = mutableSetOf(1, 2)
            }

            setContent {
                MaterialTheme { TodayRoute(viewModel = todayViewModel(today = repository, plan = plan)) }
            }

            awaitNode(hasTestTag("today_screen"))
            // Containers 1 and 2 are drunk, so the NEXT one is 3 — and only that one is shown.
            onNodeWithTag("today_water_3", useUnmergedTree = true).assertExists()
            onNodeWithTag("today_water_4").assertDoesNotExist()
            onAllNodesWithText("Water 1.0 / 3.0 L").assertCountEquals(1)

            onNodeWithTag("today_water_done_3").performClick()

            // 0.5 L more, and the row advances to the next undrunk container.
            awaitNode(hasTestTag("today_water_4"))
            onAllNodesWithText("Water 1.5 / 3.0 L").assertCountEquals(1)
        }

    // SPEC: TODAY-11
    @Test
    fun `today summarizes the current supplement bucket and opens the supplements tab`() =
        runComposeUiTest {
            val supplements = FakeSupplementRepository().apply {
                stack = listOf(
                    Supplement("s1", "Multivitamin", "1 tab", "Morning", taken = true),
                    Supplement("s2", "Omega-3", "2 caps", "Morning", taken = false),
                    Supplement("s3", "Magnesium", "1 tab", "Evening", taken = false),
                )
            }
            var openedSupplements = false

            setContent {
                MaterialTheme {
                    TodayRoute(
                        viewModel = todayViewModel(today = repository, supplements = supplements),
                        onOpenSupplements = { openedSupplements = true },
                    )
                }
            }

            awaitNode(hasTestTag("today_supplements"))
            // The FIRST bucket with anything outstanding — Morning, 1 of 2 — not Evening, and
            // not a clock-derived bucket, because Supplement carries no time (SUPP-02).
            onAllNodesWithText("Supplements · 1 of 2 taken").assertCountEquals(1)
            onAllNodesWithText("Morning").assertCountEquals(1)

            onNodeWithTag("today_supplements").performClick()
            assertTrue(openedSupplements, "the highlight opens the Supplements tab; Today never edits the stack")
        }

    // SPEC: TODAY-12
    @Test
    fun `today offers one link into the full week, opened at the current logical day`() =
        runComposeUiTest {
            var requested: String? = null

            setContent {
                MaterialTheme {
                    TodayRoute(
                        viewModel = todayViewModel(today = repository),
                        onOpenPlan = { date -> requested = Routes.mealPlan(date) },
                    )
                }
            }

            awaitNode(hasTestTag("today_plan_link"))
            onNodeWithTag("today_plan_link").performClick()
            assertEquals("plan/2026-07-22", requested)
            // And Today does NOT render the week itself — there is no day strip here.
            onNodeWithTag("plan_days").assertDoesNotExist()
        }

    // SPEC: TODAY-14
    @Test
    fun `today shows the day's vegetable count against the method's two`() = runComposeUiTest {
        val plan = FakeMealPlanRepository().apply {
            entries[TEST_DATE] = mapOf(
                MealSlot.BREAKFAST to listOf(LogEntry("b1", "Rolled oats", "80 g", 430, 38)),
                MealSlot.LUNCH to listOf(
                    LogEntry("l1", "Chicken breast", "200 g", 330, 62),
                    LogEntry("l2", "Mixed greens", "1 bowl", 90, 3, veg = true),
                ),
            )
        }

        setContent {
            MaterialTheme { TodayRoute(viewModel = todayViewModel(today = repository, plan = plan)) }
        }

        awaitNode(hasTestTag("today_veg_total"))
        // One CONTAINER holds a vegetable, not one food — three portions of greens at lunch
        // would still be one meal with veg, which is what the method's rule is about.
        onAllNodesWithText("Veg 1 of 2").assertCountEquals(1)
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
