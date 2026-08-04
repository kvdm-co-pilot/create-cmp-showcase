package com.kvdm.fuelled.inspector

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kvdm.fuelled.presentation.components.BaseScreen
import com.kvdm.fuelled.presentation.components.PlaceholderScreen
import com.kvdm.fuelled.presentation.navigation.AppShell
import com.kvdm.fuelled.presentation.navigation.appTabs
import com.kvdm.fuelled.presentation.foods.FoodDetailScreen
import com.kvdm.fuelled.presentation.foods.FoodsScreen
import com.kvdm.fuelled.presentation.meal.MealTrayScreen
import com.kvdm.fuelled.domain.model.ReminderMode
import com.kvdm.fuelled.presentation.mealplan.MealPlanDayScreen
import com.kvdm.fuelled.presentation.mealplan.MealTimesNotice
import com.kvdm.fuelled.presentation.mealplan.MealTimesScreen
import com.kvdm.fuelled.presentation.mealplan.toNotice
import com.kvdm.fuelled.presentation.today.sampleHighlightsEmpty
import com.kvdm.fuelled.presentation.mealplan.samplePlanEmpty
import com.kvdm.fuelled.presentation.mealplan.samplePlanTomorrow
import com.kvdm.fuelled.presentation.meal.TrayContents
import com.kvdm.fuelled.presentation.profile.ProfileScreen
import com.kvdm.fuelled.presentation.supplements.SupplementsScreen
import com.kvdm.fuelled.presentation.supplements.SupplementStackUi
import com.kvdm.fuelled.presentation.supplements.sampleSupplementStack
import com.kvdm.fuelled.presentation.today.TodayScreen
import com.kvdm.fuelled.presentation.progress.ProgressScreen
import com.kvdm.fuelled.presentation.builder.MealBuilderScreen
import com.kvdm.fuelled.presentation.builder.BuilderUi
import com.kvdm.fuelled.presentation.builder.sampleCatalog
import com.kvdm.fuelled.presentation.builder.sampleBuilderWeek
import com.kvdm.fuelled.domain.model.BflCategory
import com.kvdm.fuelled.domain.model.ComposedMeal
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.presentation.progress.ProgressUi
import com.kvdm.fuelled.presentation.settings.SettingsScreen
import com.kvdm.fuelled.presentation.settings.SettingsUi
import com.kvdm.fuelled.domain.model.AppSettings
import com.kvdm.fuelled.domain.model.UnitSystem
import com.kvdm.fuelled.domain.model.WeightLog
import com.kvdm.fuelled.presentation.onboarding.OnboardingScreen
import com.kvdm.fuelled.presentation.foods.FoodEditorScreen

/**
 * One previewable screen: a stable [id] (the `-Pscreen=` selector and output directory
 * name), a human [title] for the gallery, and the composable [content] exactly as the
 * app hosts it.
 *
 * The `@Preview` analog for the create-cmp inspector: the registry makes "render screen
 * X" a closed, enumerable operation. The scaffolder regenerates the tab entries from the
 * configured `tabs`, and the feature stamper (`qa/scaffold-feature.mjs`, via the
 * `add-feature`/`add-screen` skills) auto-appends a stamped screen at the
 * `// cmp:anchor preview-registry` marker below; when you add a screen by hand, add it
 * there too — the renderScreens harness, the gallery, and golden baselines pick it up by id.
 *
 * State variants (the Storybook "story" analog): a screen in a specific UI state is just
 * another entry with a derived id — e.g. `ScreenPreview("home@empty", "Home — empty")`
 * hosting the screen with that state forced (a state-first overload of the screen, or
 * preview-only fakes behind its usual parameters). Every entry renders the same way
 * (gallery card, `-Pscreen=` selector, golden baseline), so loading/empty/error states
 * sit side by side with the default seeded state.
 *
 * Component stories (`component.<kebab-name>` ids, ComponentStories.kt) are appended
 * below — one isolated render per `presentation/components` composable. The console
 * keeps them out of the Screens grid and shows each at the top of its Components-page
 * entry; the verify lane's `componentStories` step enforces one story per component.
 */
data class ScreenPreview(
    val id: String,
    val title: String,
    val content: @Composable () -> Unit,
)

