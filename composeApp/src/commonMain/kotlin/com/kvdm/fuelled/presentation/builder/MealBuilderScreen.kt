package com.kvdm.fuelled.presentation.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvdm.fuelled.domain.model.BflCategory
import com.kvdm.fuelled.domain.model.ComposedMeal
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.model.Macros100g
import com.kvdm.fuelled.domain.model.MEAL_PRESETS
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.presentation.components.BaseScreen
import com.kvdm.fuelled.presentation.components.AppButtonDefaults
import com.kvdm.fuelled.presentation.components.AppHeader
import com.kvdm.fuelled.presentation.components.AppPrimaryButton
import com.kvdm.fuelled.presentation.components.AppTextButton
import com.kvdm.fuelled.presentation.components.ScreenColumn
import com.kvdm.fuelled.presentation.components.Tag
import com.kvdm.fuelled.presentation.theme.FuelledColors
import com.kvdm.fuelled.presentation.theme.FuelledTokens
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

// ── Meal builder: a week planned in a handful of taps (BFL-05..08) ───────────────────────
// Body-for-LIFE is six meals a day, seven days — 42 meals. Nobody fills that in food by food.
// The method itself says how: a portion of protein, a portion of carbohydrate, vegetables
// with two of them. This screen is that grammar made tappable.

/** Everything the surface renders — one shape, so the registry renders it with no ViewModel. */
data class BuilderUi(
    val catalog: Map<BflCategory, List<Food>> = sampleCatalog,
    val meal: ComposedMeal = ComposedMeal(),
    val slot: MealSlot = MealSlot.BREAKFAST,
    val week: List<LocalDate> = sampleBuilderWeek,
    val days: Set<LocalDate> = setOf(sampleBuilderWeek.first()),
    val state: BuildState = BuildState.Composing,
)

data class BuilderActions(
    val onPick: (Food) -> Unit = {},
    val onPreset: (String) -> Unit = {},
    val onSlot: (MealSlot) -> Unit = {},
    val onToggleDay: (LocalDate) -> Unit = {},
    val onAllDays: () -> Unit = {},
    val onPlan: () -> Unit = {},
    val onReset: () -> Unit = {},
)

// PREVIEW fixtures — fixed dates, never a clock read (ARCH-12).
val sampleBuilderWeek: List<LocalDate> = (22..28).map { LocalDate(2026, 7, it) }

private fun previewFood(
    id: String,
    name: String,
    cat: BflCategory,
    kcal: Int,
    p: Int,
    serving: String,
    c: Int = 0,
    f: Int = 0,
) =
    Food(
        id, name, "USDA", serving, kcal, p, c, f,
        // The fixture carries per-100 g too, so a preview exercises the same shape a seeded
        // food has rather than a half-populated one.
        per100g = Macros100g(kcal.toDouble(), p.toDouble(), c.toDouble(), f.toDouble()),
        category = cat,
        portionGrams = 100,
    )

val sampleCatalog: Map<BflCategory, List<Food>> = mapOf(
    BflCategory.PROTEIN to listOf(
        previewFood("chicken-breast", "Chicken breast", BflCategory.PROTEIN, 198, 37, "1 palm (120 g)", c = 0, f = 4),
        previewFood("salmon", "Salmon fillet", BflCategory.PROTEIN, 247, 27, "1 palm (120 g)", c = 0, f = 15),
        previewFood("egg-white", "Egg whites", BflCategory.PROTEIN, 51, 11, "3 large whites (99 g)", c = 1, f = 0),
    ),
    BflCategory.CARB to listOf(
        previewFood("brown-rice", "Brown rice, cooked", BflCategory.CARB, 240, 5, "1 fist (195 g)", c = 50, f = 2),
        previewFood("sweet-potato", "Sweet potato", BflCategory.CARB, 162, 4, "1 large (180 g)", c = 37, f = 0),
        previewFood("oats-dry", "Oats, dry", BflCategory.CARB, 156, 7, "1/2 cup dry (40 g)", c = 27, f = 3),
    ),
    BflCategory.VEGETABLE to listOf(
        previewFood("broccoli", "Broccoli", BflCategory.VEGETABLE, 55, 4, "1 cup (156 g)", c = 11, f = 1),
        previewFood("asparagus", "Asparagus", BflCategory.VEGETABLE, 40, 4, "1 cup (180 g)", c = 7, f = 0),
    ),
    BflCategory.FAT to listOf(
        previewFood("almonds", "Almonds", BflCategory.FAT, 162, 6, "1 small handful (28 g)", c = 6, f = 14),
    ),
)

