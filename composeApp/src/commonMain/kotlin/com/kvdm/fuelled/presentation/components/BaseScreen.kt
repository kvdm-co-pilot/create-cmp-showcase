package com.kvdm.fuelled.presentation.components

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
import com.kvdm.fuelled.presentation.theme.designToken

/**
 * The edge-to-edge scaffold that owns system-bar insets in shared code. The Activity
 * draws behind transparent system bars; this component applies status-bar and
 * navigation-bar padding once (mapping to iOS safe areas under Compose Multiplatform),
 * consumes what it applies to prevent doubled padding, and self-reports the applied
 * inset facts to the inspector. Screens wrap their content in it instead of re-deriving
 * insets.
 *
 * @param containerColor Background color; `Color.Unspecified` resolves to the theme background.
 * @param applyStatusBarPadding False lets the body draw under the status bar, for
 *   full-bleed content that handles the top inset itself.
 * @param applyNavBarPadding False lets the body draw under the navigation bar — set it
 *   when a bottom bar owns that inset instead.
 * @param topBar Draws edge-to-edge and is responsible for its own inset padding.
 * @param bottomBar Draws edge-to-edge and is responsible for its own inset padding
 *   (e.g. a nav bar that bleeds behind the gesture pill).
 * @param content Screen body. Its padding is already applied by the wrapper; the
 *   `PaddingValues` are passed through for callers that need the raw values.
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
