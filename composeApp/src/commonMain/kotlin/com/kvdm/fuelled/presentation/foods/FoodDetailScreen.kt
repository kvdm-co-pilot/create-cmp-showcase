package com.kvdm.fuelled.presentation.foods

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.presentation.components.AppButtonDefaults
import com.kvdm.fuelled.presentation.components.AppIconButton
import com.kvdm.fuelled.presentation.components.AppTextButton
import com.kvdm.fuelled.presentation.components.AppHeader
import com.kvdm.fuelled.presentation.components.AppPrimaryButton
import com.kvdm.fuelled.presentation.components.BaseScreen
import com.kvdm.fuelled.presentation.components.ContentStateContainer
import com.kvdm.fuelled.presentation.theme.FuelledColors
import com.kvdm.fuelled.presentation.theme.FuelledTokens
import org.koin.compose.viewmodel.koinViewModel

// ── Food detail: the full nutritional breakdown + log action ─────────────────────────
// FoodDetailRoute is the VM-backed nav destination — it resolves the Food by id through
// FoodDetailViewModel (the repository), then renders it. FoodDetailScreen stays stateless
// and sample-defaulted for the preview registry (no VM, no Koin).

/**
 * The VM-backed detail destination the nav graph hosts. Resolves [foodId] via
 * [FoodDetailViewModel] and presents Loading/Content/Error through [ContentStateContainer];
 * a missing id renders the mapped NotFound copy, never a crash. The log action (UX-03) is
 * the ViewModel's own write — the route takes no `onLog` callback, because the callback was
 * how the button shipped wired to nothing.
 */