/** The VM-backed destination (`build`, BFL-05). */
@Composable
fun MealBuilderRoute(
    onBack: () -> Unit,
    viewModel: MealBuilderViewModel = koinViewModel(),
) {
    // SHELL-05: a destination registered directly on the NavHost owns its insets — tabs inherit
    // theirs from AppShell. The wrapper used to sit at the call site in AppNavHost.kt; harness
    // 0.14 requires it HERE, so a destination added later cannot ship inset-less because whoever
    // registered it forgot to wrap it.
    BaseScreen {
        val catalog by viewModel.catalog.collectAsStateWithLifecycle()
        val meal by viewModel.meal.collectAsStateWithLifecycle()
        val slot by viewModel.slot.collectAsStateWithLifecycle()
        val days by viewModel.days.collectAsStateWithLifecycle()
        val state by viewModel.state.collectAsStateWithLifecycle()

        MealBuilderScreen(
            ui = BuilderUi(catalog, meal, slot, viewModel.week, days, state),
            onBack = onBack,
            actions = BuilderActions(
                onPick = viewModel::onPick,
                onPreset = viewModel::onPreset,
                onSlot = viewModel::onSlot,
                onToggleDay = viewModel::onToggleDay,
                onAllDays = viewModel::onAllDays,
                onPlan = viewModel::onPlan,
                onReset = viewModel::onReset,
            ),
        )
    }
}

