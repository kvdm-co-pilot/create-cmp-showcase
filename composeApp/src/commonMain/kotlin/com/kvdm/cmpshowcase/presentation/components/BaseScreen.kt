package com.kvdm.cmpshowcase.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kvdm.cmpshowcase.presentation.theme.designToken

/**
 * The insets moat, pre-solved. Every screen wraps its content in [BaseScreen] instead of
 * re-deriving edge-to-edge padding. The Activity is edge-to-edge (transparent system bars);
 * this Scaffold owns the status-bar / navigation-bar insets in shared code, which also maps
 * to iOS safe areas under Compose Multiplatform.
 *
 * - [topBar] / [bottomBar] draw edge-to-edge (e.g. a nav bar that bleeds behind the gesture
 *   pill) and are responsible for their own inset padding.
 * - The content lambda receives padding already accounting for any bars; by default the body
 *   gets status + navigation bar padding so plain screens are safe with zero ceremony.
 */
@Composable
fun BaseScreen(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    applyStatusBarPadding: Boolean = true,
    applyNavBarPadding: Boolean = true,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        // Screens manage their own insets via the padding modifiers below, so the Scaffold
        // itself consumes nothing — this avoids the classic doubled-padding bug.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = if (containerColor == Color.Unspecified)
            androidx.compose.material3.MaterialTheme.colorScheme.background else containerColor,
        topBar = topBar,
        bottomBar = bottomBar,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .then(if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier)
                .then(if (applyNavBarPadding) Modifier.navigationBarsPadding() else Modifier)
                // Inspector: self-report the inset facts this Box actually applies. No dimen
                // token is used here — the padding comes from Scaffold insets, not the design
                // system — so the tokens list is empty and only the resolved facts are emitted.
                .designToken(
                    tokens = emptyList(),
                    resolved = mapOf(
                        "statusBarPadding" to applyStatusBarPadding.toString(),
                        "navBarPadding" to applyNavBarPadding.toString(),
                    ),
                )
        ) {
            content(innerPadding)
        }
    }
}
