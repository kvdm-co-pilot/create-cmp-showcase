package com.kvdm.fuelled.presentation.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The registry's icon button: M3 `IconButton` held to the 48 dp touch-target floor.
 *
 * Stock M3 `IconButton` defaults to a 40x40 dp target — below WCAG 2.2 SC 2.5.8 and this
 * verify lane's own a11y bar — so every raw use is a violation waiting to be measured
 * (and historically why a text link masqueraded as a back button). This wrapper clears the
 * floor once, by construction, the same way [AppPrimaryButton] does for filled buttons.
 *
 * A [contentDescription] is REQUIRED, not defaulted: an icon-only control with no label is
 * invisible to screen readers (`missing-label` in the same audit). Pass what the control
 * does ("Back", "Add entry"), never what the icon looks like.
 *
 * @param icon The vector to render (e.g. `Icons.AutoMirrored.Filled.ArrowBack`).
 * @param contentDescription What the control does, for screen readers.
 * @param tint Icon tint; defaults to the current content color.
 */
@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(AppButtonDefaults.MinTouchTarget),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (tint == Color.Unspecified) LocalContentColor.current else tint,
        )
    }
}
