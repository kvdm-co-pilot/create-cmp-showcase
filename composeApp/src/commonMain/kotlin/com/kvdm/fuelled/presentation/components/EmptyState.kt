package com.kvdm.fuelled.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
 * The Empty arm of the four-state contract: shown when a load succeeds with nothing to
 * list. Tags itself `<screenTag>_empty`, so the state is selectable in tests and E2E
 * flows. The default copy is deliberately generic — a shaped feature replaces
 * `title`/`body` with its own domain language.
 *
 * @param screenTag Feature slug; the root is tagged `<screenTag>_empty`.
 * @param title Headline. The default is placeholder copy, meant to be replaced per feature.
 * @param body Optional supporting line under the title.
 * @param action Optional call-to-action below the text — an [AppTextButton], for example.
 */
@Composable
fun EmptyState(
    screenTag: String,
    modifier: Modifier = Modifier,
    title: String = "Nothing here yet",
    body: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxSize().semantics { testTag = "${screenTag}_empty" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (body != null) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (action != null) {
                Box(Modifier.padding(top = 12.dp)) { action() }
            }
        }
    }
}
