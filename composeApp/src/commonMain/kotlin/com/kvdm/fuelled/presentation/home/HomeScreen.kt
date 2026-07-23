package com.kvdm.fuelled.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvdm.fuelled.presentation.components.AppHeader
import com.kvdm.fuelled.presentation.components.ContentStateContainer
import com.kvdm.fuelled.presentation.components.ListItemCard
import com.kvdm.fuelled.presentation.components.ScreenColumn
import com.kvdm.fuelled.presentation.theme.FuelledTokens
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    onItemClick: (String) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ScreenColumn(screenTag = "home") {
        AppHeader(title = "Home", screenTag = "home")
        ContentStateContainer(state = state, screenTag = "home", onRetry = viewModel::load) { items ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(FuelledTokens.GapCard)) {
                items(items, key = { it.id }) { item ->
                    ListItemCard(
                        title = item.title,
                        subtitle = item.subtitle,
                        onClick = { onItemClick(item.id) },
                        modifier = Modifier.semantics { testTag = "home_item_${item.id}" },
                    )
                }
            }
        }
    }
}
