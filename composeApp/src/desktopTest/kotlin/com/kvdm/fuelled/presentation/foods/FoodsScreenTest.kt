package com.kvdm.fuelled.presentation.foods

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.usecase.GetFoodsUseCase
import com.kvdm.fuelled.domain.usecase.SearchFoodsUseCase
import com.kvdm.fuelled.testing.awaitNode
import com.kvdm.fuelled.testing.fakes.FakeFoodRepository
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Durable screen tests — first-party Compose UI Test, spec-cited, testTag selectors.
 * Each test verifies a clause from `specs/foods.spec.md`, one behavior per test, against the
 * VM-backed [FoodsRoute] wired to a hand-written fake repository.
 */
@OptIn(ExperimentalTestApi::class)
class FoodsScreenTest {

    private val repository = FakeFoodRepository()

    private fun viewModel() =
        FoodsViewModel(GetFoodsUseCase(repository), SearchFoodsUseCase(repository))

    private val chicken = Food("1", "Chicken breast", "Raw", "100 g", 165, 31, 0, 4)
    private val oats = Food("2", "Rolled oats", "Quaker", "80 g", 303, 11, 54, 6)

    // SPEC: FOODS-01
    @Test
    fun `renders the loaded catalog with name and brand`() = runComposeUiTest {
        repository.foods = listOf(chicken, oats)

        setContent {
            MaterialTheme { FoodsRoute(onFoodClick = {}, viewModel = viewModel()) }
        }

        awaitNode(hasText("Chicken breast"))
        onAllNodesWithText("Rolled oats").assertCountEquals(1)
        onNodeWithTag("foods_title", useUnmergedTree = true).assertExists()
    }

    // SPEC: FOODS-02
    @Test
    fun `typing a query filters the list through the ViewModel`() = runComposeUiTest {
        repository.foods = listOf(chicken, oats)

        setContent {
            MaterialTheme { FoodsRoute(onFoodClick = {}, viewModel = viewModel()) }
        }

        awaitNode(hasText("Chicken breast"))
        onNodeWithTag("foods_search").performTextInput("oat")

        awaitNode(hasText("Rolled oats"))
        onAllNodesWithText("Chicken breast").assertCountEquals(0)
        assertEquals("oat", repository.lastQuery, "the filter ran at the repository, not the screen")
    }

    // SPEC: FOODS-03
    @Test
    fun `shows the empty state when a search matches nothing`() = runComposeUiTest {
        repository.foods = listOf(chicken, oats)

        setContent {
            MaterialTheme { FoodsRoute(onFoodClick = {}, viewModel = viewModel()) }
        }

        awaitNode(hasText("Chicken breast"))
        onNodeWithTag("foods_search").performTextInput("zzz")
        awaitNode(hasTestTag("foods_empty"))
    }

    // SPEC: FOODS-04
    @Test
    fun `shows presentation-mapped error copy and retry when loading fails`() = runComposeUiTest {
        repository.failure = DomainError.Network

        setContent {
            MaterialTheme { FoodsRoute(onFoodClick = {}, viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("foods_error"))
        onAllNodesWithText(DomainError.Network.toUserMessage()).assertCountEquals(1)
        onNodeWithTag("foods_retry", useUnmergedTree = true).assertExists()
    }

    // SPEC: FOODS-04
    @Test
    fun `tapping retry after a failure reloads and shows recovered foods`() = runComposeUiTest {
        repository.failure = DomainError.Network
        val vm = viewModel()

        setContent {
            MaterialTheme { FoodsRoute(onFoodClick = {}, viewModel = vm) }
        }

        awaitNode(hasTestTag("foods_error"))

        repository.failure = null
        repository.foods = listOf(chicken)
        onNodeWithTag("foods_retry").performClick()

        awaitNode(hasText("Chicken breast"))
    }

    // SPEC: FOODS-05
    @Test
    fun `tapping a food reports its id for navigation`() = runComposeUiTest {
        repository.foods = listOf(chicken)
        var clickedId: String? = null

        setContent {
            MaterialTheme { FoodsRoute(onFoodClick = { clickedId = it.id }, viewModel = viewModel()) }
        }

        awaitNode(hasText("Chicken breast"))
        onAllNodesWithText("Chicken breast").onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { clickedId != null }
        assertEquals("1", clickedId)
    }
}
