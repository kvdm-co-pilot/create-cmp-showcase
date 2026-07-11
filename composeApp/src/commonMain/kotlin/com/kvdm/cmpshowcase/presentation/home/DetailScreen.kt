package com.kvdm.cmpshowcase.presentation.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.kvdm.cmpshowcase.presentation.components.BaseScreen
import com.kvdm.cmpshowcase.presentation.theme.CMPShowcaseTokens
import com.kvdm.cmpshowcase.presentation.theme.designToken

@Composable
fun DetailScreen(
    itemId: String,
    onBack: () -> Unit,
) {
    BaseScreen {
        Column(
            Modifier
                .fillMaxSize()
                .designToken(
                    tokens = listOf("PaddingPage"),
                    resolved = mapOf("padding" to "16dp"),
                )
                .padding(CMPShowcaseTokens.PaddingPage),
        ) {
            TextButton(onClick = onBack, modifier = Modifier.semantics { testTag = "detail_back" }) {
                Text("← Back")
            }
            Text(
                "Detail",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { testTag = "detail_title" },
            )
            Text(
                "Item id: $itemId",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
