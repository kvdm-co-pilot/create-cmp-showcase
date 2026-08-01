package com.kvdm.fuelled.presentation.profile

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.kvdm.fuelled.core.format.fixed
import com.kvdm.fuelled.domain.model.Profile
import com.kvdm.fuelled.domain.model.ProfileGoals
import com.kvdm.fuelled.domain.model.ProfileIdentity
import com.kvdm.fuelled.domain.model.WeeklyStats
import com.kvdm.fuelled.presentation.components.ContentStateContainer
import com.kvdm.fuelled.presentation.components.StatTile
import com.kvdm.fuelled.presentation.theme.FuelledColors
import org.koin.compose.viewmodel.koinViewModel

// ── Profile: identity, daily goals, weekly stats, settings ────────────────────────────
// The UI-first preview seam, mirroring the Today/Foods exemplars' two entry points:
//   • ProfileScreen — STATELESS, sample-defaulted. The preview registry renders it with no VM.
//   • ProfileRoute  — the VM-backed tab the nav graph hosts: Loading/Content/Error are driven
//     by ProfileViewModel through ContentStateContainer.
// The settings-list labels (units, reminders, connected apps, account) are STATIC presentation —
// not persisted domain data (PROF-04). Their destinations do not exist yet, so the rows are
// READ-ONLY (UX-04): the tap affordance ships with the destination, never before it.

// PREVIEW/DEMO fixture — the screen's preview seam. Not production data: the Room-backed
// ProfileRepositoryImpl seeds its own realistic profile for the VM-backed ProfileRoute.
val sampleProfile = Profile(
    identity = ProfileIdentity(name = "Karel", planLabel = "Cutting", calorieTarget = 2400),
    goals = ProfileGoals(calorieTarget = 2400, proteinGoalG = 180, activity = "Trains 5×/week"),
    weeklyStats = WeeklyStats(streakDays = 12, avgProteinG = 172, weightKg = 82.4),
)

// The static settings rows (PROF-04, as amended by UX-04): a stable testTag, READ-ONLY —
// no clickable until a destination exists. A row that accepted the tap and did nothing was
// a broken promise, not a placeholder. Labels are presentation, never persisted domain data.
private data class SettingsItem(val label: String, val tag: String)

private val settingsItems = listOf(
    SettingsItem("Units & measurements", "profile_setting_units"),
    SettingsItem("Reminders", "profile_setting_reminders"),
    SettingsItem("Connected apps", "profile_setting_connected"),
    SettingsItem("Account", "profile_setting_account"),
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
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContentStateContainer(state = state, screenTag = "profile", onRetry = viewModel::load) { profile ->
        ProfileScreen(profile = profile, onOpenWeek = onOpenWeek)
    }
}

/**
 * The stateless profile — the preview/UI-first seam. Renders a [Profile]; defaults to a sample so
 * the preview registry can render it without a VM or Koin. The production path is [ProfileRoute] +
 * [ProfileViewModel]. Goal rows (PROF-02) and settings rows (PROF-04) are READ-ONLY values
 * until their editors exist (UX-04): the tap affordance returns with the personalization and
 * settings slices (usability-pass S1/S5), together with the destinations it promises.
 */
@Composable
fun ProfileScreen(
    profile: Profile = sampleProfile,
    onOpenWeek: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .semantics { testTag = "profile_screen" },
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        IdentityHeader(profile.identity)
        GoalsCard(profile.goals)
        StatsRow(profile.weeklyStats, onOpenWeek)
        SettingsList()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun IdentityHeader(identity: ProfileIdentity) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
private fun GoalsCard(goals: ProfileGoals) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        GoalRow("Calorie target", "${goals.calorieTarget.grouped()} kcal", "profile_goal_calories")
        Divider()
        GoalRow("Protein goal", "${goals.proteinGoalG} g", "profile_goal_protein")
        Divider()
        GoalRow("Activity", goals.activity, "profile_goal_activity")
    }
}

// UX-04: a VALUE row, deliberately not clickable and with no chevron — the affordance
// returns with the editor it promises (usability-pass S1). Until then the row says what is
// true and suggests nothing it cannot do.
@Composable
private fun GoalRow(label: String, value: String, tag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .semantics { testTag = tag },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = FuelledColors.Primary)
    }
}

// JRN-02: the stats row is a REAL control — it opens the Week in review, the surface that
// makes its streak/avg-protein claims verifiable. The tap exists because the destination
// does (UX-04's rule, satisfied in the other direction).
@Composable
private fun StatsRow(stats: WeeklyStats, onOpenWeek: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClickLabel = "Open week in review", onClick = onOpenWeek)
            .semantics { testTag = "profile_week_link" },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatTile(stats.streakDays.toString(), "day streak", Modifier.weight(1f))
        StatTile("${stats.avgProteinG}g", "avg protein", Modifier.weight(1f))
        StatTile(fixed(stats.weightKg, 1), "kg", Modifier.weight(1f))
    }
}

@Composable
private fun SettingsList() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        settingsItems.forEachIndexed { i, item ->
            SettingsRow(item)
            if (i < settingsItems.lastIndex) Divider()
        }
    }
}

// UX-04: read-only, like GoalRow — no clickable, no chevron, no false promise.
@Composable
private fun SettingsRow(item: SettingsItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .semantics { testTag = item.tag },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
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
