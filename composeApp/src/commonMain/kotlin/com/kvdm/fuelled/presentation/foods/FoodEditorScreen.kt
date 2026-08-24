package com.kvdm.fuelled.presentation.foods

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.presentation.components.BaseScreen
import com.kvdm.fuelled.presentation.components.AppButtonDefaults
import com.kvdm.fuelled.presentation.components.AppHeader
import com.kvdm.fuelled.presentation.components.AppPrimaryButton
import com.kvdm.fuelled.presentation.components.AppTextButton
import com.kvdm.fuelled.presentation.components.ScreenColumn
import com.kvdm.fuelled.presentation.theme.FuelledColors
import com.kvdm.fuelled.presentation.theme.FuelledTokens
import org.koin.compose.viewmodel.koinViewModel

// ── Food editor: your own catalog entries (CAT-01) ───────────────────────────────────────
// The catalog stops being a closed world. What you actually eat — your protein powder, the
// bread you buy, your mother's lasagne — goes in here once and is then one search away
// forever. Seeded foods are reference data and are not editable; this screen only ever
// creates or edits a CUSTOM food.

/** The VM-backed editor. A blank [foodId] means "new food"; the route mints the id. */
@Composable
fun FoodEditorRoute(
    foodId: String,
    newId: String,
    onDone: () -> Unit,
    viewModel: FoodEditorViewModel = koinViewModel(),
) {
    // SHELL-05: a destination registered directly on the NavHost owns its insets — tabs inherit
    // theirs from AppShell. The wrapper used to sit at the call site in AppNavHost.kt; harness
    // 0.14 requires it HERE, so a destination added later cannot ship inset-less because whoever
    // registered it forgot to wrap it.
    BaseScreen {
        LaunchedEffect(foodId) { viewModel.load(foodId) }
        val food by viewModel.food.collectAsStateWithLifecycle()
        val state by viewModel.state.collectAsStateWithLifecycle()

        // The write landed, so the editor's job is done — same discipline as the tray (MEAL-13):
        // the confirmation the user needs is the food in the list behind them.
        LaunchedEffect(state) { if (state == FoodEditState.Saved) onDone() }

        FoodEditorScreen(
            food = food,
            state = state,
            onBack = onDone,
            onSave = { name, brand, serving, kcal, p, c, f, veg ->
                viewModel.save(food?.id ?: newId, name, brand, serving, kcal, p, c, f, veg)
            },
            onDelete = food?.takeIf { it.custom }?.let { { viewModel.delete(it.id) } },
        )
    }
}

/** The stateless editor — the preview seam; `food = null` is the create-new form. */
@Composable
fun FoodEditorScreen(
    food: Food? = null,
    state: FoodEditState = FoodEditState.Editing,
    onBack: () -> Unit = {},
    onSave: (String, String, String, Int?, Int?, Int?, Int?, Boolean) -> Unit =
        { _, _, _, _, _, _, _, _ -> },
    onDelete: (() -> Unit)? = null,
) {
    var name by remember(food) { mutableStateOf(food?.name.orEmpty()) }
    var brand by remember(food) { mutableStateOf(food?.brand.orEmpty()) }
    var serving by remember(food) { mutableStateOf(food?.serving.orEmpty()) }
    var kcal by remember(food) { mutableStateOf(food?.kcal?.toString().orEmpty()) }
    var protein by remember(food) { mutableStateOf(food?.proteinG?.toString().orEmpty()) }
    var carbs by remember(food) { mutableStateOf(food?.carbsG?.toString().orEmpty()) }
    var fat by remember(food) { mutableStateOf(food?.fatG?.toString().orEmpty()) }
    var veg by remember(food) { mutableStateOf(food?.veg ?: false) }

    ScreenColumn(screenTag = "food_editor") {
        AppHeader(
            title = if (food == null) "New food" else "Edit food",
            screenTag = "food_editor",
            onBack = onBack,
        )
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(FuelledTokens.GapCard),
        ) {
            EditorField(name, { name = it }, "Name", "Chicken thigh", "food_editor_name")
            EditorField(brand, { brand = it }, "Brand or note", "Optional", "food_editor_brand")
            EditorField(serving, { serving = it }, "Serving", "100 g", "food_editor_serving")
            EditorField(kcal, { kcal = it }, "Calories per serving", "kcal", "food_editor_kcal")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    EditorField(protein, { protein = it }, "Protein", "g", "food_editor_protein")
                }
                Column(Modifier.weight(1f)) {
                    EditorField(carbs, { carbs = it }, "Carbs", "g", "food_editor_carbs")
                }
                Column(Modifier.weight(1f)) {
                    EditorField(fat, { fat = it }, "Fat", "g", "food_editor_fat")
                }
            }
            // PLAN-22: the veg flag is a claim about the FOOD, so it is set once here rather
            // than asked at every meal — which is what makes "Veg n of 2" derivable at all.
            // The ROW is the control and the Switch mirrors it — the tray's checkbox pattern.
            // A bare Switch is a 52x32 target with no label: too small, and unreadable to a
            // screen reader (both caught by audit_a11y here, 2026-08-01). As a toggleable row
            // it clears the 48 dp floor and takes the row's own text as its name.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppButtonDefaults.MinTouchTarget)
                    .toggleable(value = veg, onValueChange = { veg = it }, role = Role.Switch)
                    .semantics { testTag = "food_editor_veg" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Counts as a vegetable",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Feeds the day's \"Veg n of 2\"",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = veg, onCheckedChange = null)
            }

            if (state is FoodEditState.Error) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { testTag = "food_editor_error" },
                )
            }

            Spacer(Modifier.height(4.dp))
            AppPrimaryButton(
                text = "Save food",
                onClick = {
                    onSave(
                        name, brand, serving,
                        kcal.trim().toIntOrNull(),
                        protein.trim().toIntOrNull(),
                        carbs.trim().toIntOrNull(),
                        fat.trim().toIntOrNull(),
                        veg,
                    )
                },
                enabled = state != FoodEditState.Saving,
                modifier = Modifier.fillMaxWidth().semantics { testTag = "food_editor_save" },
            )
            onDelete?.let {
                AppTextButton(
                    text = "Delete this food",
                    onClick = it,
                    modifier = Modifier.semantics { testTag = "food_editor_delete" },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    tag: String,
) {
    // M3's own label slot — see OnboardingScreen: a sibling Text does not label a field for
    // a screen reader, it just sits near it.
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            shape = RoundedCornerShape(FuelledTokens.RadiusInput),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FuelledColors.Primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier.fillMaxWidth().semantics { testTag = tag },
        )
    }
}
