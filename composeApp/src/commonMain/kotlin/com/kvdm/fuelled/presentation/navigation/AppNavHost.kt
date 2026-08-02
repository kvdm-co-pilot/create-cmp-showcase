package com.kvdm.fuelled.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
// Nav 2.9 (multiplatform): backStackEntry.arguments is a SavedState, not an Android Bundle.
// Read it via the androidx.savedstate.read extension, NOT Bundle.getString().
import androidx.savedstate.read
import com.kvdm.fuelled.core.time.TimeSignal
import com.kvdm.fuelled.presentation.components.BaseScreen
import org.koin.compose.koinInject
import com.kvdm.fuelled.presentation.components.exposeTestTagsForAutomation
import com.kvdm.fuelled.presentation.foods.FoodDetailRoute
import com.kvdm.fuelled.presentation.foods.FoodEditorRoute
import com.kvdm.fuelled.presentation.foods.FoodsRoute
import com.kvdm.fuelled.presentation.meal.MealTrayRoute
import com.kvdm.fuelled.presentation.mealplan.MealPlanRoute
import com.kvdm.fuelled.presentation.mealplan.MealTimesRoute
import com.kvdm.fuelled.presentation.progress.ProgressRoute
import com.kvdm.fuelled.presentation.settings.SettingsRoute
import com.kvdm.fuelled.presentation.builder.MealBuilderRoute
import com.kvdm.fuelled.presentation.profile.ProfileRoute
import com.kvdm.fuelled.presentation.supplements.SupplementsRoute
import com.kvdm.fuelled.presentation.today.TodayRoute
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The Supplements tab's index in [appTabs] — Today's supplement highlight opens it (TODAY-11). */
private const val SUPPLEMENTS_TAB = 2

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    // The other half of the day-rollover fix, at the app root rather than inside AppShell —
    // the shell is a generic presentation component and must stay constructible without a DI
    // container (its screen tests build it directly).
    //
    // TimeSignal's minute ticker is a coroutine timer, and coroutine timers do not run
    // dependably while the device sleeps — Doze parks them — which is exactly why an app left
    // open overnight woke up still showing yesterday. Returning to the foreground is the moment
    // to say "time may have jumped"; everything derived from the clock re-derives from this.
    val time: TimeSignal = koinInject()
    LifecycleResumeEffect(Unit) {
        time.wake()
        onPauseOrDispose { }
    }

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
            // Hoisted so Today's supplement highlight can move the user to that TAB (TODAY-11)
            // — a tab switch, not a pushed destination, because the Supplements tab is where
            // the stack is actually edited.
            var selectedTab by rememberSaveable { mutableIntStateOf(0) }
            val tabs = appTabs(
                today = {
                    TodayRoute(
                        // TODAY-07: the tap carries its own target into the route.
                        onAddToMeal = { date, slot -> navController.navigate(Routes.mealTray(date, slot)) },
                        // TODAY-12: the one control into the full week, opened at the current
                        // logical day — planning is one tap from the dashboard.
                        onOpenPlan = { date -> navController.navigate(Routes.mealPlan(date)) },
                        // TODAY-11: Today summarizes the stack; editing it is the Supplements
                        // tab's job, so this switches tabs rather than opening an editor here.
                        onOpenSupplements = { selectedTab = SUPPLEMENTS_TAB },
                    )
                },
                foods = {
                    FoodsRoute(
                        onFoodClick = { navController.navigate(Routes.foodDetail(it.id)) },
                        // CAT-01: the Foods tab's new job — your own catalog entries.
                        onAddFood = { navController.navigate(Routes.foodEditor()) },
                    )
                },
                supplements = { SupplementsRoute() },
                profile = {
                    ProfileRoute(
                        onOpenWeek = { navController.navigate(Routes.PROGRESS) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                },
            )
            AppShell(tabs = tabs, selectedIndex = selectedTab, onSelectTab = { selectedTab = it })
        }

        composable(
            route = Screen.FoodDetail.route,
            arguments = listOf(navArgument("foodId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val foodId = backStackEntry.arguments?.read { getStringOrNull("foodId") }.orEmpty()
            FoodDetailRoute(
                foodId = foodId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.foodEditor(it)) },
            )
        }

        // The add-to-meal tray, ALREADY TARGETED (TODAY-07/TODAY-08). The target rides the
        // route as an ISO logical date + the slot's enum name; it is handed to the ViewModel as
        // a Koin parameter, so the tray's first frame is already aimed. Absent or malformed
        // arguments resolve to null, and the destination goes back rather than opening a
        // mis-aimed tray or throwing at the nav layer. BaseScreen wraps it because a destination registered
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
            if (initialTarget == null) {
                // A malformed route carries no target, and MEAL-10 left nothing to fall back
                // to — the tray can no longer guess a slot from the clock. Going back beats
                // opening a tray aimed at a meal the user never picked.
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                BaseScreen {
                    MealTrayRoute(
                        viewModel = koinViewModel { parametersOf(initialTarget) },
                        // MEAL-13: a confirmed add returns to the container it came from.
                        onAdded = { navController.popBackStack() },
                    )
                }
            }
        }
        // The structured day (PLAN-11). The date rides the route so a link into a specific day
        // arrives showing it and the back stack remembers which one. A malformed date pops
        // back rather than opening a day that does not exist — same rule as the tray.
        // BaseScreen because a destination registered directly on the NavHost owns its insets
        // (SHELL-05); the tabs get theirs from AppShell.
        composable(
            route = Screen.MealPlan.route,
            arguments = listOf(navArgument("date") { type = NavType.StringType }),
        ) { backStackEntry ->
            val date = Routes.mealPlanDate(backStackEntry.arguments?.read { getStringOrNull("date") })
            if (date == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                BaseScreen {
                    MealPlanRoute(
                        onBuildMeal = { navController.navigate(Routes.MEAL_BUILDER) },
                        // PLAN-24: the route's date SEEDS the ViewModel and is never re-applied
                        // — same shape as the tray's target above, and for the same reason.
                        viewModel = koinViewModel { parametersOf(date) },
                        // PLAN-04: the tap carries this container's day and slot into the tray.
                        onAddToMeal = { d, slot -> navController.navigate(Routes.mealTray(d, slot)) },
                        onOpenTimes = { navController.navigate(Routes.MEAL_TIMES) },
                    )
                }
            }
        }

        // The set-once meal-times sheet (PLAN-05/PLAN-06/PLAN-07). A literal route, matched
        // ahead of `plan/{date}`'s pattern.
        composable(Screen.MealTimes.route) {
            BaseScreen {
                MealTimesRoute(onBack = { navController.popBackStack() })
            }
        }
        // Progress (JRN-01/JRN-02, HIST-01) — the holistic look back; entered from Profile's
        // stats row. BaseScreen: a destination registered directly on the NavHost owns its
        // insets (SHELL-05).
        composable(Screen.Progress.route) {
            BaseScreen {
                ProgressRoute(
                    onBack = { navController.popBackStack() },
                    // HIST-02: the day card is a door. It opens the PLAN for that day rather
                    // than a read-only viewer — the reason you open Sunday is usually to fix
                    // it, and the plan screen already edits any date.
                    onOpenDay = { date -> navController.navigate(Routes.mealPlan(date)) },
                )
            }
        }
        // BFL-05: the meal builder — a week planned in a handful of taps, entered from the
        // plan screen and from Today.
        composable(Screen.MealBuilder.route) {
            BaseScreen {
                MealBuilderRoute(onBack = { navController.popBackStack() })
            }
        }
        // SET-01: the settings UX-04 stopped pretending about, entered from Profile.
        composable(Screen.Settings.route) {
            BaseScreen {
                SettingsRoute(onBack = { navController.popBackStack() })
            }
        }
        // CAT-01: the custom-food editor. `new` mints an id here rather than in the ViewModel,
        // so a rotation mid-typing keeps the same identity and cannot create a twin on save.
        composable(
            route = Screen.FoodEditor.route,
            arguments = listOf(navArgument("foodId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val arg = backStackEntry.arguments?.read { getStringOrNull("foodId") }.orEmpty()
            val editing = if (arg == Routes.NEW_FOOD) "" else arg
            BaseScreen {
                FoodEditorRoute(
                    foodId = editing,
                    newId = "custom-" + backStackEntry.id,
                    onDone = { navController.popBackStack() },
                )
            }
        }
        // cmp:anchor nav-destinations
    }
}
