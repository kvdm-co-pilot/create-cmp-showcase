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
 * The Error arm of the four-state contract: renders the message and, when a retry
 * handler is supplied, a `<screenTag>_retry` control with a 48 dp touch target. The
 * error state is a rendered, testable affordance — not just a ViewModel arm.
 *
 * @param message User-facing copy. Presentation maps a `DomainError` kind to it; never
 *   pass a raw exception string.
 * @param screenTag Feature slug; derives the `<screenTag>_error` and `<screenTag>_retry` tags.
 * @param onRetry Non-null renders the retry control; null renders the message alone.
 */
@Composable
fun ErrorState(
    message: String,
    screenTag: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().semantics { testTag = "${screenTag}_error" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            if (onRetry != null) {
                AppTextButton(
                    text = "Retry",
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 12.dp).semantics { testTag = "${screenTag}_retry" },
                )
            }
        }
    }
}
