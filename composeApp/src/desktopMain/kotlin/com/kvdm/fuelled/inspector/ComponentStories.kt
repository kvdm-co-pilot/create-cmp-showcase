package com.kvdm.fuelled.inspector

import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.Check
import com.kvdm.fuelled.presentation.components.TickButton
import com.kvdm.fuelled.presentation.components.AnimatedNumber
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.presentation.components.AppBottomBar
import com.kvdm.fuelled.presentation.components.AppHeader
import com.kvdm.fuelled.presentation.components.AppIconButton
import com.kvdm.fuelled.presentation.components.AppPrimaryButton
import com.kvdm.fuelled.presentation.components.AppTextButton
import com.kvdm.fuelled.presentation.components.BaseScreen
import com.kvdm.fuelled.presentation.components.ContentStateContainer
import com.kvdm.fuelled.presentation.components.ContentStateDefaults
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.presentation.components.EmptyState
import com.kvdm.fuelled.presentation.components.ErrorState
import com.kvdm.fuelled.presentation.components.ListItemCard
import com.kvdm.fuelled.presentation.components.ListItemSkeleton
import com.kvdm.fuelled.presentation.components.NavItem
import com.kvdm.fuelled.presentation.components.ProgressRing
import com.kvdm.fuelled.presentation.components.ScreenColumn
import com.kvdm.fuelled.presentation.components.StatBar
import com.kvdm.fuelled.presentation.components.StatTile
import com.kvdm.fuelled.presentation.components.Tag
import com.kvdm.fuelled.presentation.navigation.AppTab
import com.kvdm.fuelled.presentation.theme.FuelledColors
import com.kvdm.fuelled.presentation.theme.FuelledTokens

/**
 * Component stories — one preview-registry entry per `@Composable` in
 * `presentation/components` (the Storybook analog at component granularity).
 * Each story renders the component in isolation on a plain tokened surface;
 * a multi-variant component stacks its variants in ONE render. Ids follow
 * `component.<kebab-case-of-composable-name>` (`AppHeader` →
 * `component.app-header`), derivable mechanically from the name — the
 * verify lane's `componentStories` step (qa/lib/component-stories.mjs)
 * enforces exactly one story per component. The console excludes
 * `component.*` entries from the Screens grid and shows each render at the
 * top of that component's Components-page entry instead.
 *
 * These are preview-surface code (desktopMain), not production API: sample
 * args only, tokens for every design value, testTags on every interactive
 * node — a story meets the same bar the screens do.
 */
