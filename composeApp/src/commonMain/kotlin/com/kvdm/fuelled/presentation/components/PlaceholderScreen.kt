package com.kvdm.fuelled.presentation.components

import androidx.compose.foundation.layout.Arrangement
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
import com.kvdm.fuelled.presentation.theme.FuelledTokens

// Generated stub for a configured bottom-nav tab that has no feature yet.
// Build the real feature with the add-feature skill (qa/scaffold-feature.mjs),
// then swap this out in AppNavHost. The title testTag (`<slug>_title`) is what
// qa/e2e/smoke.yaml asserts for this tab — keep it when you replace the stub.
@Composable
fun PlaceholderScreen(title: String, titleTag: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(FuelledTokens.PaddingPage),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { testTag = titleTag },
        )
        Text(
            text = "This is a generated stub tab. Wire it up like the Home feature.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
