package com.kvdm.fuelled.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            AppTextButton(
                text = "← Back",
                onClick = onBack,
                modifier = Modifier.semantics { testTag = "${screenTag}_back" },
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f).semantics { testTag = "${screenTag}_title" },
        )
        actions()
    }
}
