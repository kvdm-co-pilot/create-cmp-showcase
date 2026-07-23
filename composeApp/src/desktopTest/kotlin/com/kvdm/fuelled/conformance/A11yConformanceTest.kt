package com.kvdm.fuelled.conformance

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.domain.model.Item
import com.kvdm.fuelled.domain.usecase.GetItemsUseCase
import com.kvdm.fuelled.presentation.home.HomeScreen
import com.kvdm.fuelled.presentation.home.HomeViewModel
import com.kvdm.fuelled.testing.awaitNode
import com.kvdm.fuelled.testing.fakes.FakeItemRepository
import kotlin.test.Test
import kotlin.test.fail

/**
 * A11y gate — SPEC: SHELL-04. Every interactive node must be perceivable by assistive
 * technology and automation: a testTag, text, or contentDescription. Runs on the JVM tier;
 * the verify lane's `a11y` step. (Deeper checks — touch-target size, contrast — come with
 * the live-tier inspector audit.)
 */
@OptIn(ExperimentalTestApi::class)
class A11yConformanceTest {

    // SPEC: SHELL-04
    @Test
    fun `every clickable on Home is perceivable`() = runComposeUiTest {
        val repository = FakeItemRepository().apply {
            items = listOf(Item(id = "1", title = "A11y row", subtitle = "sub"))
        }
        setContent {
            MaterialTheme {
                HomeScreen(onItemClick = {}, viewModel = HomeViewModel(GetItemsUseCase(repository)))
            }
        }
        awaitNode(hasText("A11y row"))

        val offenders = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            val clickable = node.config.getOrNull(SemanticsActions.OnClick) != null
            if (clickable) {
                val perceivable =
                    node.config.getOrNull(SemanticsProperties.TestTag) != null ||
                        !node.config.getOrNull(SemanticsProperties.Text).isNullOrEmpty() ||
                        !node.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() ||
                        node.children.any { !it.config.getOrNull(SemanticsProperties.Text).isNullOrEmpty() }
                if (!perceivable) offenders += "node@${node.id} (bounds=${node.boundsInRoot})"
            }
            node.children.forEach(::walk)
        }
        walk(onRoot(useUnmergedTree = true).fetchSemanticsNode())

        if (offenders.isNotEmpty()) fail(
            "[SHELL-04] Interactive nodes without testTag/text/contentDescription:\n  " +
                offenders.joinToString("\n  ") +
                "\n  Fix: give each clickable a testTag or visible text/contentDescription.",
        )
    }
}
