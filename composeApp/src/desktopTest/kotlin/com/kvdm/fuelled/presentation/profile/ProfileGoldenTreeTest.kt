package com.kvdm.fuelled.presentation.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.domain.model.Profile
import com.kvdm.fuelled.domain.model.ProfileGoals
import com.kvdm.fuelled.domain.model.ProfileIdentity
import com.kvdm.fuelled.domain.model.WeeklyStats
import com.kvdm.fuelled.domain.usecase.GetProfileUseCase
import com.kvdm.fuelled.domain.usecase.UpdateGoalsUseCase
import com.kvdm.fuelled.domain.usecase.UpdateProfileNameUseCase
import com.kvdm.fuelled.testing.StructuralTree
import com.kvdm.fuelled.testing.awaitNode
import com.kvdm.fuelled.testing.fakes.FakeProfileRepository
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Golden-tree structural baseline — SPEC: PROF-06.
 *
 * Renders Profile headlessly with FIXED fake data and diffs the semantics structure against the
 * committed baseline (`qa/golden/profile.json`). No pixels, no flake: a failure means the
 * screen's STRUCTURE changed.
 *
 * Unintended drift → fix your change. Intended drift → regenerate the baseline explicitly and
 * declare it:
 *
 *   UPDATE_GOLDEN=1 ./gradlew :composeApp:desktopTest --tests "*GoldenTree*"
 */
@OptIn(ExperimentalTestApi::class)
class ProfileGoldenTreeTest {

    private val baseline = File("../qa/golden/profile.json")

    // Fixed dataset — golden renders must be deterministic; never use live/random data here.
    private val goldenProfile = Profile(
        identity = ProfileIdentity(name = "Golden User", planLabel = "Cutting", calorieTarget = 2400),
        goals = ProfileGoals(calorieTarget = 2400, proteinGoalG = 180, activity = "Trains 5×/week"),
        weeklyStats = WeeklyStats(streakDays = 12, avgProteinG = 172, weightKg = 82.4),
    )

    // SPEC: PROF-06
    @Test
    fun `profile structure matches the committed golden tree`() = runComposeUiTest {
        val repository = FakeProfileRepository().apply { profile = goldenProfile }
        val viewModel = ProfileViewModel(GetProfileUseCase(repository), UpdateGoalsUseCase(FakeTodayRepository()), UpdateProfileNameUseCase(repository))

        setContent {
            MaterialTheme {
                ProfileRoute(viewModel = viewModel)
            }
        }
        awaitNode(hasTestTag("profile_screen"))

        val rendered = StructuralTree.serialize(onRoot(useUnmergedTree = true).fetchSemanticsNode())

        if (System.getenv("UPDATE_GOLDEN") == "1") {
            baseline.parentFile.mkdirs()
            baseline.writeText(rendered)
            return@runComposeUiTest
        }

        if (!baseline.exists()) fail(
            "[PROF-06] Golden baseline missing (qa/golden/profile.json). " +
                "Generate it explicitly: UPDATE_GOLDEN=1 ./gradlew :composeApp:desktopTest --tests \"*GoldenTree*\"",
        )

        val expected = baseline.readText()
        if (rendered != expected) {
            val diffAt = rendered.zip(expected).indexOfFirst { (a, b) -> a != b }
                .let { if (it == -1) minOf(rendered.length, expected.length) else it }
            fail(
                "[PROF-06] Profile's rendered structure drifted from qa/golden/profile.json (first diff at char $diffAt).\n" +
                    "If this drift is UNINTENDED: fix your change.\n" +
                    "If it is the intended change: regenerate with UPDATE_GOLDEN=1 and declare it.\n" +
                    "--- rendered (excerpt) ---\n${rendered.substring(maxOf(0, diffAt - 120), minOf(rendered.length, diffAt + 240))}\n" +
                    "--- baseline (excerpt) ---\n${expected.substring(maxOf(0, diffAt - 120), minOf(expected.length, diffAt + 240))}",
            )
        }
    }
}
