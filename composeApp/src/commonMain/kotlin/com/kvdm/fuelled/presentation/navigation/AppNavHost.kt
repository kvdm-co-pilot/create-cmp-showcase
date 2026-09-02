package com.kvdm.fuelled.presentation.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import com.kvdm.fuelled.presentation.components.LocalNavAnimatedVisibilityScope
import com.kvdm.fuelled.presentation.theme.FuelledMotion
import com.kvdm.fuelled.presentation.theme.LocalMotion
import com.kvdm.fuelled.presentation.theme.moves
import com.kvdm.fuelled.presentation.theme.tween
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
import com.kvdm.fuelled.presentation.appupdates.UpdateRoute
import com.kvdm.fuelled.presentation.today.TodayRoute
import com.kvdm.fuelled.presentation.workouts.WorkoutWeekRoute
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val motion = LocalMotion.current
    val density = LocalDensity.current
    // Motion D6 (MOTION-04): one transition set for the whole graph, declared here and
    // inherited by every destination — the same reasoning that put the automation exposure on
    // the host. A push slides in ScreenSlide from the trailing edge with a fade on
    // Emphasized/Enter while the outgoing screen fades on Quick/Exit leading by ScreenLead;
    // a pop mirrors both. Under Reduced the slides are zero and only the fades remain.
    val slide = with(density) { if (motion.moves) FuelledMotion.ScreenSlide.roundToPx() else 0 }
    val lead = with(density) { if (motion.moves) FuelledMotion.ScreenLead.roundToPx() else 0 }
    val pushEnter = fadeIn(motion.tween(FuelledMotion.Duration.Emphasized, FuelledMotion.Easings.Enter)) +
        slideInHorizontally(motion.tween(FuelledMotion.Duration.Emphasized, FuelledMotion.Easings.Enter)) { slide }
    val pushExit = fadeOut(motion.tween(FuelledMotion.Duration.Quick, FuelledMotion.Easings.Exit)) +
        slideOutHorizontally(motion.tween(FuelledMotion.Duration.Quick, FuelledMotion.Easings.Exit)) { -lead }
    val popEnter = fadeIn(motion.tween(FuelledMotion.Duration.Emphasized, FuelledMotion.Easings.Enter)) +
        slideInHorizontally(motion.tween(FuelledMotion.Duration.Emphasized, FuelledMotion.Easings.Enter)) { -lead }
    val popExit = fadeOut(motion.tween(FuelledMotion.Duration.Quick, FuelledMotion.Easings.Exit)) +
        slideOutHorizontally(motion.tween(FuelledMotion.Duration.Quick, FuelledMotion.Easings.Exit)) { slide }

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
    // OD5 (FOODS-09): `App` hosts the SharedTransitionLayout (it has to span the intro gate
    // too, MOTION-13); each destination here publishes its own visibility scope, so a row's
    // title can travel into the header of the screen it opens.
    NavHost(
        navController = navController,
        startDestination = Screen.Shell.route,
        modifier = Modifier.exposeTestTagsForAutomation(),
        enterTransition = { pushEnter },
        exitTransition = { pushExit },
        popEnterTransition = { popEnter },
        popExitTransition = { popExit },
    ) {
        composable(Screen.Shell.route) {
          CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
            // NAV-01: five tabs, and the shell keeps its own selection again. The
            // selectedIndex/onSelectTab hoisting lived here for exactly one caller — Today's
            // supplement highlight moving the user to the Supplements TAB — and NAV-05 turned
            // that into an ordinary push, so the parameters have no caller left.
            val tabs = appTabs(
                today = {
                    TodayRoute(
                        // TODAY-07: the tap carries its own target into the route.
                        onAddToMeal = { date, slot -> navController.navigate(Routes.mealTray(date, slot)) },
                        // TODAY-11: Today summarizes the stack; editing it is the Supplements
                        // screen's job, now a pushed destination rather than a tab (NAV-05), so
                        // system-back returns here instead of stranding the user on a tab they
                        // did not choose.
                        onOpenSupplements = { navController.navigate(Routes.SUPPLEMENTS) },
                    )
                },
                // NAV-02: the week is a tab, not a link below Today's fold. It hosts the plan
                // screen with NO date argument — a tab has none to carry — so the ViewModel
                // anchors on the current logical day and re-anchors across the 04:00 boundary.
                week = {
                    MealPlanRoute(
                        onBuildMeal = { navController.navigate(Routes.MEAL_BUILDER) },
                        // PLAN-19 (motion D17): the review, one tap from the week you plan.
                        onOpenReview = { navController.navigate(Routes.PROGRESS) },
                        onAddToMeal = { d, slot -> navController.navigate(Routes.mealTray(d, slot)) },
                        onOpenTimes = { navController.navigate(Routes.MEAL_TIMES) },
                        // SHELL-05: AppShell already owns this tab's insets.
                        ownsInsets = false,
                    )
                },
                meals = {
                    FoodsRoute(
                        onFoodClick = { navController.navigate(Routes.foodDetail(it.id)) },
                        // CAT-01: the catalog tab's own job — your custom entries.
                        onAddFood = { navController.navigate(Routes.foodEditor()) },
                        // CAT-04 (motion D15): the builder's door on the tab called Meals.
                        onBuildMeal = { navController.navigate(Routes.MEAL_BUILDER) },
                    )
                },
                // NAV-06: the training week gets a home. The tick stays on Today's card
                // (WORK-03) — this tab answers "what does my week look like", which is the
                // question that had no surface at all.
                training = {
                    WorkoutWeekRoute(onEditWeek = { navController.navigate(Routes.SETTINGS) })
                },
                profile = {
                    ProfileRoute(
                        onOpenWeek = { navController.navigate(Routes.PROGRESS) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                },
            )
            AppShell(tabs = tabs)
          }
        }

        composable(
            route = Screen.FoodDetail.route,
            arguments = listOf(navArgument("foodId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val foodId = backStackEntry.arguments?.read { getStringOrNull("foodId") }.orEmpty()
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                FoodDetailRoute(
                    foodId = foodId,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.foodEditor(it)) },
                )
            }
        }

        // The add-to-meal tray, ALREADY TARGETED (TODAY-07/TODAY-08). The target rides the
        // route as an ISO logical date + the slot's enum name; it is handed to the ViewModel as
        // a Koin parameter, so the tray's first frame is already aimed. Absent or malformed
        // arguments resolve to null, and the destination goes back rather than opening a
        // mis-aimed tray or throwing at the nav layer. The `*Destination` entry point owns the
        // insets (SHELL-05, harness 0.14: the wrapper lives in the destination's own file now).
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
                MealTrayRoute(
                    viewModel = koinViewModel { parametersOf(initialTarget) },
                    // MEAL-13: a confirmed add returns to the container it came from.
                    onAdded = { navController.popBackStack() },
                )
            }
        }
        // The structured day (PLAN-11). The date rides the route so a link into a specific day
        // arrives showing it and the back stack remembers which one. A malformed date pops
        // back rather than opening a day that does not exist — same rule as the tray.
        // MealPlanRoute, not MealPlanRoute: the Week TAB hosts the bare route (insets from
        // AppShell) while this dated entry owns its own — BaseScreen does not nest safely.
        composable(
            route = Screen.MealPlan.route,
            arguments = listOf(navArgument("date") { type = NavType.StringType }),
        ) { backStackEntry ->
            val date = Routes.mealPlanDate(backStackEntry.arguments?.read { getStringOrNull("date") })
            if (date == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                MealPlanRoute(
                    onBuildMeal = { navController.navigate(Routes.MEAL_BUILDER) },
                    onOpenReview = { navController.navigate(Routes.PROGRESS) },
                    // PLAN-24: the route's date SEEDS the ViewModel and is never re-applied
                    // — same shape as the tray's target above, and for the same reason.
                    viewModel = koinViewModel { parametersOf(date) },
                    // PLAN-04: the tap carries this container's day and slot into the tray.
                    onAddToMeal = { d, slot -> navController.navigate(Routes.mealTray(d, slot)) },
                    onOpenTimes = { navController.navigate(Routes.MEAL_TIMES) },
                )
            }
        }

        // The set-once meal-times sheet (PLAN-05/PLAN-06/PLAN-07). A literal route, matched
        // ahead of `plan/{date}`'s pattern.
        composable(Screen.MealTimes.route) {
            MealTimesRoute(onBack = { navController.popBackStack() })
        }
        // Progress (JRN-01/JRN-02, HIST-01) — the holistic look back; entered from Profile's
        // stats row. The `*Destination` entry point owns its insets (SHELL-05).
        composable(Screen.Progress.route) {
            ProgressRoute(
                onBack = { navController.popBackStack() },
                // HIST-02: the day card is a door. It opens the PLAN for that day rather
                // than a read-only viewer — the reason you open Sunday is usually to fix
                // it, and the plan screen already edits any date.
                onOpenDay = { date -> navController.navigate(Routes.mealPlan(date)) },
            )
        }
        // BFL-05: the meal builder — a week planned in a handful of taps, entered from the
        // plan screen and from Today.
        composable(Screen.MealBuilder.route) {
            MealBuilderRoute(onBack = { navController.popBackStack() })
        }
        // SET-01: the settings UX-04 stopped pretending about, entered from Profile.
        composable(Screen.Settings.route) {
            SettingsRoute(
                onBack = { navController.popBackStack() },
                onOpenUpdates = { navController.navigate(Routes.UPDATES) },
            )
        }
        // CAT-01: the custom-food editor. `new` mints an id here rather than in the ViewModel,
        // so a rotation mid-typing keeps the same identity and cannot create a twin on save.
        composable(
            route = Screen.FoodEditor.route,
            arguments = listOf(navArgument("foodId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val arg = backStackEntry.arguments?.read { getStringOrNull("foodId") }.orEmpty()
            val editing = if (arg == Routes.NEW_FOOD) "" else arg
            FoodEditorRoute(
                foodId = editing,
                newId = "custom-" + backStackEntry.id,
                onDone = { navController.popBackStack() },
            )
        }
        // NAV-05: Supplements comes off the bar and becomes a pushed destination, entered
        // from Today's highlight (TODAY-11) and from Profile. It owns its insets now that it is
        // a destination rather than a tab (SHELL-05) — which is exactly what NAV-05 changed.
        composable(Screen.Supplements.route) {
            BaseScreen {
                // SUPP-14 (motion D16): the stack's editor stays a Settings card; this is its
                // second door, from the screen that shows the doses it defines.
                SupplementsRoute(onEditStack = { navController.navigate(Routes.SETTINGS) })
            }
        }
        // UPD-09: the update surface. UpdateRoute owns its insets (SHELL-05).
        composable(Screen.Updates.route) {
            UpdateRoute(onBack = { navController.popBackStack() })
        }
        // cmp:anchor nav-destinations
    }
}
