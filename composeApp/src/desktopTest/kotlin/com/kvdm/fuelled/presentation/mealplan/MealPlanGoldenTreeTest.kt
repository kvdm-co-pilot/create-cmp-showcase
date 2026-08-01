package com.kvdm.fuelled.presentation.mealplan

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.domain.model.LogEntry
import com.kvdm.fuelled.domain.model.LogStatus
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.usecase.ArmMealRemindersUseCase
import com.kvdm.fuelled.domain.usecase.CopyDayForwardUseCase
import com.kvdm.fuelled.domain.usecase.DeleteLogEntryUseCase
import com.kvdm.fuelled.domain.usecase.RestoreLogEntryUseCase
import com.kvdm.fuelled.domain.usecase.SetEntryServingsUseCase
import com.kvdm.fuelled.domain.usecase.GetPlanDayUseCase
import com.kvdm.fuelled.domain.usecase.SetSlotDoneUseCase
import com.kvdm.fuelled.domain.usecase.SetWaterDoneUseCase
import com.kvdm.fuelled.testing.StructuralTree
import com.kvdm.fuelled.testing.TEST_NOW
import com.kvdm.fuelled.testing.TEST_ZONE
import com.kvdm.fuelled.testing.awaitNode
import com.kvdm.fuelled.testing.fakes.FakeAppStateRepository
import com.kvdm.fuelled.testing.fakes.FakeMealPlanRepository
import com.kvdm.fuelled.testing.fakes.FakeReminderScheduler
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import com.kvdm.fuelled.testing.fakes.FixedClock
import java.io.File
import kotlin.test.Test
import kotlin.test.fail
import kotlinx.datetime.LocalDate
import com.kvdm.fuelled.testing.fakes.FakeTimeSignal

/**
 * Golden-tree structural baseline for the structured day — SPEC: PLAN-18.
 *
 * Renders the plan headlessly through the REAL path (MealPlanRoute + MealPlanViewModel) over a
 * fake with FIXED data and a FIXED clock, and diffs the semantics structure against the
 * committed baseline (`qa/golden/meal-plan.json`). No pixels, no flake: a failure means the
 * screen's STRUCTURE changed.
 *
 * The clock matters more here than on any other screen: focus, lateness and missed-ness are all
 * functions of now, so an unfixed clock would rewrite this baseline every few hours. 12:45 is
 * chosen because it is the moment the day is most legible — a missed breakfast behind, lunch
 * focused and late, the afternoon still ahead.
 *
 * Unintended drift → fix your change. Intended drift → regenerate explicitly and declare it:
 *
 *   UPDATE_GOLDEN=1 ./gradlew :composeApp:desktopTest --tests "*GoldenTree*"
 */
@OptIn(ExperimentalTestApi::class)
class MealPlanGoldenTreeTest {

    private val baseline = File("../qa/golden/meal-plan.json")

    private val goldenDay = LocalDate(2026, 7, 22)

    // SPEC: PLAN-18
    @Test
    fun `meal plan structure matches the committed golden tree`() = runComposeUiTest {
        val repository = FakeMealPlanRepository(FakeTimeSignal(TEST_NOW), TEST_ZONE).apply {
            entries[goldenDay] = mapOf(
                // Planned, never ticked, and the morning has gone: breakfast reads MISSED.
                MealSlot.BREAKFAST to listOf(
                    LogEntry("g1", "Golden oats", "80 g", 430, 38, status = LogStatus.PLANNED),
                ),
                MealSlot.LUNCH to listOf(
                    LogEntry("g2", "Golden chicken", "200 g", 620, 58),
                    LogEntry("g3", "Golden greens", "1 bowl", 90, 3, veg = true),
                ),
            )
            // Ticked with nothing in it — the eaten-off-plan state (PLAN-14).
            doneSlots[goldenDay] = mutableSetOf(MealSlot.MORNING_SNACK)
            waterTicks[goldenDay] = mutableSetOf(1, 2)
        }
        val viewModel = MealPlanViewModel(
            initialDate = goldenDay,
            getPlanDay = GetPlanDayUseCase(repository, time = FakeTimeSignal(TEST_NOW), zone = TEST_ZONE),
            setSlotDone = SetSlotDoneUseCase(repository),
            setWaterDone = SetWaterDoneUseCase(repository),
            copyDayForward = CopyDayForwardUseCase(repository),
            armReminders = ArmMealRemindersUseCase(repository, FakeReminderScheduler(), FakeAppStateRepository()),
            deleteLogEntry = DeleteLogEntryUseCase(FakeTodayRepository()),
            setEntryServings = SetEntryServingsUseCase(FakeTodayRepository()),
            restoreLogEntry = RestoreLogEntryUseCase(FakeTodayRepository()),
        )

        setContent {
            MaterialTheme {
                MealPlanRoute(
                    onAddToMeal = { _, _ -> },
                    onOpenTimes = {},
                    viewModel = viewModel,
                )
            }
        }
        awaitNode(hasTestTag("plan_slot_lunch"))

        val rendered = StructuralTree.serialize(onRoot(useUnmergedTree = true).fetchSemanticsNode())

        if (System.getenv("UPDATE_GOLDEN") == "1") {
            baseline.parentFile.mkdirs()
            baseline.writeText(rendered)
            return@runComposeUiTest
        }

        if (!baseline.exists()) {
            fail(
                "No golden baseline at ${baseline.path}. Generate it with:\n" +
                    "  UPDATE_GOLDEN=1 ./gradlew :composeApp:desktopTest --tests \"*GoldenTree*\"",
            )
        }

        val expected = baseline.readText().trim()
        if (expected != rendered.trim()) {
            fail(
                "Meal plan structure drifted from ${baseline.path} (PLAN-18).\n" +
                    "If the change is intentional, regenerate and commit the baseline:\n" +
                    "  UPDATE_GOLDEN=1 ./gradlew :composeApp:desktopTest --tests \"*GoldenTree*\"\n\n" +
                    "--- expected ---\n$expected\n\n--- actual ---\n${rendered.trim()}",
            )
        }
    }
}
