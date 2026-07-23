package com.kvdm.fuelled.presentation.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
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
    @Test
    fun `shows the daily goals - calorie target, protein goal and activity - each a tappable row`() = runComposeUiTest {
        repository.profile = FakeProfileRepository.sampleProfile

        setContent {
            MaterialTheme { ProfileRoute(viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("profile_screen"))
        // Each goal is a present, actionable row (destinations are out of scope — tapping is a no-op).
        onNodeWithTag("profile_goal_calories").assertExists().performClick()
        onNodeWithTag("profile_goal_protein").assertExists().performClick()
        onNodeWithTag("profile_goal_activity").assertExists().performClick()
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
    @Test
    fun `shows the settings list - each a present and tappable row`() = runComposeUiTest {
        repository.profile = FakeProfileRepository.sampleProfile

        setContent {
            MaterialTheme { ProfileRoute(viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("profile_screen"))
        onNodeWithTag("profile_setting_units").assertExists().performClick()
        onNodeWithTag("profile_setting_reminders").assertExists().performClick()
        onNodeWithTag("profile_setting_connected").assertExists().performClick()
        onNodeWithTag("profile_setting_account").assertExists().performClick()
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
