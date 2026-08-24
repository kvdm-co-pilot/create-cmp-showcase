package com.kvdm.fuelled.presentation.settings

import com.kvdm.fuelled.core.time.systemToday
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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
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
import com.kvdm.fuelled.domain.model.ReminderLead
import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.domain.model.SupplementSchedule
import com.kvdm.fuelled.domain.model.SupplementTiming
import com.kvdm.fuelled.domain.model.UnitSystem
import com.kvdm.fuelled.domain.model.WorkoutDayPlan
import com.kvdm.fuelled.domain.model.WorkoutWeek
import com.kvdm.fuelled.domain.model.label
import com.kvdm.fuelled.domain.model.shortLabel
import kotlin.time.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.kvdm.fuelled.presentation.components.BaseScreen
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
    /** WORK-07: the training week, shaped here beside the stack. */
    val week: WorkoutWeek = WorkoutWeek.DEFAULT,
)

/**
 * PREVIEW/DEMO fixture — a realistic stack across three of the four timings, including one
 * SCHEDULED row so the editor's second branch renders in the gallery.
 */
val sampleStack: List<Supplement> = listOf(
    Supplement("1", "Creatine", "5 g", SupplementTiming.MORNING, taken = false),
    Supplement("2", "Vitamin D3", "2000 IU", SupplementTiming.MORNING, taken = false),
    Supplement(
        id = "7",
        name = "Testosterone",
        dose = "100 mg",
        timing = SupplementTiming.MORNING,
        taken = false,
        schedule = SupplementSchedule.OnDays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
        remindAt = LocalTime(8, 0),
        leads = ReminderLead.DEFAULT,
    ),
    Supplement("4", "Caffeine", "200 mg", SupplementTiming.PRE_WORKOUT, taken = false),
    Supplement("6", "Magnesium", "400 mg", SupplementTiming.EVENING, taken = false),
)

/** Every interaction Settings offers, bundled so the stateless screen keeps its shape. */
data class SettingsActions(
    val onUnitSystem: (UnitSystem) -> Unit = {},
    val onPrepLead: (Int) -> Unit = {},
    /** SET-04/SUPP-08/SUPP-12: one save carries the whole row, schedule and ladder included. */
    val onSaveSupplement: (Supplement) -> Unit = {},
    val onDeleteSupplement: (String) -> Unit = {},
    /** WORK-07: set one day of the training week — label, time and rungs together. */
    val onSaveWorkoutDay: (DayOfWeek, WorkoutDayPlan) -> Unit = { _, _ -> },
)