@Composable
fun FoodDetailRoute(
    foodId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit = {},
    viewModel: FoodDetailViewModel = koinViewModel(),
) {
    LaunchedEffect(foodId) { viewModel.load(foodId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val logState by viewModel.logState.collectAsStateWithLifecycle()

    BaseScreen { innerPadding ->
        ContentStateContainer(
            state = state,
            screenTag = "food_detail",
            onRetry = { viewModel.load(foodId) },
        ) { food ->
            FoodDetailContent(
                food = food,
                onBack = onBack,
                logState = logState,
                onLogSlot = viewModel::log,
                onToggleFavourite = viewModel::toggleFavourite,
                onEdit = if (food.custom) ({ onEdit(food.id) }) else null,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

// PREVIEW/DEMO fixture — this screen's own preview seam. Declared here, not borrowed from
// FoodsScreen's `sampleFoods`: ARCH-12 keeps a sample* symbol inside its declaring file, so
// a screen's fixture can never become a shared dependency that later leaks into production
// wiring. Each screen owning its fixture is the cost of that guarantee.
val sampleFood = Food("1", "Chicken breast", "Raw · skinless", "100 g", 165, 31, 0, 4)

/**
 * The stateless detail — the preview/UI-first seam. Renders a resolved [Food] inside
 * [BaseScreen], defaulting to a sample so the preview registry can render it without a VM.
 */
@Composable
fun FoodDetailScreen(
    food: Food = sampleFood,
    onBack: () -> Unit = {},
    logState: FoodLogState = FoodLogState.Idle,
    onLogSlot: (MealSlot) -> Unit = {},
    logPickerInitiallyOpen: Boolean = false,
    onToggleFavourite: () -> Unit = {},
    onEdit: (() -> Unit)? = null,
) {
    BaseScreen { innerPadding ->
        FoodDetailContent(
            food = food,
            onBack = onBack,
            logState = logState,
            onLogSlot = onLogSlot,
            logPickerInitiallyOpen = logPickerInitiallyOpen,
            onToggleFavourite = onToggleFavourite,
            onEdit = onEdit,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun FoodDetailContent(
    food: Food,
    onBack: () -> Unit,
    logState: FoodLogState,
    onLogSlot: (MealSlot) -> Unit,
    modifier: Modifier = Modifier,
    logPickerInitiallyOpen: Boolean = false,
    onToggleFavourite: () -> Unit = {},
    onEdit: (() -> Unit)? = null,
) {
    val pKcal = food.proteinG * 4
    val cKcal = food.carbsG * 4
    val fKcal = food.fatG * 9
    val totalMacroKcal = (pKcal + cKcal + fKcal).coerceAtLeast(1)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        AppHeader(
            title = food.name,
            screenTag = "food_detail",
            onBack = onBack,
            actions = {
                // CAT-02: one tap to pin. A favourite leads every list this catalog feeds,
                // which is the difference between searching for your whey every day and not.
                AppIconButton(
                    icon = if (food.favourite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (food.favourite) "Remove from favourites" else "Add to favourites",
                    onClick = onToggleFavourite,
                    tint = if (food.favourite) FuelledColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { testTag = "food_favourite" },
                )
                // CAT-01: only a food YOU made is editable — the seeded catalog is reference
                // data, and this is why the control appears for one and not the other.
                onEdit?.let {
                    AppTextButton(
                        text = "Edit",
                        onClick = it,
                        modifier = Modifier.semantics { testTag = "food_edit" },
                    )
                }
            },
        )

        Text(
            text = "${food.brand} · ${food.serving}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Hero calories
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = food.kcal.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = " kcal",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            Text(
                text = "per ${food.serving}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            // Stacked macro proportion bar
            Row(
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            ) {
                MacroSegment(pKcal, totalMacroKcal, FuelledColors.Protein)
                MacroSegment(cKcal, totalMacroKcal, FuelledColors.Carbs)
                MacroSegment(fKcal, totalMacroKcal, FuelledColors.Fat)
            }
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MacroStat("Protein", food.proteinG, pKcal * 100 / totalMacroKcal, FuelledColors.Protein)
                MacroStat("Carbs", food.carbsG, cKcal * 100 / totalMacroKcal, FuelledColors.Carbs)
                MacroStat("Fat", food.fatG, fKcal * 100 / totalMacroKcal, FuelledColors.Fat)
            }
        }

        LogAction(
            logState = logState,
            onLogSlot = onLogSlot,
            pickerInitiallyOpen = logPickerInitiallyOpen,
        )
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * The slot's label as the PICKER shows it — all six distinct. On a container grid the three
 * snacks read "Snack" because position says which (MEAL-03's reasoning); in a flat picker
 * there is no position, so the label must carry the identity itself.
 */
private val MealSlot.pickerLabel: String
    get() = when (this) {
        MealSlot.BREAKFAST -> "Breakfast"
        MealSlot.MORNING_SNACK -> "Morning snack"
        MealSlot.LUNCH -> "Lunch"
        MealSlot.AFTERNOON_SNACK -> "Afternoon snack"
        MealSlot.DINNER -> "Dinner"
        MealSlot.EVENING_SNACK -> "Evening snack"
    }

/**
 * The catalog-first log action (UX-03): "Log this food" reveals the six containers of the
 * current logical day; choosing one writes and confirms. The picker IS the aim step —
 * containers aim, the tray fills (MEAL-10's model); here the food came first, so the aim
 * comes second, made explicit rather than guessed from the clock (MEAL-04 died for that).
 * The picker states the day it writes to; one serving, today only — quantity and other days
 * are the tray's and the plan's jobs.
 */
@Composable
private fun LogAction(
    logState: FoodLogState,
    onLogSlot: (MealSlot) -> Unit,
    pickerInitiallyOpen: Boolean = false,
) {
    var pickerOpen by remember { mutableStateOf(pickerInitiallyOpen) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppPrimaryButton(
            text = when (logState) {
                is FoodLogState.Logged -> "Added to ${logState.slot.pickerLabel} ✓"
                FoodLogState.Saving -> "Logging…"
                else -> "Log this food"
            },
            onClick = { pickerOpen = !pickerOpen },
            enabled = logState != FoodLogState.Saving,
            modifier = Modifier.fillMaxWidth().semantics { testTag = "food_log" },
        )

        if (logState is FoodLogState.Error) {
            Text(
                text = logState.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { testTag = "food_log_error" },
            )
        }

        if (pickerOpen) {
            Text(
                text = "To today · one serving",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { testTag = "food_log_day" },
            )
            MealSlot.entries.chunked(3).forEach { rowSlots ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowSlots.forEach { slot ->
                        SlotChip(
                            label = slot.pickerLabel,
                            tag = "food_log_slot_${slot.name.lowercase()}",
                            onClick = {
                                pickerOpen = false
                                onLogSlot(slot)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** One picker chip, in the day strip's pill idiom (48 dp target, RadiusPill). */
@Composable
private fun SlotChip(label: String, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(AppButtonDefaults.MinTouchTarget)
            .clip(RoundedCornerShape(FuelledTokens.RadiusPill))
            .background(MaterialTheme.colorScheme.secondary)
            .clickable(onClick = onClick)
            .semantics { testTag = tag },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.MacroSegment(part: Int, total: Int, color: Color) {
    if (part <= 0) return
    Spacer(
        modifier = Modifier
            .weight(part.toFloat() / total)
            .height(10.dp)
            .background(color),
    )
}

@Composable
private fun MacroStat(label: String, grams: Int, pct: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${grams}g", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        Text("$pct%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
