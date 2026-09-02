package com.kvdm.fuelled.presentation.mealplan

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
 * The "Review" door (motion D17): the week's verdict, one tap from the week you are planning.
 * Rendered through the stateless screen with inert actions plus the one under test — the
 * door is a header action, so no ViewModel is needed to prove it opens.
 */
@OptIn(ExperimentalTestApi::class)
class PlanReviewDoorTest {

    // SPEC: PLAN-19
    @Test
    fun `the Review action in the plan header opens the retrospective`() = runComposeUiTest {
        var opened = false
        setContent {
            MaterialTheme {
                MealPlanDayScreen(actions = PlanDayActions.None.copy(onOpenReview = { opened = true }))
            }
        }
        awaitNode(hasTestTag("plan_review"))

        onNodeWithTag("plan_review").performClick()

        assertTrue(opened, "tapping plan_review invokes onOpenReview")
    }
}
