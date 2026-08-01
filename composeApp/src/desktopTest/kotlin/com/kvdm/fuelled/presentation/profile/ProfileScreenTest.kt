package com.kvdm.fuelled.presentation.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.usecase.GetProfileUseCase
import com.kvdm.fuelled.domain.usecase.UpdateGoalsUseCase
import com.kvdm.fuelled.domain.usecase.UpdateProfileNameUseCase
import com.kvdm.fuelled.testing.awaitNode
import com.kvdm.fuelled.testing.fakes.FakeProfileRepository
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
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

    private val todayRepository = FakeTodayRepository()

    private fun viewModel() = ProfileViewModel(GetProfileUseCase(repository), UpdateGoalsUseCase(todayRepository), UpdateProfileNameUseCase(repository))

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
    fun `goal rows carry the tap exactly where an editor exists - and only there`() = runComposeUiTest {
        repository.profile = FakeProfileRepository.sampleProfile

        setContent {
            MaterialTheme { ProfileRoute(viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("profile_screen"))
        // UX-04, both directions: calorie and protein have editors (PERS-02), so they are
        // controls; activity has none yet (S5), so it promises nothing.
        onNodeWithTag("profile_goal_calories").assertExists().assertHasClickAction()
        onNodeWithTag("profile_goal_protein").assertExists().assertHasClickAction()
        onNodeWithTag("profile_goal_activity").assertExists().assertHasNoClickAction()
        onAllNodesWithText("180 g").assertCountEquals(1)
        onAllNodesWithText("Trains 5×/week").assertCountEquals(1)
    }

    // SPEC: PERS-02
    @Test
    fun `editing the protein goal writes through the one goal store and re-renders`() = runComposeUiTest {
        repository.profile = FakeProfileRepository.sampleProfile

        setContent {
            MaterialTheme { ProfileRoute(viewModel = viewModel()) }
        }
        awaitNode(hasTestTag("profile_screen"))

        onNodeWithTag("profile_goal_protein").performClick()
        awaitNode(hasTestTag("profile_goal_input"))
        onNodeWithTag("profile_goal_input").performTextClearance()
        onNodeWithTag("profile_goal_input").performTextInput("200")
        onNodeWithTag("profile_goal_save").performClick()
        waitForIdle()

        assertEquals(
            listOf(2400 to 200),
            todayRepository.goalUpdates,
            "the edited protein and the unedited calorie target reach the ONE goal store",
        )
    }

    // SPEC: PERS-02
    @Test
    fun `junk input in the goal editor reaches no write`() = runComposeUiTest {
        repository.profile = FakeProfileRepository.sampleProfile

        setContent {
            MaterialTheme { ProfileRoute(viewModel = viewModel()) }
        }
        awaitNode(hasTestTag("profile_screen"))

        onNodeWithTag("profile_goal_calories").performClick()
        awaitNode(hasTestTag("profile_goal_input"))
        onNodeWithTag("profile_goal_input").performTextClearance()
        onNodeWithTag("profile_goal_input").performTextInput("lots")
        onNodeWithTag("profile_goal_save").performClick()
        waitForIdle()

        assertEquals(0, todayRepository.goalUpdates.size, "a non-numeric goal must attempt no write")
    }

    // SPEC: PERS-03
    @Test
    fun `renaming from the identity header persists and re-renders the header`() = runComposeUiTest {
        repository.profile = FakeProfileRepository.sampleProfile

        setContent {
            MaterialTheme { ProfileRoute(viewModel = viewModel()) }
        }
        awaitNode(hasTestTag("profile_screen"))

        onNodeWithTag("profile_edit_name").performClick()
        awaitNode(hasTestTag("profile_name_input"))
        onNodeWithTag("profile_name_input").performTextClearance()
        onNodeWithTag("profile_name_input").performTextInput("Alex")
        onNodeWithTag("profile_name_save").performClick()
        waitForIdle()

        assertEquals(listOf("Alex"), repository.nameUpdates)
        onAllNodesWithText("Alex").assertCountEquals(1)
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
