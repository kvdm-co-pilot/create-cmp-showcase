package com.kvdm.cmpshowcase.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
// Nav 2.9 (multiplatform): backStackEntry.arguments is a SavedState, not an Android Bundle.
// Read it via the androidx.savedstate.read extension, NOT Bundle.getString().
import androidx.savedstate.read
import com.kvdm.cmpshowcase.presentation.home.HomeScreen
import com.kvdm.cmpshowcase.presentation.profile.ProfileScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Shell.route) {
        composable(Screen.Shell.route) {
            val tabs = appTabs(
                home = {
                    HomeScreen(
                        onItemClick = { itemId -> navController.navigate(Routes.detail(itemId)) },
                    )
                },
                profile = { ProfileScreen() },
            )
            AppShell(tabs = tabs)
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.read { getStringOrNull("itemId") }.orEmpty()
            com.kvdm.cmpshowcase.presentation.home.DetailScreen(
                itemId = itemId,
                onBack = { navController.popBackStack() },
            )
        }
        // cmp:anchor nav-destinations
    }
}
