package com.kvdm.fuelled.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvdm.fuelled.domain.model.AppSettings
import com.kvdm.fuelled.domain.model.PREP_LEAD_CHOICES
import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.domain.model.SupplementTiming
import com.kvdm.fuelled.domain.model.UnitSystem
import com.kvdm.fuelled.presentation.components.AppButtonDefaults
import com.kvdm.fuelled.presentation.components.AppHeader
import com.kvdm.fuelled.presentation.components.AppIconButton
import com.kvdm.fuelled.presentation.components.AppTextButton
import com.kvdm.fuelled.presentation.components.ContentStateContainer
import com.kvdm.fuelled.presentation.components.ScreenColumn
import com.kvdm.fuelled.presentation.theme.FuelledColors
import com.kvdm.fuelled.presentation.theme.FuelledTokens
import org.koin.compose.viewmodel.koinViewModel

// ── Settings: the promises UX-04 stopped making, now kept (SET-01..08) ───────────────────
// The usability pass took the chevrons OFF Profile's settings rows because tapping them did
// nothing. That was the honest fix and it left the app with a Settings section that settled
// nothing. This is the other half.

/** What the surface renders. One shape, so the screen stays stateless for the registry. */
data class SettingsUi(
    val settings: AppSettings = AppSettings(),
    val stack: List<Supplement> = sampleStack,
)

/** PREVIEW/DEMO fixture — a realistic stack across three of the four timings. */
val sampleStack: List<Supplement> = listOf(
    Supplement("1", "Creatine", "5 g", SupplementTiming.MORNING, taken = false),
    Supplement("2", "Vitamin D3", "2000 IU", SupplementTiming.MORNING, taken = false),
    Supplement("4", "Caffeine", "200 mg", SupplementTiming.PRE_WORKOUT, taken = false),
    Supplement("6", "Magnesium", "400 mg", SupplementTiming.EVENING, taken = false),
)

/** Every interaction Settings offers, bundled so the stateless screen keeps its shape. */
data class SettingsActions(
    val onUnitSystem: (UnitSystem) -> Unit = {},
    val onPrepLead: (Int) -> Unit = {},
    val onSaveSupplement: (String, String, String, SupplementTiming) -> Unit = { _, _, _, _ -> },
    val onDeleteSupplement: (String) -> Unit = {},
)

/** The VM-backed destination (`settings`, SET-01). */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContentStateContainer(state = state, screenTag = "settings") { ui ->
        SettingsScreen(
            ui = ui,
            onBack = onBack,
            actions = SettingsActions(
                onUnitSystem = viewModel::onUnitSystem,
                onPrepLead = viewModel::onPrepLead,
                onSaveSupplement = viewModel::onSaveSupplement,
                onDeleteSupplement = viewModel::onDeleteSupplement,
            ),
        )
    }
}

@Composable
fun SettingsScreen(
    ui: SettingsUi = SettingsUi(),
    onBack: () -> Unit = {},
    actions: SettingsActions = SettingsActions(),
) {
    ScreenColumn(screenTag = "settings") {
        AppHeader(title = "Settings", screenTag = "settings", onBack = onBack)
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(FuelledTokens.GapCard),
        ) {
            UnitsCard(current = ui.settings.unitSystem, onSelect = actions.onUnitSystem)
            PrepLeadCard(current = ui.settings.prepLeadMinutes, onSelect = actions.onPrepLead)
            StackCard(
                stack = ui.stack,
                onSave = actions.onSaveSupplement,
                onDelete = actions.onDeleteSupplement,
            )
            Spacer(Modifier.padding(bottom = 8.dp))
        }
    }
}

// ── Units (SET-02/SET-03) ───────────────────────────────────────────────────────────────

@Composable
private fun UnitsCard(current: UnitSystem, onSelect: (UnitSystem) -> Unit) {
    SettingsCard(title = "Units & measurements", tag = "settings_units") {
        Text(
            // SET-03 stated where a human can see it. The rule is not obvious from the
            // controls, and the first person to "fix" the inconsistency will be someone who
            // noticed that servings say grams while weight says pounds.
            text = "Applies to weight and water. A food's serving stays exactly as you typed it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ChoicePill(
                label = "Metric · kg, L",
                selected = current == UnitSystem.METRIC,
                tag = "settings_units_metric",
                onClick = { onSelect(UnitSystem.METRIC) },
            )
            ChoicePill(
                label = "Imperial · lb, fl oz",
                selected = current == UnitSystem.IMPERIAL,
                tag = "settings_units_imperial",
                onClick = { onSelect(UnitSystem.IMPERIAL) },
            )
        }
    }
}

// ── The prep lead (SET-07/SET-08) ───────────────────────────────────────────────────────

