package com.kvdm.fuelled.inspector

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kvdm.fuelled.presentation.components.BaseScreen
import com.kvdm.fuelled.presentation.components.PlaceholderScreen
import com.kvdm.fuelled.presentation.navigation.AppShell
import com.kvdm.fuelled.presentation.navigation.appTabs
import com.kvdm.fuelled.presentation.foods.FoodDetailScreen
import com.kvdm.fuelled.presentation.foods.FoodsScreen
import com.kvdm.fuelled.presentation.profile.ProfileScreen
import com.kvdm.fuelled.presentation.supplements.SupplementsScreen
import com.kvdm.fuelled.presentation.today.TodayScreen

/**
 * One previewable screen: a stable [id] (the `-Pscreen=` selector and output directory
 * name), a human [title] for the gallery, and the composable [content] exactly as the
 * app hosts it.
 *
 * The `@Preview` analog for the create-cmp inspector: the registry makes "render screen
 * X" a closed, enumerable operation. The scaffolder regenerates the tab entries from the
 * configured `tabs`, and the feature stamper (`qa/scaffold-feature.mjs`, via the
 * `add-feature`/`add-screen` skills) auto-appends a stamped screen at the
 * `// cmp:anchor preview-registry` marker below; when you add a screen by hand, add it
 * there too — the renderScreens harness, the gallery, and golden baselines pick it up by id.
 *
 * State variants (the Storybook "story" analog): a screen in a specific UI state is just
 * another entry with a derived id — e.g. `ScreenPreview("home@empty", "Home — empty")`
 * hosting the screen with that state forced (a state-first overload of the screen, or
 * preview-only fakes behind its usual parameters). Every entry renders the same way
 * (gallery card, `-Pscreen=` selector, golden baseline), so loading/empty/error states
 * sit side by side with the default seeded state.
 *
 * Component stories (`component.<kebab-name>` ids, ComponentStories.kt) are appended
 * below — one isolated render per `presentation/components` composable. The console
 * keeps them out of the Screens grid and shows each at the top of its Components-page
 * entry; the verify lane's `componentStories` step enforces one story per component.
 */
data class ScreenPreview(
    val id: String,
    val title: String,
    val content: @Composable () -> Unit,
)

/** Every registered screen, in gallery order. Ids must be unique and filesystem-safe. */
fun previewRegistry(): List<ScreenPreview> = listOf(
    ScreenPreview("shell", "App shell — bottom nav (first tab selected)") {
        AppShell(
            tabs = appTabs(
                today = { TodayScreen() },
                foods = { FoodsScreen() },
                supplements = { SupplementsScreen() },
                profile = { ProfileScreen() },
            ),
        )
    },
    ScreenPreview("today", "Today tab") { TabHost { TodayScreen() } },
    ScreenPreview("foods", "Foods tab") { TabHost { FoodsScreen() } },
    ScreenPreview("food-detail", "Food detail") { TabHost { FoodDetailScreen() } },
    ScreenPreview("supplements", "Supplements tab") { TabHost { SupplementsScreen() } },
    ScreenPreview("profile", "Profile tab") { TabHost { ProfileScreen() } },
    // cmp:anchor preview-registry
) + componentStories() + placeholderScreenStories()

/**
 * Hosts a single tab's content the way [AppShell] does — inside [BaseScreen] — minus the
 * bottom bar, so a tab previews with the same insets/background it gets in the shell.
 */
@Composable
private fun TabHost(content: @Composable () -> Unit) {
    BaseScreen {
        Box(Modifier.fillMaxSize()) { content() }
    }
}

/** Component story for the generated [PlaceholderScreen] — see ComponentStories.kt for the convention. */
private fun placeholderScreenStories(): List<ScreenPreview> = listOf(
    ScreenPreview("component.placeholder-screen", "PlaceholderScreen — component story") {
        StoryHost { PlaceholderScreen(title = "Placeholder", titleTag = "story_title") }
    },
)
