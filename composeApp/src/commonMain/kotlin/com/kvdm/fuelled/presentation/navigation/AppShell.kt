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
/**
 * [selectedIndex] and [onSelectTab] default to `null`, in which case the shell keeps its own
 * selection — the ordinary case. They exist because one screen legitimately needs to move the
 * user to another TAB rather than push a destination: Today's supplement highlight opens the
 * Supplements tab (TODAY-11). Hoisting the selection is the standard answer; a global event bus
 * for one link would not be.
 */
@Composable
fun AppShell(
    tabs: List<AppTab>,
    selectedIndex: Int? = null,
    onSelectTab: ((Int) -> Unit)? = null,
) {
    var internal by rememberSaveable { mutableIntStateOf(0) }
    val selected = selectedIndex ?: internal
    val select: (Int) -> Unit = onSelectTab ?: { internal = it }

    BaseScreen(
        // testTag exposure for automation is applied once on the NavHost in AppNavHost.kt —
        // the graph root, so every destination inherits it, not just these tabs. It used to
        // live here, which is why non-tab destinations had no automation-visible ids.
        // The bottom bar owns the navigation-bar inset; the body must not also pad it.
        applyNavBarPadding = false,
        bottomBar = {
            AppBottomBar(
                tabs = tabs,
                selectedIndex = selected,
                onSelect = select,
            )
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            tabs.getOrNull(selected)?.content?.invoke()
        }
    }
}