fun componentStories(): List<ScreenPreview> = listOf(
    // Structure: the containers a screen roots itself in.
    story("component.screen-column", "ScreenColumn") {
        ScreenColumn(screenTag = "story") {
            Text("ScreenColumn owns the tagged root and the PaddingPage inset.")
            Text("Children stack vertically; scrollable = true adds scrolling.")
        }
    },
    story("component.base-screen", "BaseScreen") {
        BaseScreen { _ ->
            Text(
                "BaseScreen owns the status/navigation-bar insets; body content is safe with zero ceremony.",
                modifier = Modifier.padding(FuelledTokens.PaddingPage),
            )
        }
    },
    // Header and navigation.
    variantsStory("component.app-header", "AppHeader") {
        AppHeader(title = "Screen title", screenTag = "story")
        AppHeader(
            title = "With back and action",
            screenTag = "story_nav",
            onBack = {},
            actions = {
                AppTextButton(
                    text = "Action",
                    onClick = {},
                    modifier = Modifier.semantics { testTag = "story_header_action" },
                )
            },
        )
    },
    story("component.app-bottom-bar", "AppBottomBar") {
        AppBottomBar(
            tabs = listOf(
                AppTab("Home", Icons.Filled.Home) {},
                AppTab("Profile", Icons.Filled.Person) {},
            ),
            selectedIndex = 0,
            onSelect = {},
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    },
    story("component.nav-item", "NavItem") {
        // NavItem is a RowScope member (M3's NavigationBarItem is), so the story supplies
        // the bar that scopes it — rendered as the app renders it, not in a vacuum.
        androidx.compose.material3.NavigationBar(containerColor = FuelledColors.Surface) {
        NavItem(scope = this, label = "Selected", selected = true, onClick = {}) {
            Icon(
                Icons.Filled.Home,
                contentDescription = "Selected",
                tint = FuelledColors.Primary,
            )
        }
        NavItem(scope = this, label = "Unselected", selected = false, onClick = {}) {
            Icon(
                Icons.Filled.Person,
                contentDescription = "Unselected",
                tint = FuelledColors.OnSurfaceVariant,
            )
        }
        }
    },
    // Buttons.
    variantsStory("component.app-primary-button", "AppPrimaryButton") {
        AppPrimaryButton(
            text = "Primary",
            onClick = {},
            modifier = Modifier.semantics { testTag = "story_primary" },
        )
        AppPrimaryButton(
            text = "Primary — disabled",
            onClick = {},
            enabled = false,
            modifier = Modifier.semantics { testTag = "story_primary_disabled" },
        )
    },
    variantsStory("component.app-text-button", "AppTextButton") {
        AppTextButton(
            text = "Text button",
            onClick = {},
            modifier = Modifier.semantics { testTag = "story_text" },
        )
        AppTextButton(
            text = "Text button — disabled",
            onClick = {},
            enabled = false,
            modifier = Modifier.semantics { testTag = "story_text_disabled" },
        )
    },
    variantsStory("component.app-icon-button", "AppIconButton") {
        AppIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = {},
            modifier = Modifier.semantics { testTag = "story_icon_button" },
        )
        AppIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back — disabled",
            onClick = {},
            enabled = false,
            modifier = Modifier.semantics { testTag = "story_icon_button_disabled" },
        )
    },
    // The four-state contract: all four arms of the container, stacked.
    variantsStory("component.content-state-container", "ContentStateContainer") {
        ContentStateContainer<List<String>>(
            state = ContentUiState.Loading,
            screenTag = "story_loading",
            modifier = Modifier.height(180.dp),
        ) { }
        ContentStateContainer<List<String>>(
            state = ContentUiState.Empty,
            screenTag = "story_empty",
            modifier = Modifier.height(180.dp),
        ) { }
        ContentStateContainer<List<String>>(
            state = ContentUiState.Error("Something went wrong."),
            screenTag = "story_error",
            onRetry = {},
            modifier = Modifier.height(180.dp),
        ) { }
        ContentStateContainer(
            state = ContentUiState.Content(listOf("First item", "Second item")),
            screenTag = "story_content",
            modifier = Modifier.height(180.dp),
        ) { data ->
            Column(verticalArrangement = Arrangement.spacedBy(FuelledTokens.GapCard)) {
                data.forEachIndexed { i, title ->
                    ListItemCard(
                        title = title,
                        onClick = {},
                        modifier = Modifier.semantics { testTag = "story_state_item_$i" },
                    )
                }
            }
        }
    },
    variantsStory("component.empty-state", "EmptyState") {
        EmptyState(
            screenTag = "story",
            modifier = Modifier.height(220.dp),
            body = "Items you add will show up here.",
            action = {
                AppTextButton(
                    text = "Add an item",
                    onClick = {},
                    modifier = Modifier.semantics { testTag = "story_empty_action" },
                )
            },
        )
    },
    variantsStory("component.error-state", "ErrorState") {
        ErrorState(
            message = "Something went wrong.",
            screenTag = "story",
            onRetry = {},
            modifier = Modifier.height(220.dp),
        )
    },
    // List vocabulary.
    variantsStory("component.list-item-card", "ListItemCard") {
        ListItemCard(
            title = "Title only",
            onClick = {},
            modifier = Modifier.semantics { testTag = "story_item_1" },
        )
        ListItemCard(
            title = "With subtitle",
            subtitle = "Secondary line",
            onClick = {},
            modifier = Modifier.semantics { testTag = "story_item_2" },
        )
        ListItemCard(
            title = "With a leading slot",
            subtitle = "Leading content precedes the text column",
            onClick = {},
            modifier = Modifier.semantics { testTag = "story_item_3" },
            leading = { Icon(Icons.Filled.Person, contentDescription = null) },
        )
    },
    story("component.list-skeleton", "ContentStateDefaults.ListSkeleton") {
        Box(Modifier.fillMaxSize().padding(FuelledTokens.PaddingPage)) {
            ContentStateDefaults.ListSkeleton(screenTag = "story")
        }
    },
    variantsStory("component.list-item-skeleton", "ListItemSkeleton") {
        ListItemSkeleton()
    },
    story("component.spinner", "ContentStateDefaults.Spinner") {
        ContentStateDefaults.Spinner(screenTag = "story")
    },
    // Distilled from the screens — clean reusable PRIMITIVES only (do-not-force-reuse:
    // the segmented bar and the five feature rows deliberately stayed local).
    story("component.progress-ring", "ProgressRing") {
        ProgressRing(progress = 0.66f, modifier = Modifier.size(120.dp)) {
            Text("560", color = FuelledColors.OnSurface)
        }
    },
    variantsStory("component.stat-bar", "StatBar") {
        StatBar(progress = 0.82f, label = "Protein", valueText = "148 / 180g", color = FuelledColors.Protein)
        StatBar(progress = 0.4f, color = FuelledColors.Primary)
    },
    variantsStory("component.stat-tile", "StatTile") {
        StatTile(value = "12", label = "day streak")
        StatTile(value = "172g", label = "avg protein")
    },
    // Motion primitives (motion D4): rendered at their end state under Instant — the geometry
    // and colour are what a story signs; the movement is judged on the dev-client.
    variantsStory("component.animated-number", "AnimatedNumber") {
        AnimatedNumber(value = 1865, countFrom = 0)
        AnimatedNumber(value = 121, style = MaterialTheme.typography.displayMedium, format = { "$it g" })
    },
    variantsStory("component.tick-button", "TickButton") {
        TickButton(
            icon = Icons.Filled.Check,
            checked = false,
            contentDescription = "Mark Lunch done",
            onClick = {},
            uncheckedTint = FuelledColors.OnSurfaceVariant,
            checkedTint = FuelledColors.Success,
            modifier = Modifier.semantics { testTag = "story_tick_unchecked" },
        )
        TickButton(
            icon = Icons.Filled.Check,
            checked = true,
            contentDescription = "Undo Lunch done",
            onClick = {},
            uncheckedTint = FuelledColors.OnSurfaceVariant,
            checkedTint = FuelledColors.Success,
            modifier = Modifier.semantics { testTag = "story_tick_checked" },
        )
    },
    variantsStory("component.tag", "Tag") {
        Tag("P", "38g", FuelledColors.Protein)
        Tag("C", "40g", FuelledColors.Carbs)
        Tag("F", "8g", FuelledColors.Fat)
    },
)

