package com.kvdm.fuelled.presentation.foods

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.usecase.GetFoodsUseCase
import com.kvdm.fuelled.domain.usecase.SearchFoodsUseCase
import com.kvdm.fuelled.testing.awaitNode
import com.kvdm.fuelled.testing.fakes.FakeFoodRepository
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Meals tab's chrome after the motion brief's IA pass (D13, D15): the header says the
 * tab's own name, and it carries the door to the meal builder. Durable screen tests —
 * spec-cited, testTag selectors — against the VM-backed [FoodsRoute] over the hand-written
 * fake repository, the same wiring [com.kvdm.fuelled.conformance.ComponentConformanceTest] uses.
 */
@OptIn(ExperimentalTestApi::class)
class MealsBuildDoorTest {

    private fun viewModel(repository: FakeFoodRepository) =
        FoodsViewModel(GetFoodsUseCase(repository), SearchFoodsUseCase(repository))

    private fun repository() = FakeFoodRepository().apply {
        foods = listOf(Food("1", "Chicken breast", "Raw", "100 g", 165, 31, 0, 4))
    }

    // SPEC: CAT-04
    @Test
    fun `the header's Build a meal action opens the builder`() = runComposeUiTest {
        var opened = false
        setContent {
            MaterialTheme {
                FoodsRoute(onFoodClick = {}, onBuildMeal = { opened = true }, viewModel = viewModel(repository()))
            }
        }

        awaitNode(hasTestTag("foods_build"))
        onNodeWithTag("foods_build").performClick()

        assertTrue(opened, "foods_build opens the meal builder")
    }

    // SPEC: CAT-01
    @Test
    fun `the header reads Meals - the tab's own name`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                FoodsRoute(onFoodClick = {}, viewModel = viewModel(repository()))
            }
        }

        awaitNode(hasTestTag("foods_title"))
        onNodeWithTag("foods_title").assert(hasText("Meals"))
    }
}
