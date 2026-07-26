package com.kvdm.fuelled.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
// Nav 2.9 (multiplatform): backStackEntry.arguments is a SavedState, not an Android Bundle.
// Read it via the androidx.savedstate.read extension, NOT Bundle.getString().
import androidx.savedstate.read
import com.kvdm.fuelled.presentation.components.BaseScreen
import com.kvdm.fuelled.presentation.components.exposeTestTagsForAutomation
import com.kvdm.fuelled.presentation.foods.FoodDetailRoute
import com.kvdm.fuelled.presentation.foods.FoodsRoute
import com.kvdm.fuelled.presentation.meal.MealTrayRoute
import com.kvdm.fuelled.presentation.profile.ProfileRoute
import com.kvdm.fuelled.presentation.supplements.SupplementsRoute
import com.kvdm.fuelled.presentation.today.TodayRoute
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    // Report every back-stack change to the common inspection seam — a no-op unless the
    // androidDebug inspector registered a listener (see NavInspectionHook.kt). Best-effort:
    // `currentBackStack` is a live snapshot, not a durable history.
    LaunchedEffect(navController) {
        // The jump half of the same seam: lets the debug inspector navigate by route
        // (its navigate endpoint) so walkthrough coverage enumerates the graph instead of
        // synthesizing taps. Registered/cleared with the controller's composition lifetime;
        // NavController rejects unknown routes itself (IllegalArgumentException).
        NavInspectionHook.navigator = { route -> navController.navigate(route) }
        try {
            navController.currentBackStack.collect { stack ->
                NavInspectionHook.listener?.invoke(
                    navController.currentDestination?.route,
                    stack.mapNotNull { it.destination.route },
                )
            }
        } finally {
            NavInspectionHook.navigator = null
        }
    }

    // Expose Compose testTags to the platform automation layer (Android resource-ids / iOS
    // accessibilityIdentifiers) for the WHOLE graph. The property is inherited by descendants,
    // so it belongs on the graph root, not on a destination: it used to sit inside AppShell,
    // which made it cover the tabs and nothing else — every destination registered directly
    // here (food detail, the meal tray) had testTags that no id-selector could see, so Maestro
    // could not assert arrival on them at all. Applied here, a destination added later inherits
    // it without anyone remembering to. Desktop: no-op.
    NavHost(
        navController = navController,
        startDestination = Screen.Shell.route,
        modifier = Modifier.exposeTestTagsForAutomation(),
    ) {
        composable(Screen.Shell.route) {
            val tabs = appTabs(
                today = {
                    TodayRoute(
                        // TODAY-07/TODAY-08: the tap carries its own target into the route.
                        onAddToMeal = { date, slot -> navController.navigate(Routes.mealTray(date, slot)) },
                    )
                },
                foods = { FoodsRoute(onFoodClick = { navController.navigate(Routes.foodDetail(it.id)) }) },
                supplements = { SupplementsRoute() },
                profile = { ProfileRoute() },
            )
            AppShell(tabs = tabs)
        }

        composable(
            route = Screen.FoodDetail.route,
            arguments = listOf(navArgument("foodId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val foodId = backStackEntry.arguments?.read { getStringOrNull("foodId") }.orEmpty()
            FoodDetailRoute(foodId = foodId, onBack = { navController.popBackStack() })
        }

        // The add-to-meal tray, ALREADY TARGETED (TODAY-07/TODAY-08). The target rides the
        // route as an ISO logical date + the slot's enum name; it is handed to the ViewModel as
        // a Koin parameter, so the tray's first frame is already aimed. Absent or malformed
        // arguments resolve to null — the ViewModel's own clock-derived opening target — rather
        // than throwing at the nav layer. BaseScreen wraps it because a destination registered
        // directly on the NavHost owns its insets (SHELL-05); the tabs get theirs from AppShell.
        composable(
            route = Screen.MealTray.route,
            arguments = listOf(
                navArgument("date") { type = NavType.StringType },
                navArgument("slot") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            val initialTarget = Routes.mealTrayTarget(
                date = args?.read { getStringOrNull("date") },
                slot = args?.read { getStringOrNull("slot") },
            )
            BaseScreen {
                MealTrayRoute(viewModel = koinViewModel { parametersOf(initialTarget) })
            }
        }
        // cmp:anchor nav-destinations
    }
}