/**
 * `component.<kebab-name>` entry hosting [content] on the plain story surface.
 * The id is passed as a full literal (never concatenated) so the lane's parity
 * gate (qa/lib/component-stories.mjs) and a plain grep both find it.
 */
private fun story(
    id: String,
    title: String,
    content: @Composable BoxScope.() -> Unit,
): ScreenPreview = ScreenPreview(id, "$title — component story") {
    StoryHost(content)
}

/** Stacked-variants flavor: the story surface with a padded, token-gapped column. */
private fun variantsStory(
    id: String,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
): ScreenPreview = story(id, title) {
    Column(
        modifier = Modifier.fillMaxSize().padding(FuelledTokens.PaddingPage),
        verticalArrangement = Arrangement.spacedBy(FuelledTokens.GapCard),
        content = content,
    )
}

/**
 * The plain tokened surface every story renders on: theme background, nothing
 * else — the component is the only subject. Internal (not private) so the
 * generated registry can host the PlaceholderScreen story on custom-tab
 * scaffolds (PlaceholderScreen ships only when a configured tab has no
 * feature yet, so its story rides PreviewRegistry.kt, not this file).
 *
 * A `Surface`, not a bare `Box`: real screens root in [BaseScreen]'s `Scaffold`, which
 * provides `LocalContentColor` to everything below it. A Box only PAINTS a background —
 * it supplies no content color — so any component that correctly inherits one (an
 * [AppIconButton] tint, a bare `Text`) fell back to `LocalContentColor`'s black default
 * and rendered black-on-near-black. The story read as an empty rectangle while the
 * component was in fact drawing perfectly. Stories must sit in the same content-color
 * context as the screens they document, or they document a lie.
 */
@Composable
internal fun StoryHost(content: @Composable BoxScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = FuelledColors.Background,
        contentColor = FuelledColors.OnSurface,
    ) {
        Box(Modifier.fillMaxSize()) { content() }
    }
}
