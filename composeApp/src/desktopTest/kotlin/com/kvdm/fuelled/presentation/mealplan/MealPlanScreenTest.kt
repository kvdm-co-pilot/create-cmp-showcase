package com.kvdm.fuelled.presentation.mealplan

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.domain.model.LogEntry
import com.kvdm.fuelled.domain.model.LogStatus
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.usecase.ArmMealRemindersUseCase
import com.kvdm.fuelled.domain.usecase.CopyDayForwardUseCase
import com.kvdm.fuelled.domain.usecase.GetPlanDayUseCase
import com.kvdm.fuelled.domain.usecase.SetSlotDoneUseCase
import com.kvdm.fuelled.domain.usecase.SetWaterDoneUseCase
import com.kvdm.fuelled.presentation.navigation.Routes
import com.kvdm.fuelled.testing.TEST_NOW
import com.kvdm.fuelled.testing.TEST_ZONE
import com.kvdm.fuelled.testing.awaitNode
import com.kvdm.fuelled.testing.fakes.FakeMealPlanRepository
import com.kvdm.fuelled.testing.fakes.FakeReminderScheduler
import com.kvdm.fuelled.testing.fakes.FixedClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import com.kvdm.fuelled.testing.fakes.FakeTimeSignal

/**
 * Durable screen tests for the structured day — first-party Compose UI Test, spec-cited,
 * testTag selectors, against the VM-backed [MealPlanRoute] over a hand-written fake.
 *
 * The clock is FIXED at 2026-07-22 12:45 (the shared test fixture's instant), because half of
 * what this screen renders — focus, lateness, missed-ness — is a function of now.
 */
@OptIn(ExperimentalTestApi::class)
class MealPlanScreenTest {

    private val today = LocalDate(2026, 7, 22)
    private val repository = FakeMealPlanRepository(FakeTimeSignal(TEST_NOW), TEST_ZONE)

    private fun viewModel(on: LocalDate = today): MealPlanViewModel {
        val getPlanDay = GetPlanDayUseCase(repository, time = FakeTimeSignal(TEST_NOW), zone = TEST_ZONE)
        return MealPlanViewModel(
            initialDate = on,
            getPlanDay = getPlanDay,
            setSlotDone = SetSlotDoneUseCase(repository),
            setWaterDone = SetWaterDoneUseCase(repository),
            copyDayForward = CopyDayForwardUseCase(repository),
            armReminders = ArmMealRemindersUseCase(repository, FakeReminderScheduler()),
        )
    }

