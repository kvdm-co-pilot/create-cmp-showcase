package com.kvdm.fuelled.presentation.meal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.presentation.components.AppButtonDefaults
import com.kvdm.fuelled.presentation.components.AppHeader
import com.kvdm.fuelled.presentation.components.AppPrimaryButton
import com.kvdm.fuelled.presentation.components.ContentStateContainer
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.presentation.components.EmptyState
import com.kvdm.fuelled.presentation.components.ListItemCard
import com.kvdm.fuelled.presentation.components.ScreenColumn
import com.kvdm.fuelled.presentation.components.Tag
import com.kvdm.fuelled.presentation.theme.FuelledColors
import com.kvdm.fuelled.presentation.theme.FuelledTokens
import com.kvdm.fuelled.presentation.theme.designToken
import com.kvdm.fuelled.presentation.today.dayHeaderLabel
import com.kvdm.fuelled.presentation.today.label
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.koin.compose.viewmodel.koinViewModel

// ── Meal tray: the add-to-meal screen (MEAL-09/10/11/12) ─────────────────────────────
// The visual language descends from the signed design draft (feature-design:meal):
// searchable list, checkbox rows, a pinned total bar with one primary Add. The running
// total carries protein/carbs/fat (MEAL-09) and the header states the target slot AND
// date (MEAL-10). The meal-plan brief's usability audit then removed the draft's two
// targeting controls — the slot pills and the date row — leaving the target a statement;
// the call site below says why. That removal is what feature-design:meal is currently
// reopened for: it re-signs on rendered output, never on this description.
//
// Two entry points, the UI-first preview seam mirroring the Foods exemplar:
//   • MealTrayScreen — STATELESS, sample-defaulted. The preview registry renders it, no VM.
//   • MealTrayRoute  — the VM-backed wrapper: catalog, search, tray, target, and confirm are
//     all driven by MealTrayViewModel.

// PREVIEW/DEMO fixtures — the screen's preview seam, never production data (ARCH-12): the
// Room-backed FoodRepositoryImpl feeds the VM-backed MealTrayRoute the same catalog. The
// date is FIXED, not "today", so gallery renders and structural diffs stay deterministic.
val sampleTrayFoods = listOf(
    Food("1", "Chicken breast", "Raw · skinless", "100 g", 165, 31, 0, 4),
    Food("2", "Whey protein", "Gold Standard", "1 scoop · 30 g", 120, 24, 3, 2),
    Food("3", "Rolled oats", "Quaker", "80 g", 303, 11, 54, 6),
    Food("4", "Greek yogurt 0%", "Fage", "170 g", 100, 17, 6, 0),
    Food("5", "Banana", "Medium", "1 · 118 g", 105, 1, 27, 0),
    Food("6", "White rice", "Cooked", "150 g", 195, 4, 42, 0),
    Food("7", "Almonds", "Raw", "20 g", 116, 4, 4, 10),
)

val sampleTrayTarget = MealTrayTarget(
    date = LocalDate(2026, 7, 22),
    slot = MealSlot.LUNCH,
    currentDay = LocalDate(2026, 7, 22),
)

val sampleTrayContents = TrayContents(
    listOf(TrayLine(sampleTrayFoods[0]), TrayLine(sampleTrayFoods[2]), TrayLine(sampleTrayFoods[4])),
)

/**
 * The VM-backed add-to-meal tray. Catalog loading, search, the tray's contents and its running
 * total, the target, and the confirm all live in [MealTrayViewModel]; this wrapper only renders
 * them. The stateless [MealTrayScreen] below stays VM-free for the preview registry.
 */
@Composable
fun MealTrayRoute(
    viewModel: MealTrayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val target by viewModel.target.collectAsStateWithLifecycle()
    val tray by viewModel.tray.collectAsStateWithLifecycle()
    val confirmState by viewModel.confirmState.collectAsStateWithLifecycle()

    MealTrayScreen(
        state = state,
        query = query,
        target = target,
        tray = tray,
        confirmState = confirmState,
        onQueryChange = viewModel::onQueryChange,
        onFoodToggled = viewModel::onFoodToggled,
        onConfirm = viewModel::confirm,
        onRetry = viewModel::load,
    )
}

/**
 * The stateless tray — the preview/UI-first seam. Every piece of state arrives as a parameter
 * with a sample default, so the registry renders it without a VM or Koin; the production path
 * is [MealTrayRoute] + [MealTrayViewModel].
 *
 * @param tray What the tray holds. Its [TrayContents.total] is the running total the bar shows
 *   (MEAL-09) and the empty check the Add control is disabled by (MEAL-11).
 * @param target The logical date and slot this tray is aimed at (MEAL-10) — carried by the
 *   tap that opened it; the tray states it and never changes it.
 */
