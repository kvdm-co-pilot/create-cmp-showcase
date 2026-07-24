package com.kvdm.fuelled.presentation.components

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The filled call-to-action button: M3 `Button` with a 48 dp minimum touch target
 * applied. Stock M3 buttons sit below that floor by default; wrapping them here clears
 * WCAG 2.2 SC 2.5.8 and the harness's `audit_a11y` bar once, for every call site.
 */
@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.sizeIn(minWidth = AppButtonDefaults.MinTouchTarget, minHeight = AppButtonDefaults.MinTouchTarget),
    ) {
        Text(text)
    }
}

/**
 * The low-emphasis text button, with the same 48 dp floor as [AppPrimaryButton]. The
 * registry's buttons are these two plus [AppIconButton] — a new variant (loading,
 * destructive, FAB) is a registry addition a human approves, not a local tweak.
 */
@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.sizeIn(minWidth = AppButtonDefaults.MinTouchTarget, minHeight = AppButtonDefaults.MinTouchTarget),
    ) {
        Text(text)
    }
}

/** Shared button constants, following the `ComponentDefaults` naming convention. */
object AppButtonDefaults {
    val MinTouchTarget = 48.dp
}