    // SPEC: PLAN-02
    @Test
    fun `all six containers render, interleaved with six waters, on a day never planned`() =
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    MealPlanRoute(onAddToMeal = { _, _ -> }, onOpenTimes = {}, viewModel = viewModel())
                }
            }

            awaitNode(hasTestTag("meal_plan_screen"))
            // A day with nothing written to it is still a full day — this is the clause's whole
            // point: the containers come from the enum, not from rows that happen to exist.
            MealSlot.entries.forEach { slot ->
                onNodeWithTag("plan_slot_${slot.name.lowercase()}", useUnmergedTree = true).assertExists()
            }
            (1..6).forEach { onNodeWithTag("plan_water_$it", useUnmergedTree = true).assertExists() }
            // Each container states its slot time (PLAN-05's defaults).
            onAllNodesWithText("07:00").assertCountEquals(1)
            onAllNodesWithText("19:30").assertCountEquals(1)
        }

    // SPEC: PLAN-04
    @Test
    fun `an empty container's body IS its add control, and the tap carries that container's target`() =
        runComposeUiTest {
            var requested: String? = null

            setContent {
                MaterialTheme {
                    MealPlanRoute(
                        onAddToMeal = { date, slot -> requested = Routes.mealTray(date, slot) },
                        onOpenTimes = {},
                        viewModel = viewModel(),
                    )
                }
            }

            awaitNode(hasTestTag("plan_add_dinner"))
            // The day is taller than the window, so scroll the control into view before
            // tapping it — a click at an off-screen coordinate lands on nothing.
            onNodeWithTag("plan_add_dinner").performScrollTo().performClick()
            assertEquals("meal/2026-07-22/DINNER", requested)

            // The second tap is the proof: a control that defaulted would answer both the same.
            onNodeWithTag("plan_add_breakfast").performScrollTo().performClick()
            assertEquals("meal/2026-07-22/BREAKFAST", requested)
        }

    // SPEC: PLAN-19
    @Test
    fun `a missed container is muted but keeps its add control and its tick`() = runComposeUiTest {
        repository.entries[today] = mapOf(
            MealSlot.BREAKFAST to listOf(LogEntry("b1", "Rolled oats", "80 g", 430, 38, status = LogStatus.PLANNED)),
        )

        setContent {
            MaterialTheme {
                MealPlanRoute(onAddToMeal = { _, _ -> }, onOpenTimes = {}, viewModel = viewModel())
            }
        }

        awaitNode(hasTestTag("plan_slot_breakfast"))
        // At 12:45 with nothing ticked, TWO containers are missed: breakfast (its successor's
        // 09:30 has passed) and the morning snack (lunch's 12:00 has passed). Both read muted;
        // alarm styling belongs only to the one slot still actionable in time.
        onAllNodesWithText("MISSED").assertCountEquals(2)
        // Back-fillable, not closed: both affordances are still live (PLAN-19).
        onNodeWithTag("plan_done_breakfast", useUnmergedTree = true).assertExists()
        onNodeWithTag("plan_add_breakfast", useUnmergedTree = true).assertExists()

        onNodeWithTag("plan_done_breakfast").performScrollTo().performClick()
        waitUntil { repository.doneCalls.isNotEmpty() }
        assertTrue(MealSlot.BREAKFAST in repository.doneSlots.getValue(today))
    }

    // SPEC: PLAN-10
    @Test
    fun `ticking water raises the day's litres on screen`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MealPlanRoute(onAddToMeal = { _, _ -> }, onOpenTimes = {}, viewModel = viewModel())
            }
        }

        awaitNode(hasTestTag("plan_water_total"))
        onAllNodesWithText("Water 0.0 / 3.0 L").assertCountEquals(1)

        onNodeWithTag("plan_water_done_1").performScrollTo().performClick()
        waitUntil { repository.waterCalls.isNotEmpty() }
        waitForIdle()
        onAllNodesWithText("Water 0.5 / 3.0 L").assertCountEquals(1)
    }

    // SPEC: PLAN-11
    @Test
    fun `the day strip is the only date selector and selecting a day renders it`() = runComposeUiTest {
        repository.entries[LocalDate(2026, 7, 24)] = mapOf(
            MealSlot.DINNER to listOf(LogEntry("d1", "Salmon fillet", "180 g", 610, 45, status = LogStatus.PLANNED)),
        )

        setContent {
            MaterialTheme {
                MealPlanRoute(onAddToMeal = { _, _ -> }, onOpenTimes = {}, viewModel = viewModel())
            }
        }

        awaitNode(hasTestTag("plan_days"))
        // Nine chips: yesterday, today, and the next seven.
        (0..8).forEach { onNodeWithTag("plan_day_$it", useUnmergedTree = true).assertExists() }
        onNodeWithTag("plan_day_9").assertDoesNotExist()

        // Index 3 is 2026-07-24 (yesterday, today, +1, +2).
        onNodeWithTag("plan_day_3").performScrollTo().performClick()
        awaitNode(hasTestTag("plan_slot_dinner"))
        onAllNodesWithText("Salmon fillet").assertCountEquals(1)
    }

    // SPEC: PLAN-23
    @Test
    fun `a future day shows its containers and makes no punctuality claims`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MealPlanRoute(
                    onAddToMeal = { _, _ -> },
                    onOpenTimes = {},
                    // PLAN-24: the day arrives as the ViewModel's seed, not as a route argument.
                    viewModel = viewModel(on = LocalDate(2026, 7, 25)),
                )
            }
        }

        awaitNode(hasTestTag("meal_plan_screen"))
        onNodeWithTag("plan_slot_lunch", useUnmergedTree = true).assertExists()
        // Nothing on a day being planned ahead is next, late, or missed — the clock says
        // 12:45, but 12:45 on a Wednesday tells you nothing about Saturday.
        onAllNodesWithText("NEXT").assertCountEquals(0)
        onAllNodesWithText("LATE").assertCountEquals(0)
        onAllNodesWithText("MISSED").assertCountEquals(0)
    }

    // SPEC: PLAN-20
    @Test
    fun `copy-forward is offered and leaves the source day on screen`() = runComposeUiTest {
        repository.entries[today] = mapOf(
            MealSlot.BREAKFAST to listOf(LogEntry("b1", "Rolled oats", "80 g", 430, 38, status = LogStatus.PLANNED)),
        )

        setContent {
            MaterialTheme {
                MealPlanRoute(onAddToMeal = { _, _ -> }, onOpenTimes = {}, viewModel = viewModel())
            }
        }

        awaitNode(hasTestTag("plan_copy_forward"))
        onNodeWithTag("plan_copy_forward").performScrollTo().performClick()
        waitUntil { repository.copyCalls.isNotEmpty() }
        waitForIdle()
        // The view does not move — the source day stays on screen, unchanged, which is the
        // confirmation that nothing was taken away from it.
        onAllNodesWithText("Rolled oats").assertCountEquals(1)
        assertEquals(today, repository.copyCalls.single().from)
        assertEquals(6, repository.copyCalls.single().to.size, "the rest of the week")
    }

    // SPEC: PLAN-24
    @Test
    fun `the selected day survives a trip to the tray and back`() = runComposeUiTest {
        // The nav-entry-scoped ViewModel outlives the tray, so the same instance comes back —
        // and `visible` toggling is exactly what happens to this composable when the tray covers
        // it and is then dismissed: it leaves composition and re-enters.
        val vm = viewModel()
        var visible by mutableStateOf(true)
        setContent { MaterialTheme { if (visible) MealPlanRoute({ _, _ -> }, {}, vm) } }

        awaitNode(hasTestTag("plan_days"))
        // Thursday: index 3 of [yesterday, today, +1 … +7], two days out.
        val thursday = today.plus(2, DateTimeUnit.DAY)
        vm.select(thursday)
        waitUntil { vm.selectedDate.value == thursday }
        waitForIdle()

        // Off to the tray, and back.
        visible = false
        waitForIdle()
        visible = true
        awaitNode(hasTestTag("plan_days"))

        assertEquals(
            thursday,
            vm.selectedDate.value,
            "re-entering must not re-apply the route's opening date — that is what made " +
                "planning a day ahead mean re-picking it after every single food",
        )
    }
}