@Composable
private fun PrepLeadCard(current: Int, onSelect: (Int) -> Unit) {
    SettingsCard(title = "Meal reminders", tag = "settings_reminders") {
        Text(
            text = if (current == 0) {
                "Reminders fire at the meal time."
            } else {
                "Reminders fire $current minutes before each meal — while there is still time to cook."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { testTag = "settings_lead_summary" },
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // A closed set, not a minutes field (SET-07): a text box invites -15 and 9999 and
            // then needs the guard anyway.
            PREP_LEAD_CHOICES.forEach { minutes ->
                ChoicePill(
                    label = if (minutes == 0) "At the meal" else "$minutes min",
                    selected = minutes == current,
                    tag = "settings_lead_$minutes",
                    onClick = { onSelect(minutes) },
                )
            }
        }
    }
}

// ── The stack (SET-04..06) ──────────────────────────────────────────────────────────────

@Composable
private fun StackCard(
    stack: List<Supplement>,
    onSave: (String, String, String, SupplementTiming) -> Unit,
    onDelete: (String) -> Unit,
) {
    // Which supplement's editor is open: an id, "" for the new-supplement form, or null for
    // none. One at a time — the entry row's accordion reasoning (ENTRY-03).
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    SettingsCard(title = "Supplement stack", tag = "settings_stack") {
        SupplementTiming.entries.forEach { timing ->
            val group = stack.filter { it.timing == timing }
            if (group.isEmpty()) return@forEach
            Text(
                text = timing.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            group.forEach { supplement ->
                StackRow(
                    supplement = supplement,
                    editing = editing == supplement.id,
                    onToggleEdit = { editing = if (editing == supplement.id) null else supplement.id },
                    onSave = { name, dose, t -> onSave(supplement.id, name, dose, t); editing = null },
                    onDelete = { onDelete(supplement.id); editing = null },
                )
            }
        }

        if (editing == NEW) {
            SupplementEditor(
                initial = null,
                onSave = { name, dose, timing ->
                    // SET-04: a client-minted id, so a double-tapped save replaces one row
                    // rather than creating twins (MEAL-05's reasoning).
                    onSave(newSupplementId(stack), name, dose, timing)
                    editing = null
                },
                onCancel = { editing = null },
                onDelete = null,
            )
        } else {
            AppTextButton(
                text = "Add a supplement",
                onClick = { editing = NEW },
                modifier = Modifier.semantics { testTag = "settings_stack_add" },
            )
        }
    }
}

@Composable
private fun StackRow(
    supplement: Supplement,
    editing: Boolean,
    onToggleEdit: () -> Unit,
    onSave: (String, String, SupplementTiming) -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppButtonDefaults.MinTouchTarget)
                .semantics { testTag = "settings_supplement_${supplement.id}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    supplement.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    supplement.dose,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppIconButton(
                icon = Icons.Filled.Edit,
                contentDescription = "Edit ${supplement.name}",
                onClick = onToggleEdit,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { testTag = "settings_supplement_edit_${supplement.id}" },
            )
        }
        if (editing) {
            SupplementEditor(
                initial = supplement,
                onSave = onSave,
                onCancel = onToggleEdit,
                onDelete = onDelete,
            )
        }
    }
}

/**
 * The add/edit form. Deliberately the SAME composable for both: "add a supplement" and
 * "correct this one" differ only in what the fields start as and whether a remove is
 * offered — two forms would be two places for the guards to drift apart.
 */
@Composable
private fun SupplementEditor(
    initial: Supplement?,
    onSave: (String, String, SupplementTiming) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var dose by rememberSaveable(initial?.id) { mutableStateOf(initial?.dose ?: "") }
    var timing by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.timing ?: SupplementTiming.MORNING)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FuelledTokens.RadiusCard))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
            .semantics { testTag = "settings_supplement_editor" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Labels ride M3's own slot — a field labelled by a sibling Text reads to a screen
        // reader as an unlabelled clickable (audit_a11y, 2026-08-01).
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth().semantics { testTag = "settings_supplement_name" },
        )
        OutlinedTextField(
            value = dose,
            onValueChange = { dose = it },
            singleLine = true,
            label = { Text("Dose") },
            modifier = Modifier.fillMaxWidth().semantics { testTag = "settings_supplement_dose" },
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SupplementTiming.entries.forEach { option ->
                ChoicePill(
                    label = option.label,
                    selected = option == timing,
                    tag = "settings_timing_${option.name}",
                    onClick = { timing = option },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppTextButton(
                text = "Save",
                onClick = { onSave(name, dose, timing) },
                modifier = Modifier.semantics { testTag = "settings_supplement_save" },
            )
            Spacer(Modifier.width(8.dp))
            AppTextButton(text = "Cancel", onClick = onCancel)
            Spacer(Modifier.weight(1f))
            onDelete?.let {
                AppIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "Remove ${initial?.name.orEmpty()}",
                    onClick = it,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { testTag = "settings_supplement_delete" },
                )
            }
        }
    }
}

// ── Shared bits ─────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsCard(title: String, tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .semantics { testTag = tag },
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            content()
        },
    )
}

/** One choice in a closed set — the day strip's pill, reused so choices look like choices. */
@Composable
private fun ChoicePill(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
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

private const val NEW = ""

/** A fresh id that cannot collide with a seeded row or an existing custom one. */
private fun newSupplementId(stack: List<Supplement>): String =
    "s-${(stack.mapNotNull { it.id.removePrefix("s-").toIntOrNull() }.maxOrNull() ?: stack.size) + 1}"