/** Every registered screen, in gallery order. Ids must be unique and filesystem-safe. */
fun previewRegistry(): List<ScreenPreview> = listOf(
    ScreenPreview("shell", "App shell — bottom nav (first tab selected)") {
        AppShell(
            tabs = appTabs(
                today = { TodayScreen() },
                foods = { FoodsScreen() },
                supplements = { SupplementsScreen() },
                profile = { ProfileScreen() },
            ),
        )
    },
    ScreenPreview("today", "Today tab — highlights (lunch focused, late)") { TabHost { TodayScreen() } },
    ScreenPreview("today@empty", "Today — fresh day (breakfast focused, add-in-card)") {
        TabHost { TodayScreen(sampleHighlightsEmpty) }
    },
    ScreenPreview("foods", "Foods tab") { TabHost { FoodsScreen() } },
    ScreenPreview("food-detail", "Food detail") { TabHost { FoodDetailScreen() } },
    // JRN-01/HIST-01: Progress — the holistic results surface (verdict, trend, weight, days).
    ScreenPreview("progress", "Progress — verdict, 4-week trend, weight, days") { TabHost { ProgressScreen() } },
    // START-01: the app's first words.
    ScreenPreview("onboarding", "First run — the three answers") { OnboardingScreen() },
    // CAT-01: your own catalog entries.
    ScreenPreview("food-editor", "New food — custom catalog entry") { TabHost { FoodEditorScreen() } },
    // UX-03: the catalog-first log with its slot picker open — the aim step, rendered.
    ScreenPreview("food-detail@log", "Food detail — slot picker open (UX-03)") {
        TabHost { FoodDetailScreen(logPickerInitiallyOpen = true) }
    },
    ScreenPreview("supplements", "Supplements tab") { TabHost { SupplementsScreen() } },
    ScreenPreview("profile", "Profile tab") { TabHost { ProfileScreen() } },
    ScreenPreview("meal-tray", "Meal tray — add to Lunch (3 ticked)") { TabHost { MealTrayScreen() } },
    ScreenPreview("meal-tray@empty", "Meal tray — empty selection") {
        TabHost { MealTrayScreen(tray = TrayContents()) }
    },
    // The structured day + the set-once times sheet. Stateless and fixture-driven, so every
    // state renders here without a device, a clock, or a database.
    ScreenPreview("meal-plan", "Meal plan — mid-day (lunch focused, late)") { TabHost { MealPlanDayScreen() } },
    ScreenPreview("meal-plan@empty", "Meal plan — fresh day (containers always render)") { TabHost { MealPlanDayScreen(samplePlanEmpty) } },
    ScreenPreview("meal-plan@planned", "Meal plan — tomorrow, planned ahead") { TabHost { MealPlanDayScreen(samplePlanTomorrow) } },
    // ENTRY-03: the disclosed state. The collapsed row is the default everywhere else, so this
    // is the only render that shows what the tap reveals — without it the editor would be a
    // control no gallery, golden tree or human review ever sees.
    ScreenPreview("meal-plan@editing", "Meal plan — one row's editor open (ENTRY-03)") {
        TabHost { MealPlanDayScreen(initialExpandedEntryId = "s-l1") }
    },
    ScreenPreview("meal-times", "Meal times — set-once alarms") { TabHost { MealTimesScreen() } },
    ScreenPreview("meal-times@no-reminders", "Meal times — notifications denied (PLAN-07/NOTIF-03)") {
        TabHost {
            // Derived through the same mapping the route uses — a hand-built notice here once
            // hid NOTIF-03's settings tap-through from every render of this state.
            MealTimesScreen(
                notice = ReminderMode.UNAVAILABLE.toNotice(),
            )
        }
    },
    // HIST-07: the state a real user has for their first month — no chart, no zero, no empty
    // axes. Registered because an empty state nobody renders is an empty state nobody reviews.
    ScreenPreview("progress@no-weight", "Progress — no weigh-ins yet (HIST-07)") {
        TabHost { ProgressScreen(ProgressUi(weight = WeightLog(emptyList()))) }
    },
    // SET-01..08: units, the stack, the lead.
    ScreenPreview("settings", "Settings — units, stack, reminder lead") { TabHost { SettingsScreen() } },
    // SET-04: the add/edit form, disclosed. Same composable for both, so one render covers it.
    ScreenPreview("settings@imperial", "Settings — imperial units (SET-02)") {
        TabHost { SettingsScreen(ui = SettingsUi(settings = AppSettings(unitSystem = UnitSystem.IMPERIAL, prepLeadMinutes = 60))) }
    },
    // BFL-05..08: the builder, empty and mid-compose. Two renders because the interesting
    // state is the SECOND one — an empty builder shows the vocabulary, a filled one shows
    // what the vocabulary is for.
    ScreenPreview("builder", "Meal builder — nothing picked yet") { TabHost { MealBuilderScreen() } },
    ScreenPreview("builder@composed", "Meal builder — chicken, rice & broccoli, all 7 days") {
        TabHost {
            MealBuilderScreen(
                ui = BuilderUi(
                    meal = ComposedMeal(
                        protein = sampleCatalog.getValue(BflCategory.PROTEIN)[0],
                        carb = sampleCatalog.getValue(BflCategory.CARB)[0],
                        vegetable = sampleCatalog.getValue(BflCategory.VEGETABLE)[0],
                    ),
                    slot = MealSlot.LUNCH,
                    days = sampleBuilderWeek.toSet(),
                ),
            )
        }
    },
    // SUPP-09: the off-day half of the split. The default `supplements` render already
    // carries a resting row, so this variant exists for the OTHER shape — a day where the
    // resting list is what there is to see, and nothing is due at all.
    ScreenPreview("supplements@none-due", "Supplements — nothing due today (SUPP-09)") {
        TabHost {
            SupplementsScreen(
                stack = SupplementStackUi(
                    groups = emptyList(),
                    takenCount = 0,
                    total = 0,
                    resting = sampleSupplementStack.resting,
                    today = sampleSupplementStack.today,
                ),
            )
        }
    },
    // cmp:anchor preview-registry
) + componentStories() + placeholderScreenStories()

/**
 * Hosts a single tab's content the way [AppShell] does — inside [BaseScreen] — minus the
 * bottom bar, so a tab previews with the same insets/background it gets in the shell.
 */
@Composable
private fun TabHost(content: @Composable () -> Unit) {
    BaseScreen {
        Box(Modifier.fillMaxSize()) { content() }
    }
}

/** Component story for the generated [PlaceholderScreen] — see ComponentStories.kt for the convention. */
private fun placeholderScreenStories(): List<ScreenPreview> = listOf(
    ScreenPreview("component.placeholder-screen", "PlaceholderScreen — component story") {
        StoryHost { PlaceholderScreen(title = "Placeholder", titleTag = "story_title") }
    },
)
