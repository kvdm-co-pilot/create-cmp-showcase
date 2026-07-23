package com.kvdm.fuelled.conformance

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.usecase.GetFoodsUseCase
import com.kvdm.fuelled.domain.usecase.SearchFoodsUseCase
import com.kvdm.fuelled.presentation.foods.FoodsRoute
import com.kvdm.fuelled.presentation.foods.FoodsViewModel
import com.kvdm.fuelled.testing.awaitNode
import com.kvdm.fuelled.testing.fakes.FakeFoodRepository
import kotlin.test.Test

/**
 * The COMP clauses (`specs/app-base.spec.md`) as executable checks — the component
 * vocabulary's runtime contract, proven against the exemplar screen the same way the
 * ARCH/SHELL conformance gates prove the architecture. Component substructure/tag
 * stability is additionally pinned by the golden-tree baseline (`qa/golden/`); this class
 * proves the STATE/A11Y contract every registry consumer inherits for free.
 */
@OptIn(ExperimentalTestApi::class)
class ComponentConformanceTest {

    private fun viewModel(repository: FakeFoodRepository) =
        FoodsViewModel(GetFoodsUseCase(repository), SearchFoodsUseCase(repository))

    // SPEC: COMP-01
    @Test
    fun `a failed load is presented by ContentStateContainer with the screen-derived error tag`() = runComposeUiTest {
        val repository = FakeFoodRepository().apply { failure = DomainError.Network }
        setContent {
            MaterialTheme {
                FoodsRoute(onFoodClick = {}, viewModel = viewModel(repository))
            }
        }
        awaitNode(hasTestTag("foods_error"))
    }

    // SPEC: COMP-01
    @Test
    fun `a zero-result search is presented by ContentStateContainer with the screen-derived empty tag`() = runComposeUiTest {
        val repository = FakeFoodRepository().apply {
            foods = listOf(Food("1", "Chicken breast", "Raw", "100 g", 165, 31, 0, 4))
        }
        setContent {
            MaterialTheme {
                FoodsRoute(onFoodClick = {}, viewModel = viewModel(repository))
            }
        }
        awaitNode(hasText("Chicken breast"))
        onNodeWithTag("foods_search").performTextInput("zzz")
        awaitNode(hasTestTag("foods_empty"))
    }

    // SPEC: COMP-02
    @Test
    fun `a recoverable error renders a retry control of at least 48dp`() = runComposeUiTest {
        val repository = FakeFoodRepository().apply { failure = DomainError.Network }
        setContent {
            MaterialTheme {
                FoodsRoute(onFoodClick = {}, viewModel = viewModel(repository))
            }
        }
        awaitNode(hasTestTag("foods_retry"))
        onNodeWithTag("foods_retry")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    // SPEC: COMP-03
    @Test
    fun `every food row clears the 48dp minimum pointer target`() = runComposeUiTest {
        val repository = FakeFoodRepository().apply {
            foods = listOf(Food("1", "Chicken breast", "Raw", "100 g", 165, 31, 0, 4))
        }
        setContent {
            MaterialTheme {
                FoodsRoute(onFoodClick = {}, viewModel = viewModel(repository))
            }
        }
        awaitNode(hasText("Chicken breast"))
        onNodeWithTag("foods_item_1").assertHeightIsAtLeast(48.dp)
    }
}
