package com.kvdm.fuelled.presentation.profile

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvdm.fuelled.core.format.fixed
import com.kvdm.fuelled.domain.model.Profile
import com.kvdm.fuelled.domain.model.ProfileGoals
import com.kvdm.fuelled.domain.model.ProfileIdentity
import com.kvdm.fuelled.domain.model.WeeklyStats
import com.kvdm.fuelled.presentation.components.ContentStateContainer
import com.kvdm.fuelled.presentation.components.ScreenColumn
import com.kvdm.fuelled.presentation.components.StatTile
import com.kvdm.fuelled.presentation.components.enterRise
import com.kvdm.fuelled.presentation.components.pressable
import com.kvdm.fuelled.presentation.theme.FuelledColors
import org.koin.compose.viewmodel.koinViewModel

// ── Profile: identity, daily goals, weekly stats, settings ────────────────────────────
// The UI-first preview seam, mirroring the Today/Foods exemplars' two entry points:
//   • ProfileScreen — STATELESS, sample-defaulted. The preview registry renders it with no VM.
//   • ProfileRoute  — the VM-backed tab the nav graph hosts: Loading/Content/Error are driven
//     by ProfileViewModel through ContentStateContainer.
// The settings-list labels (units, stack, reminders) are STATIC presentation — not persisted
// domain data (PROF-04). Every row opens Settings (SET-01); a row with no destination is not
// rendered at all (UX-04, motion D14): the tap affordance ships with the destination, never
// before it, and neither does the row.
//
// Motion (D8, D12): the root is the registry's ScreenColumn, and the four cards arrive in a
// stagger (`enterRise`); the tappable header, stats row and settings rows give press feedback
// (`pressable`). All end-state under Instant, so the golden tree sees no motion.

// PREVIEW/DEMO fixture — the screen's preview seam. Not production data: the Room-backed
// ProfileRepositoryImpl seeds its own realistic profile for the VM-backed ProfileRoute.
val sampleProfile = Profile(
    identity = ProfileIdentity(name = "Karel", planLabel = "Cutting", calorieTarget = 2400),
    goals = ProfileGoals(calorieTarget = 2400, proteinGoalG = 180, activity = "Trains 5×/week"),
    weeklyStats = WeeklyStats(streakDays = 12, avgProteinG = 172, weightKg = 82.4),
)

// The settings rows (PROF-04). UX-04 took the tap OFF every one of them because none had a
// destination; SET-01 gave three of them one, and the tap came back WITH it. Motion D14 then
// took the two that still went nowhere ("Connected apps", "Account") off the screen entirely:
// an affordance is a promise, and a list row that does nothing is a row that should not be
// there. Their tags return with the features. So every row here opens Settings — the rule is
// encoded by membership, not by a flag. Labels are presentation, never persisted domain data.
private data class SettingsItem(val label: String, val tag: String)

private val settingsItems = listOf(
    SettingsItem("Units & measurements", "profile_setting_units"),
    SettingsItem("Supplement stack", "profile_setting_stack"),
    SettingsItem("Reminders", "profile_setting_reminders"),
)

/**
 * The VM-backed Profile tab the nav graph hosts. The Loading/Content/Error state machine lives in
 * [ProfileViewModel]; this wrapper only renders it through [ContentStateContainer] (which owns the
 * `profile_loading`/`profile_error`/`profile_retry` arms). A profile always exists, so there is no
 * Empty arm. A tab: it inherits BaseScreen (insets) from AppShell, so it does not re-wrap it
 * (SHELL-05).
 */
@Composable
fun ProfileRoute(
    viewModel: ProfileViewModel = koinViewModel(),
    onOpenWeek: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContentStateContainer(state = state, screenTag = "profile", onRetry = viewModel::load) { profile ->
        ProfileScreen(
            profile = profile,
            onOpenWeek = onOpenWeek,
            onOpenSettings = onOpenSettings,
            onSaveGoals = viewModel::saveGoals,
            onSaveName = viewModel::saveName,
        )
    }
}

/**
 * The stateless profile — the preview/UI-first seam. Renders a [Profile]; defaults to a sample so
 * the preview registry can render it without a VM or Koin. The production path is [ProfileRoute] +
 * [ProfileViewModel]. Calorie/protein rows and the identity header carry their editors
 * (PERS-02/PERS-03 — the affordance arrived WITH the capability, UX-04); the activity and
 * settings rows stay read-only until theirs exist (usability-pass S5).
 */
/** Which editor is open (PERS-02/PERS-03) — one at a time, none by default. */
private enum class ProfileEdit { CALORIES, PROTEIN, NAME }

