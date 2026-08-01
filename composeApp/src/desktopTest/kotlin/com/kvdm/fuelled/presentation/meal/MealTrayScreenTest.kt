package com.kvdm.fuelled.presentation.meal

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.usecase.AddLogEntriesUseCase
import com.kvdm.fuelled.domain.usecase.GetFoodsUseCase
import com.kvdm.fuelled.domain.usecase.SearchFoodsUseCase
import com.kvdm.fuelled.testing.awaitNode
import com.kvdm.fuelled.testing.fakes.FakeFoodRepository
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import com.kvdm.fuelled.testing.fakes.FixedClock
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import com.kvdm.fuelled.testing.fakes.FakeTimeSignal

/**
 * Durable screen tests for the add-to-meal tray — first-party Compose UI Test, spec-cited,
 * testTag selectors, against the VM-backed [MealTrayRoute] wired to hand-written fakes.
 *
 * These assert what the SCREEN does with the ViewModel's state; the ViewModel's own refusals
 * and arithmetic are proven in `MealTrayViewModelTest`.
 */
@OptIn(ExperimentalTestApi::class)
class MealTrayScreenTest {

    private val zone = TimeZone.UTC
    private val openedAt = LocalDateTime(2026, 7, 22, 12, 30)
    // The header's opening state — "Lunch · Wednesday, Jul 22" — is now the caller's explicit
    // target (MEAL-10, PLAN-04), not a clock guess, though it matches `openedAt`'s logical day.
    private val targetDate = LocalDate(2026, 7, 22)
    private val foodRepository = FakeFoodRepository()
    private val todayRepository = FakeTodayRepository()

    private val chicken = Food("1", "Chicken breast", "Raw · skinless", "100 g", 165, 31, 0, 4)

    private fun viewModel(
        target: MealTrayInitialTarget = MealTrayInitialTarget(date = targetDate, slot = MealSlot.LUNCH),
    ): MealTrayViewModel {
        foodRepository.foods = listOf(chicken)
        val clock = FixedClock(openedAt.toInstant(zone))
        return MealTrayViewModel(
            getFoods = GetFoodsUseCase(foodRepository),
            searchFoods = SearchFoodsUseCase(foodRepository),
            addLogEntries = AddLogEntriesUseCase(todayRepository, clock, zone, DEFAULT_DAY_START_HOUR),
            initialTarget = target,
            clock = clock,
            zone = zone,
            dayStartHour = DEFAULT_DAY_START_HOUR,
        )
    }

    // SPEC: MEAL-09
    @Test
    fun `the total bar shows calories and all three macros, recomputed as an item goes in`() =
        runComposeUiTest {
            setContent { MaterialTheme { MealTrayRoute(viewModel = viewModel()) } }
            awaitNode(hasTestTag("meal_tray_item_1"))

            onNodeWithTag("meal_tray_total", useUnmergedTree = true)
                .assertTextEquals("0 items · 0 kcal")

            onNodeWithTag("meal_tray_item_1").performClick()

            awaitNode(hasTestTag("meal_tray_total"))
            // Singular at one item — the count's copy agrees with the count.
            onNodeWithTag("meal_tray_total", useUnmergedTree = true)
                .assertTextEquals("1 item · 165 kcal")
            onNodeWithTag("meal_tray_macros", useUnmergedTree = true).assertIsDisplayed()
        }

    // SPEC: MEAL-10
    @Test
    fun `the header STATES the target it was aimed at, and offers no way to change it`() = runComposeUiTest {
        setContent { MaterialTheme { MealTrayRoute(viewModel = viewModel()) } }
        awaitNode(hasTestTag("meal_tray_target"))

        onNodeWithTag("meal_tray_target", useUnmergedTree = true)
            .assertTextEquals("Lunch · Wednesday, Jul 22")

        // The slot pills and the date row are GONE: retargeting mid-tray is how food lands in
        // the wrong meal, and four generic pills could not even say WHICH snack. To aim
        // somewhere else you go back and tap that container (PLAN-04).
        onNodeWithTag("meal_tray_slot_dinner").assertDoesNotExist()
        onNodeWithTag("meal_tray_date_tomorrow").assertDoesNotExist()
    }

    // SPEC: MEAL-11
    @Test
    fun `the confirm control is disabled on an empty tray and enabled once an item is in`() =
        runComposeUiTest {
            setContent { MaterialTheme { MealTrayRoute(viewModel = viewModel()) } }
            awaitNode(hasTestTag("meal_tray_item_1"))

            onNodeWithTag("meal_tray_add").assertIsNotEnabled()

            onNodeWithTag("meal_tray_item_1").performClick()
            awaitNode(hasTestTag("meal_tray_add"))
            onNodeWithTag("meal_tray_add").assertIsEnabled()
        }

    // SPEC: MEAL-11
    @Test
    fun `clicking the disabled confirm on an empty tray reaches no write`() = runComposeUiTest {
        setContent { MaterialTheme { MealTrayRoute(viewModel = viewModel()) } }
        awaitNode(hasTestTag("meal_tray_add"))

        // performClick on a disabled Button dispatches the gesture; the button swallows it.
        // Asserting at the REPOSITORY (not on the button's enabled flag) is what proves the
        // clause: nothing downstream was asked to write.
        onNodeWithTag("meal_tray_add").performClick()
        waitForIdle()

        assertTrue(todayRepository.addCalls.isEmpty(), "an empty tray must attempt no write")
    }

