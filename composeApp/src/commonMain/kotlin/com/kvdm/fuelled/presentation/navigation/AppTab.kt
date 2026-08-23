package com.kvdm.fuelled.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
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

/**
 * The tab list drives AppShell + AppBottomNav generically (no role-hardcoded shells).
 *
 * NAV-01: five tabs, in the order of the day rather than the order of the data model —
 * Today (what is true now) → Week (what is planned) → Meals (what you eat from) →
 * Training (the sixth pillar) → Profile (you). Five is Material 3's ceiling for a
 * NavigationBar and this app has exactly five daily surfaces.
 *
 * Each label also derives its item's `nav_<slug>` testTag (AppBottomBar.navItemTag), so these
 * strings are automation ids as much as copy — renaming one renames an id the e2e flow selects
 * by. Full words, not abbreviations, for exactly that reason: `nav_training` outlives `nav_train`.
 *
 * Supplements is deliberately NOT here (NAV-05). Its real entry point is Today's highlight
 * (TODAY-11), which is the whole question — "2 of 4 taken" — answered without navigating.
 */
@Composable
fun appTabs(
    today: @Composable () -> Unit,
    week: @Composable () -> Unit,
    meals: @Composable () -> Unit,
    training: @Composable () -> Unit,
    profile: @Composable () -> Unit,
): List<AppTab> = listOf(
    AppTab("Today", Icons.Filled.Today, today),
    AppTab("Week", Icons.Filled.CalendarMonth, week),
    AppTab("Meals", Icons.Filled.Restaurant, meals),
    AppTab("Training", Icons.Filled.FitnessCenter, training),
    AppTab("Profile", Icons.Filled.Person, profile),
)
