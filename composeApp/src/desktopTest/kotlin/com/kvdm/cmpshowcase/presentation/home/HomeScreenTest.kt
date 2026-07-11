package com.kvdm.cmpshowcase.presentation.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.cmpshowcase.domain.model.Item
import com.kvdm.cmpshowcase.domain.usecase.GetItemsUseCase
import com.kvdm.cmpshowcase.testing.awaitNode
import com.kvdm.cmpshowcase.testing.fakes.FakeItemRepository
import kotlin.test.Test

/**
 * Durable screen tests — first-party Compose UI Test, spec-cited, testTag selectors.
 * These are the long-lived regression layer (TESTING-ARCHITECTURE tier 3b): each test
 * verifies a clause from `specs/home.spec.md`, one behavior per test.
 */
@OptIn(ExperimentalTestApi::class)
class HomeScreenTest {

    private val repository = FakeItemRepository()

    private fun viewModel() = HomeViewModel(GetItemsUseCase(repository))

    // SPEC: HOME-02
    @Test
    fun `renders loaded items with title and subtitle`() = runComposeUiTest {
        repository.items = listOf(
            Item(id = "1", title = "First title", subtitle = "First subtitle"),
            Item(id = "2", title = "Second title", subtitle = "Second subtitle"),
        )

        setContent {
            MaterialTheme { HomeScreen(onItemClick = {}, viewModel = viewModel()) }
        }

        awaitNode(hasText("First title"))
        onAllNodesWithText("Second subtitle").assertCountEquals(1)
        onNodeWithTag("home_title", useUnmergedTree = true).assertExists()
    }

    // SPEC: HOME-03
    @Test
    fun `shows the error message when loading fails`() = runComposeUiTest {
        repository.shouldFail = true
        repository.failureMessage = "network down"

        setContent {
            MaterialTheme { HomeScreen(onItemClick = {}, viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("home_error"))
        onAllNodesWithText("network down").assertCountEquals(1)
    }

    // SPEC: HOME-05
    @Test
    fun `tapping an item reports its id for navigation`() = runComposeUiTest {
        repository.items = listOf(Item(id = "item-42", title = "Tap me", subtitle = "sub"))
        var clickedId: String? = null

        setContent {
            MaterialTheme { HomeScreen(onItemClick = { clickedId = it }, viewModel = viewModel()) }
        }

        awaitNode(hasText("Tap me"))
        onAllNodesWithText("Tap me").onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { clickedId != null }
        kotlin.test.assertEquals("item-42", clickedId)
    }
}
