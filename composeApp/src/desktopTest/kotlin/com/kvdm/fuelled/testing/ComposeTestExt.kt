package com.kvdm.fuelled.testing

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher

/**
 * Waits until at least one node matches. Portable helper (the upstream
 * waitUntilAtLeastOneExists is not exposed on this CMP version's desktop test artifact).
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.awaitNode(matcher: SemanticsMatcher, timeoutMillis: Long = 5_000) {
    waitUntil(timeoutMillis = timeoutMillis) {
        onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }
}
