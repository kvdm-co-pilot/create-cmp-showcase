package com.kvdm.cmpshowcase.presentation.favorites

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.cmpshowcase.domain.model.Favorite
import com.kvdm.cmpshowcase.domain.usecase.GetFavoritesUseCase
import com.kvdm.cmpshowcase.testing.StructuralTree
import com.kvdm.cmpshowcase.testing.awaitNode
import com.kvdm.cmpshowcase.testing.fakes.FakeFavoriteRepository
import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Golden-tree structural baseline — SPEC: FAVORITES-06.
 *
 * Renders Favorites headlessly with FIXED fake data and diffs the semantics structure against the
 * committed baseline (`qa/golden/favorites.json`). No pixels, no flake: a failure means the
 * screen's STRUCTURE changed.
 *
 * Unintended drift → fix your change. Intended drift → regenerate the baseline explicitly
 * and declare it in your summary/PR:
 *
 *   UPDATE_GOLDEN=1 ./gradlew :composeApp:desktopTest --tests "*GoldenTree*"
 */
@OptIn(ExperimentalTestApi::class)
class FavoritesGoldenTreeTest {

    private val baseline = File("../qa/golden/favorites.json")

    // Fixed dataset — golden renders must be deterministic; never use live/random data here.
    private val goldenItems = listOf(
        Favorite(id = "1", title = "Golden first", subtitle = "Structural baseline row one"),
        Favorite(id = "2", title = "Golden second", subtitle = "Structural baseline row two"),
    )

    // SPEC: FAVORITES-06
    @Test
    fun `favorites structure matches the committed golden tree`() = runComposeUiTest {
        val repository = FakeFavoriteRepository().apply { items = goldenItems }

        setContent {
            MaterialTheme {
                FavoritesScreen(onItemClick = {}, viewModel = FavoritesViewModel(GetFavoritesUseCase(repository)))
            }
        }
        awaitNode(hasText("Golden first"))

        val rendered = StructuralTree.serialize(onRoot(useUnmergedTree = true).fetchSemanticsNode())

        if (System.getenv("UPDATE_GOLDEN") == "1") {
            baseline.parentFile.mkdirs()
            baseline.writeText(rendered)
            return@runComposeUiTest
        }

        if (!baseline.exists()) fail(
            "[FAVORITES-06] Golden baseline missing (qa/golden/favorites.json). " +
                "Generate it explicitly: UPDATE_GOLDEN=1 ./gradlew :composeApp:desktopTest --tests \"*GoldenTree*\"",
        )

        val expected = baseline.readText()
        if (rendered != expected) {
            val diffAt = rendered.zip(expected).indexOfFirst { (a, b) -> a != b }
                .let { if (it == -1) minOf(rendered.length, expected.length) else it }
            fail(
                "[FAVORITES-06] Favorites's rendered structure drifted from qa/golden/favorites.json (first diff at char $diffAt).\n" +
                    "If this drift is UNINTENDED: fix your change.\n" +
                    "If it is the intended change: regenerate with UPDATE_GOLDEN=1 and declare it.\n" +
                    "--- rendered (excerpt) ---\n${rendered.substring(maxOf(0, diffAt - 120), minOf(rendered.length, diffAt + 240))}\n" +
                    "--- baseline (excerpt) ---\n${expected.substring(maxOf(0, diffAt - 120), minOf(expected.length, diffAt + 240))}",
            )
        }
    }
}