@Composable
fun MealBuilderScreen(
    ui: BuilderUi = BuilderUi(),
    onBack: () -> Unit = {},
    actions: BuilderActions = BuilderActions(),
) {
    ScreenColumn(screenTag = "builder") {
        AppHeader(title = "Build a meal", screenTag = "builder", onBack = onBack)
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(FuelledTokens.GapCard),
        ) {
            // The order is the order of the decisions: start from something known, adjust the
            // parts, then say where it goes. Presets first because the fastest correct answer
            // for most meals is one somebody already worked out (BFL-07).
            PresetRow(actions.onPreset)
            BflCategory.entries.forEach { category ->
                CategoryPicker(
                    category = category,
                    foods = ui.catalog[category].orEmpty(),
                    chosen = ui.meal[category],
                    onPick = actions.onPick,
                )
            }
            TotalCard(ui.meal)
            TargetCard(ui = ui, actions = actions)
            PlanButton(ui = ui, onPlan = actions.onPlan, onReset = actions.onReset)
            Spacer(Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun PresetRow(onPreset: (String) -> Unit) {
    SectionLabel("START FROM", "builder_presets_label")
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MEAL_PRESETS.forEach { preset ->
            Box(
                modifier = Modifier
                    .heightIn(min = AppButtonDefaults.MinTouchTarget)
                    .clip(RoundedCornerShape(FuelledTokens.RadiusPill))
                    .background(MaterialTheme.colorScheme.secondary)
                    .clickable(onClickLabel = "Start from ${preset.name}") { onPreset(preset.id) }
                    .semantics { testTag = "builder_preset_${preset.id}" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

/**
 * One role's choices. The whole screen is four of these, which IS the method: a meal is a
 * protein and a carb, with vegetables on two meals a day.
 */
@Composable
private fun CategoryPicker(
    category: BflCategory,
    foods: List<Food>,
    chosen: Food?,
    onPick: (Food) -> Unit,
) {
    if (foods.isEmpty()) return
    SectionLabel(category.plural.uppercase(), "builder_label_${category.name}")
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        foods.forEach { food ->
            val selected = chosen?.id == food.id
            Column(
                modifier = Modifier
                    .width(150.dp)
                    .clip(RoundedCornerShape(FuelledTokens.RadiusCard))
                    .background(MaterialTheme.colorScheme.surface)
                    .let {
                        if (selected) it.border(2.dp, FuelledColors.Primary, RoundedCornerShape(FuelledTokens.RadiusCard))
                        else it
                    }
                    .selectable(selected = selected, onClick = { onPick(food) })
                    .padding(12.dp)
                    .semantics { testTag = "builder_food_${food.id}" },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = food.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    text = food.serving,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${food.kcal} kcal",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${food.proteinG}g P",
                        style = MaterialTheme.typography.labelMedium,
                        color = FuelledColors.Protein,
                    )
                }
            }
        }
    }
}

/** BFL-05: the running total, and BFL-08's shape note — stated, never enforced. */
@Composable
private fun TotalCard(meal: ComposedMeal) {
    val total = meal.total
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .semantics { testTag = "builder_total" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${total.kcal} kcal",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${total.proteinG}g P · ${total.carbsG}g C · ${total.fatG}g F",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // BFL-08: information, not a gate. A mid-morning snack is a protein and a piece of
        // fruit, and an app that refuses to compose it in order to be correct is an app people
        // stop opening.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShapeTag("PROTEIN", meal.hasProtein)
            ShapeTag("CARB", meal.hasCarb)
            ShapeTag("VEG", meal.hasVegetable)
        }
    }
}

@Composable
private fun ShapeTag(label: String, present: Boolean) {
    Tag(
        label,
        if (present) "yes" else "—",
        if (present) FuelledColors.Success else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** BFL-06: which slot, and which days. The reason the whole surface exists. */
@Composable
private fun TargetCard(ui: BuilderUi, actions: BuilderActions) {
    SectionLabel("PUT IT IN", "builder_target_label")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .semantics { testTag = "builder_target" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MealSlot.entries.forEach { slot ->
                Pill(
                    label = slot.label(),
                    selected = slot == ui.slot,
                    tag = "builder_slot_${slot.name}",
                    onClick = { actions.onSlot(slot) },
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "on ${ui.days.size} ${if (ui.days.size == 1) "day" else "days"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { testTag = "builder_day_count" },
            )
            Spacer(Modifier.weight(1f))
            AppTextButton(
                text = if (ui.days.size == ui.week.size) "Just today" else "All 7 days",
                onClick = actions.onAllDays,
                modifier = Modifier.semantics { testTag = "builder_all_days" },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ui.week.forEach { date ->
                Pill(
                    label = date.chipLabel(ui.week.first()),
                    selected = date in ui.days,
                    tag = "builder_day_$date",
                    onClick = { actions.onToggleDay(date) },
                )
            }
        }
    }
}

@Composable
private fun PlanButton(ui: BuilderUi, onPlan: () -> Unit, onReset: () -> Unit) {
    when (val state = ui.state) {
        is BuildState.Planned -> Column(
            modifier = Modifier.fillMaxWidth().semantics { testTag = "builder_planned" },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Planned into ${state.slot.label()} on ${state.days} " +
                    if (state.days == 1) "day." else "days.",
                style = MaterialTheme.typography.bodyLarge,
                color = FuelledColors.Success,
            )
            AppTextButton(
                text = "Build another",
                onClick = onReset,
                modifier = Modifier.semantics { testTag = "builder_again" },
            )
        }
        is BuildState.Error -> Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = FuelledColors.Warning,
            modifier = Modifier.semantics { testTag = "builder_error" },
        )
        else -> AppPrimaryButton(
            text = if (ui.days.size == 1) {
                "Add to ${ui.slot.label()}"
            } else {
                "Plan into ${ui.slot.label()} on ${ui.days.size} days"
            },
            onClick = onPlan,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = "builder_plan" },
        )
    }
}

@Composable
private fun SectionLabel(text: String, tag: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { testTag = tag },
    )
}

@Composable
private fun Pill(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = AppButtonDefaults.MinTouchTarget)
            .clip(RoundedCornerShape(FuelledTokens.RadiusPill))
            .background(if (selected) FuelledColors.Primary else MaterialTheme.colorScheme.secondary)
            .selectable(selected = selected, onClick = onClick)
            .semantics { testTag = tag },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) FuelledColors.OnPrimary else MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/** "Today", else "Thu 23" — the plan strip's idiom, so the two read as the same calendar. */
private fun LocalDate.chipLabel(today: LocalDate): String =
    if (this == today) "Today"
    else "${dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} $day"

private fun MealSlot.label(): String =
    name.lowercase().split('_').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