@Composable
fun ProfileScreen(
    profile: Profile = sampleProfile,
    onOpenWeek: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onSaveGoals: (targetKcal: Int, proteinGoalG: Int) -> Unit = { _, _ -> },
    onSaveName: (String) -> Unit = {},
) {
    var editing by remember { mutableStateOf<ProfileEdit?>(null) }

    // D12: the registry's root (`profile_screen`, PaddingPage, the token self-report) instead
    // of a hand-rolled column. Scrollable: nothing inside scrolls itself.
    ScreenColumn(screenTag = "profile", scrollable = true) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            IdentityHeader(profile.identity, onEdit = { editing = ProfileEdit.NAME }, modifier = Modifier.enterRise(0))
            GoalsCard(
                goals = profile.goals,
                onEditCalories = { editing = ProfileEdit.CALORIES },
                onEditProtein = { editing = ProfileEdit.PROTEIN },
                modifier = Modifier.enterRise(1),
            )
            StatsRow(profile.weeklyStats, onOpenWeek, modifier = Modifier.enterRise(2))
            SettingsList(onOpenSettings, modifier = Modifier.enterRise(3))
        }
    }

    when (editing) {
        ProfileEdit.CALORIES -> ValueEditorDialog(
            title = "Calorie target",
            initial = profile.goals.calorieTarget.toString(),
            suffix = "kcal / day",
            inputTag = "profile_goal_input",
            saveTag = "profile_goal_save",
            onDismiss = { editing = null },
            onSave = { text ->
                text.toIntOrNull()?.let { onSaveGoals(it, profile.goals.proteinGoalG) }
                editing = null
            },
        )
        ProfileEdit.PROTEIN -> ValueEditorDialog(
            title = "Protein goal",
            initial = profile.goals.proteinGoalG.toString(),
            suffix = "g / day",
            inputTag = "profile_goal_input",
            saveTag = "profile_goal_save",
            onDismiss = { editing = null },
            onSave = { text ->
                text.toIntOrNull()?.let { onSaveGoals(profile.goals.calorieTarget, it) }
                editing = null
            },
        )
        ProfileEdit.NAME -> ValueEditorDialog(
            title = "Your name",
            initial = profile.identity.name,
            suffix = null,
            inputTag = "profile_name_input",
            saveTag = "profile_name_save",
            onDismiss = { editing = null },
            onSave = { text ->
                onSaveName(text)
                editing = null
            },
        )
        null -> {}
    }
}

/**
 * One editor for one value (PERS-02/PERS-03) — a dialog, deliberately: the edit is a single
 * field, and a whole screen for one number adds a navigation for nothing. The VM re-guards
 * every save (a dialog cannot be the refusal), so a junk parse here simply closes without
 * writing.
 */
@Composable
private fun ValueEditorDialog(
    title: String,
    initial: String,
    suffix: String?,
    inputTag: String,
    saveTag: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { testTag = "profile_goal_editor" },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                supportingText = suffix?.let { { Text(it) } },
                modifier = Modifier.semantics { testTag = inputTag },
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onSave(text) },
                modifier = Modifier.semantics { testTag = saveTag },
            ) { Text("Save") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun IdentityHeader(identity: ProfileIdentity, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    // PERS-03: the header IS the name's editor door — tap to rename.
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .pressable(interactionSource)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClickLabel = "Edit your name",
                onClick = onEdit,
            )
            .semantics { testTag = "profile_edit_name" },
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(FuelledColors.Primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(identity.name.initials(), style = MaterialTheme.typography.headlineMedium, color = FuelledColors.OnPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(16.dp))
        Column {
            Text(
                identity.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { testTag = "profile_title" },
            )
            Text(
                "${identity.planLabel} · ${identity.calorieTarget.grouped()} kcal target",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GoalsCard(
    goals: ProfileGoals,
    onEditCalories: () -> Unit,
    onEditProtein: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        GoalRow("Calorie target", "${goals.calorieTarget.grouped()} kcal", "profile_goal_calories", onEditCalories)
        Divider()
        GoalRow("Protein goal", "${goals.proteinGoalG} g", "profile_goal_protein", onEditProtein)
        Divider()
        GoalRow("Activity", goals.activity, "profile_goal_activity")
    }
}

// UX-04, both directions: a row with an editor carries the tap (PERS-02 — calories,
// protein); a row without one stays a plain value (activity, until S5). The affordance
// and the capability arrive together, never apart.
@Composable
private fun GoalRow(label: String, value: String, tag: String, onEdit: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onEdit != null) it.clickable(onClickLabel = "Edit $label", onClick = onEdit) else it }
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .semantics { testTag = tag },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = FuelledColors.Primary)
    }
}

// JRN-02: the stats row is a REAL control — it opens Progress, the surface that
// makes its streak/avg-protein claims verifiable. The tap exists because the destination
// does (UX-04's rule, satisfied in the other direction).
@Composable
private fun StatsRow(stats: WeeklyStats, onOpenWeek: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(interactionSource)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClickLabel = "Open progress",
                onClick = onOpenWeek,
            )
            .semantics { testTag = "profile_progress_link" },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatTile(stats.streakDays.toString(), "day streak", Modifier.weight(1f))
        StatTile("${stats.avgProteinG}g", "avg protein", Modifier.weight(1f))
        StatTile(fixed(stats.weightKg, 1), "kg", Modifier.weight(1f))
    }
}

@Composable
private fun SettingsList(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        settingsItems.forEachIndexed { i, item ->
            SettingsRow(item, onOpenSettings)
            if (i < settingsItems.lastIndex) Divider()
        }
    }
}

/**
 * SET-01/UX-04: every row here is a control — it opens Settings, carries a chevron and a tap.
 * A row that would accept a tap and do nothing is not rendered (motion D14), because that is a
 * broken promise, not a placeholder.
 */
@Composable
private fun SettingsRow(item: SettingsItem, onOpenSettings: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClickLabel = "Open ${item.label}",
                onClick = onOpenSettings,
            )
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .semantics { testTag = item.tag },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(
            text = "›",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 18.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/** Initials from a name for the avatar — decorative (no testTag). "Karel van der Merwe" -> "KV". */
private fun String.initials(): String =
    trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercaseChar().toString() }

/** Thousands-grouped integer for display: 2400 -> "2,400". Presentation-only formatting. */
private fun Int.grouped(): String {
    val negative = this < 0
    val digits = kotlin.math.abs(this).toString()
    val sb = StringBuilder()
    for ((i, c) in digits.withIndex()) {
        if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return if (negative) "-$sb" else sb.toString()
}
