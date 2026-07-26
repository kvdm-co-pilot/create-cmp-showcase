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
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

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
    private val foodRepository = FakeFoodRepository()
    private val todayRepository = FakeTodayRepository()

    private val chicken = Food("1", "Chicken breast", "Raw · skinless", "100 g", 165, 31, 0, 4)

    private fun viewModel(): MealTrayViewModel {
        foodRepository.foods = listOf(chicken)
        val clock = FixedClock(openedAt.toInstant(zone))
        return MealTrayViewModel(
            getFoods = GetFoodsUseCase(foodRepository),
            searchFoods = SearchFoodsUseCase(foodRepository),
            addLogEntries = AddLogEntriesUseCase(todayRepository, clock, zone, DEFAULT_DAY_START_HOUR),
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
    fun `the header states the target date and slot, and a tap retargets it`() = runComposeUiTest {
        setContent { MaterialTheme { MealTrayRoute(viewModel = viewModel()) } }
        awaitNode(hasTestTag("meal_tray_target"))

        onNodeWithTag("meal_tray_target", useUnmergedTree = true)
            .assertTextEquals("Lunch · Wednesday, Jul 22")

        onNodeWithTag("meal_tray_slot_dinner").performClick()
        onNodeWithTag("meal_tray_date_tomorrow").performClick()

        onNodeWithTag("meal_tray_target", useUnmergedTree = true)
            .assertTextEquals("Dinner · Thursday, Jul 23")
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

    // SPEC: MEAL-10
    @Test
    fun `confirming a retargeted tray writes to the retargeted date and slot`() = runComposeUiTest {
        setContent { MaterialTheme { MealTrayRoute(viewModel = viewModel()) } }
        awaitNode(hasTestTag("meal_tray_item_1"))

        onNodeWithTag("meal_tray_item_1").performClick()
        onNodeWithTag("meal_tray_slot_dinner").performClick()
        onNodeWithTag("meal_tray_date_tomorrow").performClick()
        onNodeWithTag("meal_tray_add").performClick()
        waitUntil(timeoutMillis = 5_000) { todayRepository.addCalls.isNotEmpty() }

        val call = todayRepository.addCalls.single()
        assertTrue(call.slot == MealSlot.DINNER, "the write followed the header's slot")
        assertTrue(call.date.toString() == "2026-07-23", "the write followed the header's date")
    }
}
