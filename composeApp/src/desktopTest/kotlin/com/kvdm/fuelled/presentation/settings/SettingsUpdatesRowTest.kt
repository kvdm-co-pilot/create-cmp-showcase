package com.kvdm.fuelled.presentation.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.testing.awaitNode
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The update surface's entry point in Settings — SPEC: SET-09, and the presentation half of
 * UPD-08.
 *
 * Both directions matter and only one of them is obvious. That the row APPEARS where installing
 * is possible is the feature working; that it is ABSENT where installing is impossible is the
 * clause's actual claim, and the failure mode it guards is the easy one to ship — a row that
 * renders everywhere and dead-ends on iOS.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsUpdatesRowTest {

    // SPEC: SET-09
    @Test
    fun `settings offers the update entry point where the platform can install`() =
        runComposeUiTest {
            var opened = false
            setContent {
                MaterialTheme {
                    SettingsScreen(
                        ui = SettingsUi(updatesSupported = true),
                        onOpenUpdates = { opened = true },
                    )
                }
            }

            awaitNode(hasTestTag("settings_screen"))
            // Settings is long; the row sits below the fold. Scroll as a user does — a tap on a
            // centre below the viewport lands somewhere else entirely.
            onNodeWithTag("settings_updates").performScrollTo().assertIsDisplayed()
            onNodeWithTag("settings_updates").performClick()
            assertTrue(opened, "the row opens the update surface")
        }

    // SPEC: SET-09
    @Test
    fun `settings has no update row at all where the platform cannot install`() =
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    SettingsScreen(ui = SettingsUi(updatesSupported = false))
                }
            }

            awaitNode(hasTestTag("settings_screen"))
            // assertDoesNotExist, not assertIsNotDisplayed: UPD-08 says the row is ABSENT.
            // A present-but-hidden row would satisfy "not displayed" and still be reachable by
            // a screen reader, which is the accessible version of the bug.
            onNodeWithTag("settings_updates").assertDoesNotExist()
        }
}
