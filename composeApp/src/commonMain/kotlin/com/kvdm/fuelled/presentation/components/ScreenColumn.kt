package com.kvdm.fuelled.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.kvdm.fuelled.presentation.theme.FuelledTokens
import com.kvdm.fuelled.presentation.theme.designToken

/**
 * The page container every screen roots itself in. Owns the tagged root
 * (`<screenTag>_screen`), the page padding from `PaddingPage`, and that token's
 * inspector self-report — the reported value is derived from the token itself, so it
 * cannot drift from the catalog. Screens never hand-roll their root column.
 *
 * @param screenTag Feature slug ("home"); the root is tagged `<screenTag>_screen`.
 * @param scrollable True wraps the column in a vertical scroll. Leave false when the
 *   content scrolls itself (a `LazyColumn`) — nesting the two crashes at runtime.
 * @param content Column body, laid out inside the page padding.
 */
@Composable
fun ScreenColumn(
    screenTag: String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .fillMaxSize()
        .semantics { testTag = "${screenTag}_screen" }
        .designToken(
            tokens = listOf("PaddingPage"),
            resolved = mapOf("padding" to "${FuelledTokens.PaddingPage.value.toInt()}dp"),
        )
        .padding(FuelledTokens.PaddingPage)

    Column(
        modifier = if (scrollable) base.verticalScroll(rememberScrollState()) else base,
        content = content,
    )
}
