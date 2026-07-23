package com.kvdm.fuelled.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Today
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

// A single bottom-nav tab: its label, icon, and the screen it renders.
data class AppTab(
    val label: String,
    val icon: ImageVector,
    val content: @Composable () -> Unit,
)

// The tab list drives AppShell + AppBottomNav generically (no role-hardcoded shells).
// The scaffolder regenerates this list from the configured `tabs`.
@Composable
fun appTabs(
    today: @Composable () -> Unit,
    foods: @Composable () -> Unit,
    supplements: @Composable () -> Unit,
    profile: @Composable () -> Unit,
): List<AppTab> = listOf(
    AppTab("Today", Icons.Filled.Today, today),
    AppTab("Foods", Icons.Filled.Restaurant, foods),
    AppTab("Supplements", Icons.Filled.Medication, supplements),
    AppTab("Profile", Icons.Filled.Person, profile),
)
