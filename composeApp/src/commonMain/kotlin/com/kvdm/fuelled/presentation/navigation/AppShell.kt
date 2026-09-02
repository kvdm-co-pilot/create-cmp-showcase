package com.kvdm.fuelled.presentation.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import com.kvdm.fuelled.presentation.theme.FuelledMotion
import com.kvdm.fuelled.presentation.theme.LocalMotion
import com.kvdm.fuelled.presentation.theme.moves
import com.kvdm.fuelled.presentation.theme.tween

/**
 * The app shell: the bottom bar plus the selected tab's content, inside [BaseScreen] so
 * the tabs inherit the insets once (SHELL-05). Selection is the shell's own state — a tab
 * has no route argument to carry, and hoisting the index bought nothing when the last
 * caller (Today's supplement highlight) became an ordinary push (NAV-05).
 *
 * Motion (D5, MOTION-05): tab content swaps with a Material fade-through — the outgoing tab
 * fades on `Quick`/`Exit`, the incoming fades in on `Standard`/`Enter` from `TabScale`. Under
 * Reduced it is a plain cross-fade; under Instant a cut.
 *
 * Constructible without a DI container: its screen tests build it directly, which is why
 * the day-rollover wake lives in [AppNavHost] rather than here.
 */
@Composable
fun AppShell(tabs: List<AppTab>) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val select: (Int) -> Unit = { selected = it }
    val motion = LocalMotion.current

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
        AnimatedContent(
            targetState = selected,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val enter = fadeIn(motion.tween(FuelledMotion.Duration.Standard, FuelledMotion.Easings.Enter))
                    .let { if (motion.moves) it + scaleIn(motion.tween(FuelledMotion.Duration.Standard, FuelledMotion.Easings.Enter), initialScale = FuelledMotion.TabScale) else it }
                val exit = fadeOut(motion.tween(FuelledMotion.Duration.Quick, FuelledMotion.Easings.Exit))
                enter togetherWith exit
            },
            label = "tab",
        ) { index ->
            Box(Modifier.fillMaxSize()) {
                tabs.getOrNull(index)?.content?.invoke()
            }
        }
    }
}
