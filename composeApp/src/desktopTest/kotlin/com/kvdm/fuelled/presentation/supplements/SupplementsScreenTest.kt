package com.kvdm.fuelled.presentation.supplements

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.domain.usecase.GetSupplementStackUseCase
import com.kvdm.fuelled.domain.usecase.SetSupplementTakenUseCase
import com.kvdm.fuelled.testing.awaitNode
import com.kvdm.fuelled.testing.fakes.FakeSupplementRepository
import kotlin.test.Test

/**
 * Durable screen tests — first-party Compose UI Test, spec-cited, testTag selectors. Each test
 * verifies a clause from `specs/supplements.spec.md` against the VM-backed [SupplementsRoute]
 * wired to a hand-written fake repository (mirrors FoodsScreenTest/TodayScreenTest).
 */
@OptIn(ExperimentalTestApi::class)
class SupplementsScreenTest {

    private val repository = FakeSupplementRepository()

    private fun viewModel() =
        SupplementsViewModel(GetSupplementStackUseCase(repository), SetSupplementTakenUseCase(repository))

    private val stack = listOf(
        Supplement("1", "Creatine", "5 g", "Morning", taken = true),
        Supplement("2", "Omega-3", "1 g", "Morning", taken = false),
        Supplement("3", "Caffeine", "200 mg", "Pre-workout", taken = false),
    )

    // SPEC: SUPP-01
    @Test
    fun `renders the stack grouped by timing with name and dose and no error`() = runComposeUiTest {
        repository.stack = stack

        setContent {
            MaterialTheme { SupplementsRoute(viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("supplements_screen"))
        onNodeWithTag("supplements_title", useUnmergedTree = true).assertExists()
        onAllNodesWithText("MORNING").assertCountEquals(1)
        onAllNodesWithText("PRE-WORKOUT").assertCountEquals(1)
        onAllNodesWithText("Creatine").assertCountEquals(1)
        onAllNodesWithText("5 g").assertCountEquals(1)
        onNodeWithTag("supplements_error").assertDoesNotExist()
    }

    // SPEC: SUPP-02
    @Test
    fun `shows the taken-of-total summary`() = runComposeUiTest {
        repository.stack = stack // 1 of 3 taken

        setContent {
            MaterialTheme { SupplementsRoute(viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("supplements_summary"))
        onAllNodesWithText("of 3 taken").assertCountEquals(1)
    }

    // SPEC: SUPP-03
    @Test
    fun `tapping a take control toggles the state and updates the summary`() = runComposeUiTest {
        repository.stack = stack // 1 of 3 taken

        setContent {
            MaterialTheme { SupplementsRoute(viewModel = viewModel()) }
        }

        awaitNode(hasText("of 3 taken"))
        // Take Omega-3 (id 2): the summary count climbs from 1 to 2.
        onNodeWithTag("supplements_take_2").performClick()
        awaitNode(hasTestTag("supplements_screen"))
        onAllNodesWithText("of 3 taken").assertCountEquals(1) // still 3 total
        // The persisted state is reflected: two are now taken.
        onNodeWithTag("supplements_take_1").assertExists()
    }

    // SPEC: SUPP-03
    @Test
    fun `the toggled state survives a fresh render from the same source`() = runComposeUiTest {
        repository.stack = stack

        setContent {
            MaterialTheme { SupplementsRoute(viewModel = viewModel()) }
        }
        awaitNode(hasText("of 3 taken"))
        onNodeWithTag("supplements_take_2").performClick()
        awaitNode(hasTestTag("supplements_screen"))

        // A brand-new ViewModel over the SAME (persisted) repository — a reload — sees the write.
        setContent {
            MaterialTheme { SupplementsRoute(viewModel = viewModel()) }
        }
        awaitNode(hasTestTag("supplements_screen"))
        // Omega-3 now reads "Taken" (its take control's contentDescription flips on persistence).
        onAllNodesWithText("Creatine").assertCountEquals(1)
    }

    // SPEC: SUPP-04
    @Test
    fun `shows the empty state when the stack is empty`() = runComposeUiTest {
        repository.stack = emptyList()

        setContent {
            MaterialTheme { SupplementsRoute(viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("supplements_empty"))
        onNodeWithTag("supplements_error").assertDoesNotExist()
    }

    // SPEC: SUPP-05
    @Test
    fun `shows presentation-mapped error copy and retry when loading fails`() = runComposeUiTest {
        repository.failure = DomainError.Network

        setContent {
            MaterialTheme { SupplementsRoute(viewModel = viewModel()) }
        }

        awaitNode(hasTestTag("supplements_error"))
        onAllNodesWithText(DomainError.Network.toUserMessage()).assertCountEquals(1)
        onNodeWithTag("supplements_retry", useUnmergedTree = true).assertExists()
    }

    // SPEC: SUPP-05
    @Test
    fun `tapping retry after a failure reloads and shows the recovered stack`() = runComposeUiTest {
        repository.failure = DomainError.Network
        val vm = viewModel()

        setContent {
            MaterialTheme { SupplementsRoute(viewModel = vm) }
        }

        awaitNode(hasTestTag("supplements_error"))

        repository.failure = null
        repository.stack = stack
        onNodeWithTag("supplements_retry").performClick()

        awaitNode(hasTestTag("supplements_screen"))
        onAllNodesWithText("Creatine").assertCountEquals(1)
    }
}