/** The VM-backed destination (`settings`, SET-01). */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    // SHELL-05: a destination registered directly on the NavHost owns its insets — tabs inherit
    // theirs from AppShell. The wrapper used to sit at the call site in AppNavHost.kt; harness
    // 0.14 requires it HERE, so a destination added later cannot ship inset-less because whoever
    // registered it forgot to wrap it.
    BaseScreen {
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
                    onSaveWorkoutDay = viewModel::onSaveWorkoutDay,
                ),
            )
        }
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
            // WORK-07: the training week, beside the stack. Both are "the routine you keep",
            // and splitting them across two screens would make one of them the forgotten one.
            WorkoutWeekCard(week = ui.week, onSave = actions.onSaveWorkoutDay)
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
    onSave: (Supplement) -> Unit,
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
                    onSave = { edited -> onSave(edited.copy(id = supplement.id)); editing = null },
                    onDelete = { onDelete(supplement.id); editing = null },
                )
            }
        }

        if (editing == NEW) {
            SupplementEditor(
                initial = null,
                onSave = { edited ->
                    // SET-04: a client-minted id, so a double-tapped save replaces one row
                    // rather than creating twins (MEAL-05's reasoning).
                    onSave(edited.copy(id = newSupplementId(stack)))
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
    onSave: (Supplement) -> Unit,
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
                    // SUPP-08: the collapsed row states the schedule, so a Mon/Thu dose is
                    // recognisable without opening its editor.
                    if (supplement.schedule is SupplementSchedule.Daily) supplement.dose
                    else "${supplement.dose}  ·  ${supplement.schedule.label}",
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
    onSave: (Supplement) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var dose by rememberSaveable(initial?.id) { mutableStateOf(initial?.dose ?: "") }
    var timing by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.timing ?: SupplementTiming.MORNING)
    }
    // SUPP-08: the schedule is held as its three parts rather than as the sealed value, so
    // switching branch and back does not lose the days you already picked. Only the branch in
    // effect is read at save.
    var kind by rememberSaveable(initial?.id) { mutableStateOf(initial?.schedule.kind()) }
    var days by rememberSaveable(initial?.id, saver = daysSaver) {
        mutableStateOf((initial?.schedule as? SupplementSchedule.OnDays)?.days ?: emptySet())
    }
    var cadence by rememberSaveable(initial?.id) {
        mutableStateOf((initial?.schedule as? SupplementSchedule.EveryNDays)?.n ?: 2)
    }
    var remindAt by rememberSaveable(initial?.id) { mutableStateOf(initial?.remindAt?.clock() ?: "") }
    var leads by rememberSaveable(initial?.id, saver = leadsSaver) {
        mutableStateOf(initial?.leads ?: ReminderLead.DEFAULT)
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
        // TIMING is when in the DAY. SCHEDULE, below, is which DAYS. Two questions, two rows —
        // folding them together would make a Monday-only evening dose unrepresentable without
        // a timing bucket per weekday (SUPP-08).
        FieldLabel("Timing")
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

        FieldLabel("Schedule")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScheduleKindChoice.entries.forEach { option ->
                ChoicePill(
                    label = option.label,
                    selected = option == kind,
                    tag = "settings_schedule_${option.name}",
                    onClick = { kind = option },
                )
            }
        }
        when (kind) {
            ScheduleKindChoice.DAILY -> Unit
            ScheduleKindChoice.ON_DAYS -> Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DayOfWeek.entries.forEach { day ->
                    ChoicePill(
                        label = day.shortLabel,
                        selected = day in days,
                        tag = "settings_day_${day.name}",
                        onClick = { days = if (day in days) days - day else days + day },
                    )
                }
            }
            ScheduleKindChoice.EVERY_N_DAYS -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // AppIconButton, not a bare pill: these are 48dp-enforced, which the draft's
                // hand-rolled steppers were not — the a11y audit caught exactly that.
                AppIconButton(
                    icon = Icons.Filled.Remove,
                    contentDescription = "Fewer days between doses",
                    onClick = { cadence = (cadence - 1).coerceIn(SupplementSchedule.CADENCE_RANGE) },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { testTag = "settings_cadence_down" },
                )
                Text(
                    "Every $cadence days",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { testTag = "settings_cadence" },
                )
                AppIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "More days between doses",
                    onClick = { cadence = (cadence + 1).coerceIn(SupplementSchedule.CADENCE_RANGE) },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { testTag = "settings_cadence_up" },
                )
            }
        }

        // SUPP-12: the ladder. Blank time = no reminders at all, which is the default for a
        // new row — an alarm nobody asked for is how an app's notifications get switched off.
        FieldLabel("Reminders")
        OutlinedTextField(
            value = remindAt,
            onValueChange = { remindAt = it },
            singleLine = true,
            label = { Text("Time (HH:MM)") },
            placeholder = { Text("08:00") },
            modifier = Modifier.width(160.dp).semantics { testTag = "settings_supplement_time" },
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReminderLead.entries.forEach { lead ->
                // NIGHT_BEFORE is not offered on a daily schedule: "tomorrow is creatine day"
                // is noise, and the rung's whole value is that it names an exception.
                if (lead == ReminderLead.NIGHT_BEFORE && kind == ScheduleKindChoice.DAILY) return@forEach
                ChoicePill(
                    label = lead.label,
                    selected = lead in leads,
                    tag = "settings_lead_${lead.name}",
                    onClick = { leads = if (lead in leads) leads - lead else leads + lead },
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            AppTextButton(
                text = "Save",
                onClick = {
                    onSave(
                        Supplement(
                            id = initial?.id.orEmpty(),
                            name = name,
                            dose = dose,
                            timing = timing,
                            // `taken` is a fact about today, owned by the dose table — never
                            // written from the editor (SUPP-07).
                            taken = initial?.taken ?: false,
                            schedule = kind.build(days, cadence, initial?.schedule),
                            remindAt = remindAt.parseClock(),
                            leads = leads,
                        ),
                    )
                },
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

// ── The training week (WORK-07) ─────────────────────────────────────────────────────────

/**
 * Seven rows, one per weekday, each opening the same editor — the accordion the stack uses.
 *
 * Always seven, never "the training days": a week is a grid (WORK-02), and hiding rest days
 * would make "add Sunday back" a control that has to exist somewhere else.
 */
@Composable
private fun WorkoutWeekCard(week: WorkoutWeek, onSave: (DayOfWeek, WorkoutDayPlan) -> Unit) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    SettingsCard(title = "Workout week", tag = "settings_week") {
        DayOfWeek.entries.forEach { day ->
            val plan = week[day]
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AppButtonDefaults.MinTouchTarget)
                        .semantics { testTag = "settings_workout_${day.name}" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        day.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        plan.rowLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (plan.isTraining) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AppIconButton(
                        icon = Icons.Filled.Edit,
                        contentDescription = "Edit ${day.name.lowercase()}",
                        onClick = { editing = if (editing == day.name) null else day.name },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { testTag = "settings_workout_edit_${day.name}" },
                    )
                }
                if (editing == day.name) {
                    WorkoutDayEditor(
                        initial = plan,
                        onSave = { onSave(day, it); editing = null },
                        onCancel = { editing = null },
                    )
                }
            }
        }
    }
}

/** "Upper body · 18:00", or "Rest" — the collapsed row's whole story. */
private fun WorkoutDayPlan.rowLabel(): String = when {
    !isTraining -> "Rest"
    remindAt != null && leads.isNotEmpty() -> "$label  ·  ${remindAt.clock()}"
    else -> label.orEmpty()
}

/**
 * One day's plan: what it is, when to be reminded, and which rungs.
 *
 * The TIME is per day (WORK-07) — a weekday session after work and a Saturday morning session
 * are the normal shape of a real week, and one time for all seven would be wrong on most of
 * them. Clearing the label is how a day becomes a rest day; there is no separate "rest"
 * control, because a training day with no name is not a thing.
 */
@Composable
private fun WorkoutDayEditor(
    initial: WorkoutDayPlan,
    onSave: (WorkoutDayPlan) -> Unit,
    onCancel: () -> Unit,
) {
    var label by rememberSaveable { mutableStateOf(initial.label.orEmpty()) }
    var time by rememberSaveable { mutableStateOf(initial.remindAt?.clock() ?: "") }
    var leads by rememberSaveable(saver = leadsSaver) {
        mutableStateOf(initial.leads.ifEmpty { ReminderLead.DEFAULT })
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FuelledTokens.RadiusCard))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
            .semantics { testTag = "settings_workout_editor" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            singleLine = true,
            label = { Text("Session") },
            placeholder = { Text("Leave empty for a rest day") },
            modifier = Modifier.fillMaxWidth().semantics { testTag = "settings_workout_label" },
        )
        OutlinedTextField(
            value = time,
            onValueChange = { time = it },
            singleLine = true,
            label = { Text("Time (HH:MM)") },
            placeholder = { Text("18:00") },
            modifier = Modifier.width(160.dp).semantics { testTag = "settings_workout_time" },
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The SAME three rungs the stack offers — one reminder vocabulary across the app.
            ReminderLead.entries.forEach { lead ->
                ChoicePill(
                    label = lead.label,
                    selected = lead in leads,
                    tag = "settings_workout_lead_${lead.name}",
                    onClick = { leads = if (lead in leads) leads - lead else leads + lead },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppTextButton(
                text = "Save",
                onClick = {
                    onSave(
                        WorkoutDayPlan(
                            label = label.trim().ifBlank { null },
                            remindAt = time.parseClock(),
                            leads = leads,
                        ),
                    )
                },
                modifier = Modifier.semantics { testTag = "settings_workout_save" },
            )
            Spacer(Modifier.width(8.dp))
            AppTextButton(text = "Cancel", onClick = onCancel)
        }
    }
}

// ── Editor helpers (SUPP-08/SUPP-12/WORK-07) ────────────────────────────────────────────

/** The editor's schedule branches — the sealed hierarchy as a flat, pickable choice. */
private enum class ScheduleKindChoice(val label: String) {
    DAILY("Daily"),
    ON_DAYS("Days of week"),
    EVERY_N_DAYS("Every N days"),
}

private fun SupplementSchedule?.kind(): ScheduleKindChoice = when (this) {
    is SupplementSchedule.OnDays -> ScheduleKindChoice.ON_DAYS
    is SupplementSchedule.EveryNDays -> ScheduleKindChoice.EVERY_N_DAYS
    else -> ScheduleKindChoice.DAILY
}

/**
 * Rebuild the sealed schedule from the branch in effect.
 *
 * A cadence keeps its ORIGINAL anchor when one already exists: re-saving a name typo must not
 * silently restart an every-other-day cycle from today, which would move every future dose by
 * a day. Only a schedule that was not already a cadence gets today as its anchor.
 */
private fun ScheduleKindChoice.build(
    days: Set<DayOfWeek>,
    cadence: Int,
    previous: SupplementSchedule?,
): SupplementSchedule = when (this) {
    ScheduleKindChoice.DAILY -> SupplementSchedule.Daily
    ScheduleKindChoice.ON_DAYS -> SupplementSchedule.OnDays(days)
    ScheduleKindChoice.EVERY_N_DAYS -> SupplementSchedule.EveryNDays(
        n = cadence,
        anchor = (previous as? SupplementSchedule.EveryNDays)?.anchor ?: todayForAnchor(),
    )
}

/**
 * The anchor a NEW cadence starts from.
 *
 * The wall-clock date, via `core/time`'s named provider. It used to read `Clock.System`
 * inline here, justified as "the one place in this screen that does" — ARCH-13 does not grant
 * per-screen exceptions, and rightly: sixteen files each held one such exception. The reasoning
 * that a stateless editor should not thread a TimeSignal to stamp one date still stands, so the
 * read moved to the provider rather than becoming a parameter.
 *
 * [systemToday] and NOT the logical day, deliberately — see its own note.
 */
private fun todayForAnchor(): LocalDate = systemToday()

/** "08:00" — the one clock format both editors read and write. */
private fun LocalTime.clock(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/**
 * Parse "08:00", or null for anything else — including empty, which IS the way to say "no
 * reminders". Deliberately forgiving rather than validating: a half-typed "8:" while the user
 * is still going means no alarm yet, not an error banner.
 */
private fun String.parseClock(): LocalTime? {
    val parts = trim().split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return LocalTime(hour, minute)
}

/** rememberSaveable needs an explicit saver for a Set — stored as the CSV the DB uses. */
private val leadsSaver: Saver<MutableState<Set<ReminderLead>>, String> = Saver(
    save = { it.value.joinToString(",") { lead -> lead.name } },
    restore = { csv -> mutableStateOf(csv.split(',').mapNotNull(ReminderLead::of).toSet()) },
)

private val daysSaver: Saver<MutableState<Set<DayOfWeek>>, String> = Saver(
    save = { it.value.joinToString(",") { day -> day.name } },
    restore = { csv ->
        mutableStateOf(
            csv.split(',').mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name } }.toSet(),
        )
    },
)

/** A small caption above a field group — the editor's own section rule. */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
