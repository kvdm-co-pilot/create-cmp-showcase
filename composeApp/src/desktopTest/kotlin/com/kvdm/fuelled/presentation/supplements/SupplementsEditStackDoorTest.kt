package com.kvdm.fuelled.presentation.supplements

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.testing.awaitNode
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The "Edit stack" door (motion D16): the editor stays a Settings card; the screen that shows
 * today's doses gains a second way in. The header is the registry's, so the title keeps its
 * `supplements_title` tag.
 */
@OptIn(ExperimentalTestApi::class)
class SupplementsEditStackDoorTest {

    // SPEC: SUPP-14
    @Test
    fun `the Edit stack action in the header opens the stack editor and the title keeps its tag`() = runComposeUiTest {
        var opened = false
        setContent {
            MaterialTheme {
                SupplementsScreen(onEditStack = { opened = true })
            }
        }
        awaitNode(hasTestTag("supplements_edit_stack"))
        onNodeWithTag("supplements_title").assertExists()

        onNodeWithTag("supplements_edit_stack").performClick()

        assertTrue(opened, "tapping supplements_edit_stack invokes onEditStack")
    }
}
