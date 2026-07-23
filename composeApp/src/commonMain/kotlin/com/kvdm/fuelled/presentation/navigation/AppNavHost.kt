package com.kvdm.fuelled.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
// Nav 2.9 (multiplatform): backStackEntry.arguments is a SavedState, not an Android Bundle.
// Read it via the androidx.savedstate.read extension, NOT Bundle.getString().
import androidx.savedstate.read
import com.kvdm.fuelled.presentation.foods.FoodDetailScreen
import com.kvdm.fuelled.presentation.foods.FoodsScreen
import com.kvdm.fuelled.presentation.foods.sampleFoods
import com.kvdm.fuelled.presentation.profile.ProfileScreen
import com.kvdm.fuelled.presentation.supplements.SupplementsScreen
import com.kvdm.fuelled.presentation.today.TodayScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    // Report every back-stack change to the common inspection seam — a no-op unless the
    // androidDebug inspector registered a listener (see NavInspectionHook.kt). Best-effort:
    // `currentBackStack` is a live snapshot, not a durable history.
    LaunchedEffect(navController) {
        navController.currentBackStack.collect { stack ->
            NavInspectionHook.listener?.invoke(
                navController.currentDestination?.route,
                stack.mapNotNull { it.destination.route },
            )
        }
    }

    NavHost(navController = navController, startDestination = Screen.Shell.route) {
        composable(Screen.Shell.route) {
            val tabs = appTabs(
                today = { TodayScreen() },
                foods = { FoodsScreen(onFoodClick = { navController.navigate(Routes.foodDetail(it.id)) }) },
                supplements = { SupplementsScreen() },
                profile = { ProfileScreen() },
            )
            AppShell(tabs = tabs)
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.read { getStringOrNull("itemId") }.orEmpty()
            com.kvdm.fuelled.presentation.home.DetailScreen(
                itemId = itemId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Screen.FoodDetail.route,
            arguments = listOf(navArgument("foodId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val foodId = backStackEntry.arguments?.read { getStringOrNull("foodId") }.orEmpty()
            val food = sampleFoods.firstOrNull { it.id == foodId } ?: sampleFoods.first()
            FoodDetailScreen(food = food, onBack = { navController.popBackStack() })
        }
        // cmp:anchor nav-destinations
    }
}
