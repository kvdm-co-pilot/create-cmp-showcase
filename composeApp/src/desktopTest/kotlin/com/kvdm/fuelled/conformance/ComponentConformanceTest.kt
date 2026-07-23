package com.kvdm.fuelled.conformance

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Item
import com.kvdm.fuelled.domain.usecase.GetItemsUseCase
import com.kvdm.fuelled.presentation.home.HomeScreen
import com.kvdm.fuelled.presentation.home.HomeViewModel
import com.kvdm.fuelled.testing.awaitNode
import com.kvdm.fuelled.testing.fakes.FakeItemRepository
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

    // SPEC: COMP-01
    @Test
    fun `a failed load is presented by ContentStateContainer with the screen-derived error tag`() = runComposeUiTest {
        val repository = FakeItemRepository().apply { failure = DomainError.Network }
        setContent {
            MaterialTheme {
                HomeScreen(onItemClick = {}, viewModel = HomeViewModel(GetItemsUseCase(repository)))
            }
        }
        awaitNode(hasTestTag("home_error"))
    }

    // SPEC: COMP-01
    @Test
    fun `a zero-item load is presented by ContentStateContainer with the screen-derived empty tag`() = runComposeUiTest {
        val repository = FakeItemRepository().apply { items = emptyList() }
        setContent {
            MaterialTheme {
                HomeScreen(onItemClick = {}, viewModel = HomeViewModel(GetItemsUseCase(repository)))
            }
        }
        awaitNode(hasTestTag("home_empty"))
    }

    // SPEC: COMP-02
    @Test
    fun `a recoverable error renders a retry control of at least 48dp`() = runComposeUiTest {
        val repository = FakeItemRepository().apply { failure = DomainError.Network }
        setContent {
            MaterialTheme {
                HomeScreen(onItemClick = {}, viewModel = HomeViewModel(GetItemsUseCase(repository)))
            }
        }
        awaitNode(hasTestTag("home_retry"))
        onNodeWithTag("home_retry")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    // SPEC: COMP-03
    @Test
    fun `every ListItemCard row clears the 48dp minimum pointer target`() = runComposeUiTest {
        val repository = FakeItemRepository().apply {
            items = listOf(Item(id = "1", title = "Row", subtitle = "sub"))
        }
        setContent {
            MaterialTheme {
                HomeScreen(onItemClick = {}, viewModel = HomeViewModel(GetItemsUseCase(repository)))
            }
        }
        awaitNode(hasText("Row"))
        onNodeWithTag("home_item_1").assertHeightIsAtLeast(48.dp)
    }
}
