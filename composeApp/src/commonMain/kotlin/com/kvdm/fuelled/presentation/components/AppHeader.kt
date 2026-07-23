package com.kvdm.fuelled.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp

/**
 * The screen header: a headline row with an optional back affordance and a trailing
 * actions slot, tagged `<screenTag>_title` and `<screenTag>_back`. Deliberately not an
 * M3 `TopAppBar` — no scroll behaviors, no center-aligned variants, no window-inset
 * handling (`BaseScreen` owns insets, SHELL-03). A collapsing toolbar would be a
 * registry addition, not a default.
 *
 * The back affordance is a Material `IconButton` with the auto-mirrored arrow (RTL-correct,
 * 48 dp touch target) — never a text link. It renders only when [onBack] is non-null, so a
 * tab root never shows a back control.
 *
 * @param title Headline text, rendered in `headlineMedium`.
 * @param screenTag Feature slug; derives the `<screenTag>_title` and `<screenTag>_back` tags.
 * @param onBack Non-null renders a 48 dp back affordance left of the title.
 * @param actions Trailing slot at the row's end, for per-screen controls.
 */
@Composable
fun AppHeader(
    title: String,
    screenTag: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).semantics { testTag = "${screenTag}_back" },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { testTag = "${screenTag}_title" },
        )
        actions()
    }
}
