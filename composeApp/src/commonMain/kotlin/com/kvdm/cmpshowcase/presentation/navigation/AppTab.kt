package com.kvdm.cmpshowcase.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
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
    home: @Composable () -> Unit,
    profile: @Composable () -> Unit,
): List<AppTab> = listOf(
    AppTab("Home", Icons.Filled.Home, home),
    AppTab("Profile", Icons.Filled.Person, profile),
)
