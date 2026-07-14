package com.kvdm.cmpshowcase.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvdm.cmpshowcase.presentation.theme.CMPShowcaseTokens
import com.kvdm.cmpshowcase.presentation.theme.designToken
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    onItemClick: (String) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .designToken(
                tokens = listOf("PaddingPage"),
                resolved = mapOf("padding" to "16dp"),
            )
            .padding(CMPShowcaseTokens.PaddingPage),
    ) {
        Text(
            text = "Home",
            style = MaterialTheme.typography.headlineMedium,
            // Refusal demo: a hardcoded brand color instead of a theme token.
            // ARCH-05 forbids Color(0x…) literals outside presentation/theme — CI will name it.
            color = Color(0xFFE91E63),
            modifier = Modifier.semantics { testTag = "home_title" }.padding(bottom = 12.dp),
        )

        val errorMessage = state.errorMessage
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (errorMessage != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { testTag = "home_error" },
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(CMPShowcaseTokens.GapCard)) {
                items(state.items, key = { it.id }) { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = CMPShowcaseTokens.ElevationCard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .designToken(
                                tokens = listOf("RadiusCard", "ElevationCard", "PaddingCard"),
                                resolved = mapOf(
                                    "radius" to "16dp",
                                    "elevation" to "2dp",
                                    "padding" to "16dp",
                                    "color" to "#FFFFFFFF",
                                ),
                            )
                            .clickable { onItemClick(item.id) },
                    ) {
                        Column(Modifier.padding(CMPShowcaseTokens.PaddingCard)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                item.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
