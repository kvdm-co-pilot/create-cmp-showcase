package com.kvdm.fuelled.presentation.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.usecase.GetProfileUseCase
import com.kvdm.fuelled.testing.awaitNode
import com.kvdm.fuelled.testing.fakes.FakeProfileRepository
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Durable screen tests — first-party Compose UI Test, spec-cited, testTag selectors. Each test
 * verifies a clause from `specs/profile.spec.md` against the VM-backed [ProfileRoute] wired to a
 * hand-written fake repository (mirrors TodayScreenTest).
 */
@OptIn(ExperimentalTestApi::class)
class ProfileScreenTest {

    private val repository = FakeProfileRepository()

    private fun viewModel() = ProfileViewModel(GetProfileUseCase(repository))

    // SPEC: PROF-01
    @Test
    fun `renders the identity - name, plan and calorie target - with no error`() = runComposeUiTest {
        repository.profile = FakeProfileRepository.sampleProfile

        setContent {
            MaterialTheme { ProfileRoute(viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("profile_screen"))
        onNodeWithTag("profile_title", useUnmergedTree = true).assertExists()
        onAllNodesWithText("Karel").assertCountEquals(1)
        onAllNodesWithText("Cutting · 2,400 kcal target").assertCountEquals(1)
        onNodeWithTag("profile_error").assertDoesNotExist()
    }

    // SPEC: PROF-02
    // SPEC: UX-04
    @Test
    fun `shows the daily goals as read-only value rows - no tap affordance until an editor exists`() = runComposeUiTest {
        repository.profile = FakeProfileRepository.sampleProfile

        setContent {
            MaterialTheme { ProfileRoute(viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("profile_screen"))
        // UX-04: the values are shown, and the rows promise nothing — a row that accepted a
        // tap and did nothing was the defect this amendment removed.
        onNodeWithTag("profile_goal_calories").assertExists().assertHasNoClickAction()
        onNodeWithTag("profile_goal_protein").assertExists().assertHasNoClickAction()
        onNodeWithTag("profile_goal_activity").assertExists().assertHasNoClickAction()
        onAllNodesWithText("180 g").assertCountEquals(1)
        onAllNodesWithText("Trains 5×/week").assertCountEquals(1)
    }

    // SPEC: PROF-03
    @Test
    fun `shows the weekly stats - day streak, average protein and current weight`() = runComposeUiTest {
        repository.profile = FakeProfileRepository.sampleProfile

        setContent {
            MaterialTheme { ProfileRoute(viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("profile_screen"))
        onAllNodesWithText("day streak").assertCountEquals(1)
        onAllNodesWithText("172g").assertCountEquals(1)
        onAllNodesWithText("82.4").assertCountEquals(1)
    }

    // SPEC: PROF-04
    // SPEC: UX-04
    @Test
    fun `shows the settings list as read-only rows - the tap ships with the destination`() = runComposeUiTest {
        repository.profile = FakeProfileRepository.sampleProfile

        setContent {
            MaterialTheme { ProfileRoute(viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("profile_screen"))
        onNodeWithTag("profile_setting_units").assertExists().assertHasNoClickAction()
        onNodeWithTag("profile_setting_reminders").assertExists().assertHasNoClickAction()
        onNodeWithTag("profile_setting_connected").assertExists().assertHasNoClickAction()
        onNodeWithTag("profile_setting_account").assertExists().assertHasNoClickAction()
    }

    // SPEC: JRN-02
    @Test
    fun `the stats row is a real control - it opens the week in review`() = runComposeUiTest {
        repository.profile = FakeProfileRepository.sampleProfile
        var opened = 0

        setContent {
            MaterialTheme { ProfileRoute(viewModel = viewModel(), onOpenWeek = { opened++ }) }
        }

        awaitNode(hasTestTag("profile_screen"))
        // The tap exists BECAUSE the destination does (UX-04's rule, the other direction):
        // the streak and avg-protein claims are now verifiable by the surface they open.
        onNodeWithTag("profile_week_link").assertExists().performClick()
        assertEquals(1, opened, "the stats row navigates to the week review")
    }

    // SPEC: PROF-05
    @Test
    fun `shows presentation-mapped error copy and retry when loading fails`() = runComposeUiTest {
        repository.failure = DomainError.Network

        setContent {
            MaterialTheme { ProfileRoute(viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("profile_error"))
        onAllNodesWithText(DomainError.Network.toUserMessage()).assertCountEquals(1)
        onNodeWithTag("profile_retry", useUnmergedTree = true).assertExists()
    }

    // SPEC: PROF-05
    @Test
    fun `tapping retry after a failure reloads and shows the recovered profile`() = runComposeUiTest {
        repository.failure = DomainError.Network
        val vm = viewModel()

        setContent {
            MaterialTheme { ProfileRoute(viewModel = vm) }
        }

        awaitNode(hasTestTag("profile_error"))

        repository.failure = null
        repository.profile = FakeProfileRepository.sampleProfile
        onNodeWithTag("profile_retry").performClick()

        awaitNode(hasTestTag("profile_screen"))
    }
}