    // SPEC: UX-01
    @Test
    fun `a checked row grows the serving stepper and the total follows it`() = runComposeUiTest {
        setContent { MaterialTheme { MealTrayRoute(viewModel = viewModel()) } }
        awaitNode(hasTestTag("meal_tray_item_1"))

        // Unchecked: selection is the row's only job — no stepper competing for the tap.
        onNodeWithTag("meal_tray_plus_1").assertDoesNotExist()

        onNodeWithTag("meal_tray_item_1").performClick()
        // The stepper's buttons are their own semantics nodes (clickable); the servings text
        // merges into the card, so it is awaited via the button and read unmerged.
        awaitNode(hasTestTag("meal_tray_plus_1"))
        onNodeWithTag("meal_tray_servings_1", useUnmergedTree = true).assertTextEquals("1×")

        onNodeWithTag("meal_tray_plus_1").performClick()
        waitForIdle()
        onNodeWithTag("meal_tray_servings_1", useUnmergedTree = true).assertTextEquals("2×")
        onNodeWithTag("meal_tray_total", useUnmergedTree = true)
            .assertTextEquals("1 item · 330 kcal")
    }

    // SPEC: UX-01
    @Test
    fun `minus at one serving takes the food back out`() = runComposeUiTest {
        setContent { MaterialTheme { MealTrayRoute(viewModel = viewModel()) } }
        awaitNode(hasTestTag("meal_tray_item_1"))

        onNodeWithTag("meal_tray_item_1").performClick()
        awaitNode(hasTestTag("meal_tray_minus_1"))
        onNodeWithTag("meal_tray_minus_1").performClick()
        waitForIdle()

        // The line is gone: stepper collapsed, total back to empty, confirm re-disabled.
        onNodeWithTag("meal_tray_servings_1").assertDoesNotExist()
        onNodeWithTag("meal_tray_total", useUnmergedTree = true)
            .assertTextEquals("0 items · 0 kcal")
        onNodeWithTag("meal_tray_add").assertIsNotEnabled()
    }

    // SPEC: MEAL-10
    @Test
    fun `confirming writes to the target the tray was AIMED at, not one chosen inside it`() = runComposeUiTest {
        // Aimed at tomorrow's Dinner by the tap that opened it — the case that used to be
        // reached by retargeting inside the tray, now carried in from the container.
        setContent {
            MaterialTheme {
                MealTrayRoute(
                    viewModel = viewModel(
                        target = MealTrayInitialTarget(date = LocalDate(2026, 7, 23), slot = MealSlot.DINNER),
                    ),
                )
            }
        }
        awaitNode(hasTestTag("meal_tray_item_1"))

        onNodeWithTag("meal_tray_target", useUnmergedTree = true)
            .assertTextEquals("Dinner · Thursday, Jul 23")
        onNodeWithTag("meal_tray_item_1").performClick()
        onNodeWithTag("meal_tray_add").performClick()
        waitUntil(timeoutMillis = 5_000) { todayRepository.addCalls.isNotEmpty() }

        val call = todayRepository.addCalls.single()
        assertTrue(call.slot == MealSlot.DINNER, "the write followed the target it was aimed at")
        assertTrue(call.date.toString() == "2026-07-23", "the write followed the target it was aimed at")
    }

    // SPEC: MEAL-13
    @Test
    fun `a confirmed add closes the tray - and only AFTER the write actually lands`() = runComposeUiTest {
        var closed = 0
        setContent { MaterialTheme { MealTrayRoute(viewModel = viewModel(), onAdded = { closed++ }) } }
        awaitNode(hasTestTag("meal_tray_item_1"))

        // Picking food does not close it. Only the confirmed WRITE does — a tray that popped on
        // selection would make a multi-item meal impossible to assemble.
        onNodeWithTag("meal_tray_item_1").performClick()
        waitForIdle()
        assertTrue(closed == 0, "selecting is not confirming")

        onNodeWithTag("meal_tray_add").performClick()
        waitUntil(timeoutMillis = 5_000) { closed > 0 }

        assertTrue(todayRepository.addCalls.isNotEmpty(), "the close follows a real write")
        assertTrue(closed == 1, "exactly once — a recomposition must not re-pop the back stack")
    }

    // SPEC: MEAL-13
    @Test
    fun `a FAILED confirm keeps the tray open - there is nothing to go back to yet`() = runComposeUiTest {
        todayRepository.failure = DomainError.Unexpected()
        var closed = 0
        setContent { MaterialTheme { MealTrayRoute(viewModel = viewModel(), onAdded = { closed++ }) } }
        awaitNode(hasTestTag("meal_tray_item_1"))

        onNodeWithTag("meal_tray_item_1").performClick()
        onNodeWithTag("meal_tray_add").performClick()
        waitUntil(timeoutMillis = 5_000) { todayRepository.addCalls.isNotEmpty() }
        waitForIdle()

        assertTrue(closed == 0, "closing on a failed write would silently discard the user's tray")
    }
}
