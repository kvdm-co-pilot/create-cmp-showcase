package com.kvdm.cmpshowcase.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.cmpshowcase.testing.awaitNode
import kotlin.test.Test

/**
 * Durable shell tests — first-party Compose UI Test, spec-cited, testTag selectors.
 * Verifies `specs/app-base.spec.md`'s SHELL-01/SHELL-02 clauses directly against [AppShell]
 * with trivial in-test tabs (no ViewModel/DI wiring — the shell itself is under test), so no
 * scaffold flag combination (in particular `--no-e2e`, which removes qa/e2e) orphans these
 * clauses. `qa/e2e/smoke.yaml` remains the on-device citation of the same clauses — being
 * cited by both a durable JVM test and the E2E flow is expected, not redundant.
 */
@OptIn(ExperimentalTestApi::class)
class AppShellTest {

    private fun testTabs(): List<AppTab> = listOf(
        AppTab(
            label = "Tab One",
            icon = Icons.Filled.Home,
            content = {
                Text("Tab one content", modifier = Modifier.semantics { testTag = "tab_one_content" })
            },
        ),
        AppTab(
            label = "Tab Two",
            icon = Icons.Filled.Home,
            content = {
                Text("Tab two content", modifier = Modifier.semantics { testTag = "tab_two_content" })
            },
        ),
    )

    // SPEC: SHELL-01
    @Test
    fun `first tab renders inside the shell with the bottom nav visible on launch`() = runComposeUiTest {
        setContent {
            MaterialTheme { AppShell(tabs = testTabs()) }
        }

        awaitNode(hasTestTag("tab_one_content"))
        onNodeWithTag("tab_one_content").assertIsDisplayed()
        onNodeWithTag("app_bottom_nav").assertIsDisplayed()
    }

    // SPEC: SHELL-02
    @Test
    fun `tapping another tab renders its screen and keeps the bottom nav visible`() = runComposeUiTest {
        setContent {
            MaterialTheme { AppShell(tabs = testTabs()) }
        }

        awaitNode(hasTestTag("tab_one_content"))

        onNodeWithText("Tab Two").performClick()
        awaitNode(hasTestTag("tab_two_content"))
        onNodeWithTag("tab_two_content").assertIsDisplayed()
        onNodeWithTag("app_bottom_nav").assertIsDisplayed()

        onNodeWithText("Tab One").performClick()
        awaitNode(hasTestTag("tab_one_content"))
        onNodeWithTag("tab_one_content").assertIsDisplayed()
        onNodeWithTag("app_bottom_nav").assertIsDisplayed()
    }
}
