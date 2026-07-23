package com.kvdm.fuelled.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kvdm.fuelled.presentation.components.AppBottomBar
import com.kvdm.fuelled.presentation.components.BaseScreen
import com.kvdm.fuelled.presentation.components.exposeTestTagsForAutomation

/**
 * Generic bottom-nav shell. Parameterized by a [tabs] list — NOT role-hardcoded.
 * Hosts the selected tab's content inside a [BaseScreen] so each tab gets correct insets;
 * the bottom bar reserves the navigation-bar inset exactly once.
 *
 * The bar itself is [com.kvdm.fuelled.presentation.components.AppBottomBar] — promoted out of
 * this file into the governed component registry (§4.3 of the component-vocabulary
 * proposal): it was already a mature component, just invisible to the registry as a
 * `private` composable here.
 */
@Composable
fun AppShell(tabs: List<AppTab>) {
    var selected by rememberSaveable { mutableIntStateOf(0) }

    BaseScreen(
        // Expose Compose testTags to the platform automation layer (Android resource-ids /
        // iOS accessibilityIdentifiers) so Appium/uiautomator id-selectors and the
        // cmp-test-generated suites find them; covers the whole subtree. Desktop: no-op.
        modifier = Modifier.exposeTestTagsForAutomation(),
        // The bottom bar owns the navigation-bar inset; the body must not also pad it.
        applyNavBarPadding = false,
        bottomBar = {
            AppBottomBar(
                tabs = tabs,
                selectedIndex = selected,
                onSelect = { selected = it },
            )
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            tabs.getOrNull(selected)?.content?.invoke()
        }
    }
}