@Composable
fun MealTrayScreen(
    state: ContentUiState<List<Food>> = ContentUiState.Content(sampleTrayFoods),
    query: String = "",
    target: MealTrayTarget = sampleTrayTarget,
    tray: TrayContents = sampleTrayContents,
    confirmState: TrayConfirmState = TrayConfirmState.Idle,
    onQueryChange: (String) -> Unit = {},
    onFoodToggled: (Food) -> Unit = {},
    onConfirm: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    ScreenColumn(screenTag = "meal_tray") {
        AppHeader(title = "Add to meal", screenTag = "meal_tray")
        // The target is a STATEMENT now, not a control (meal-plan brief, usability audit):
        // the tap on a plan container is what aims the tray, so the in-tray slot pills and
        // date row are gone — retargeting mid-tray was how food landed in the wrong meal,
        // and four generic pills could not even name which snack. To retarget: go back and
        // tap the right container.
        TrayTargetLine(target)
        Spacer(Modifier.height(FuelledTokens.GapCard))

        TraySearchField(query = query, onQueryChange = onQueryChange)
        Spacer(Modifier.height(FuelledTokens.GapCard))

        Box(Modifier.weight(1f)) {
            ContentStateContainer(
                state = state,
                screenTag = "meal_tray",
                onRetry = onRetry,
                empty = {
                    EmptyState(
                        screenTag = "meal_tray",
                        title = "No foods match",
                        body = "Try a different search",
                    )
                },
            ) { foods ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(FuelledTokens.GapCard)) {
                    items(foods, key = { it.id }) { food ->
                        TrayFoodRow(
                            food = food,
                            checked = tray.holds(food.id),
                            onToggle = { onFoodToggled(food) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(FuelledTokens.GapCard))
        TrayTotalBar(
            target = target,
            total = tray.total,
            confirmState = confirmState,
            onConfirm = onConfirm,
        )
    }
}

/**
 * The header's statement of the target (MEAL-10): which slot, on which logical date. It is
 * the whole targeting surface now — nothing here changes it, so it says both halves in
 * words rather than leaving "tomorrow" implied the way a segmented control would.
 *
 * Both halves reuse the Today screen's formatters ([label], [dayHeaderLabel]) — the app has
 * ONE rendering of a slot and of a logical day, and a second copy here would be the thing
 * that eventually disagrees.
 */
@Composable
private fun TrayTargetLine(target: MealTrayTarget) {
    Text(
        text = "${target.slot.label} · ${target.date.dayHeaderLabel()}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { testTag = "meal_tray_target" },
    )
}

/** The tray's search box, in the Foods search idiom, radius from RadiusInput. */
@Composable
private fun TraySearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().semantics { testTag = "meal_tray_search" },
        placeholder = { Text("Search foods") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(FuelledTokens.RadiusInput),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = FuelledColors.Primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/**
 * One checkbox-selectable food row (§2): the whole card toggles, the checkbox mirrors —
 * ticking accumulates into the running total. [ListItemCard] owns the card surface, radius,
 * elevation, and the 48 dp row floor.
 *
 * The checkbox is DECORATIVE (`onCheckedChange = null`): the card is the control. A second
 * clickable inside the row was a 24x24 hit target — half the 48 dp floor — that announced
 * nothing to a screen reader, since a testTag is a test hook and not an accessibility label.
 * As a mirror it inherits the card's target and the card's name; one control per row.
 */
@Composable
private fun TrayFoodRow(food: Food, checked: Boolean, onToggle: () -> Unit) {
    ListItemCard(
        title = food.name,
        subtitle = "${food.brand} · ${food.serving}",
        onClick = onToggle,
        modifier = Modifier.semantics { testTag = "meal_tray_item_${food.id}" },
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FuelledTokens.GapCard),
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = food.kcal.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "kcal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    modifier = Modifier.semantics { testTag = "meal_tray_check_${food.id}" },
                )
            }
        },
    )
}

/**
 * The pinned running-total bar (§2), raised on ElevationModal so it reads as a bar over the
 * list. It carries the tray's whole running total (MEAL-09): the item count and calories on
 * the headline, then protein/carbs/fat as the app's macro [Tag]s — the same three-tag row the
 * Foods catalog rows use, so the tray totals read exactly like the foods that fill it.
 *
 * The Add control is disabled while the tray is empty (MEAL-11). The refusal itself is not
 * here — [MealTrayViewModel.confirm] guards the write — this is only its rendering.
 */
@Composable
private fun TrayTotalBar(
    target: MealTrayTarget,
    total: TrayTotal,
    confirmState: TrayConfirmState,
    onConfirm: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = FuelledTokens.ElevationModal,
        modifier = Modifier
            .fillMaxWidth()
            .designToken(
                tokens = listOf("RadiusCard", "ElevationModal", "PaddingCard"),
                resolved = mapOf(
                    "radius" to "${FuelledTokens.RadiusCard.value.toInt()}dp",
                    "elevation" to "${FuelledTokens.ElevationModal.value.toInt()}dp",
                    "padding" to "${FuelledTokens.PaddingCard.value.toInt()}dp",
                ),
            ),
    ) {
        Row(
            modifier = Modifier.padding(FuelledTokens.PaddingCard),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${total.items} ${if (total.items == 1) "item" else "items"} · ${total.kcal} kcal",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { testTag = "meal_tray_total" },
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(FuelledTokens.GapCard),
                    modifier = Modifier.semantics { testTag = "meal_tray_macros" },
                ) {
                    Tag("P", "${total.proteinG}g", FuelledColors.Protein)
                    Tag("C", "${total.carbsG}g", FuelledColors.Carbs)
                    Tag("F", "${total.fatG}g", FuelledColors.Fat)
                }
                Text(
                    text = confirmState.barMessage(target),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { testTag = "meal_tray_status" },
                )
            }
            AppPrimaryButton(
                text = "Add",
                onClick = onConfirm,
                enabled = !total.isEmpty && confirmState != TrayConfirmState.Saving,
                modifier = Modifier.semantics { testTag = "meal_tray_add" },
            )
        }
    }
}

/**
 * The bar's supporting line. Presentation owns user-facing copy, so the confirm's outcome —
 * including a mapped [com.kvdm.fuelled.domain.model.DomainError]'s message — is rendered on
 * the line that already states where the tray is going, never as a raw exception.
 */
private fun TrayConfirmState.barMessage(target: MealTrayTarget): String = when (this) {
    TrayConfirmState.Idle -> "Adding to ${target.slot.label}"
    TrayConfirmState.Saving -> "Adding to ${target.slot.label}…"
    TrayConfirmState.Saved -> "Added to ${target.slot.label}"
    is TrayConfirmState.Error -> message
}
