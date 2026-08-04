package com.kvdm.fuelled.presentation.supplements

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvdm.fuelled.domain.model.ReminderLead
import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.domain.model.SupplementSchedule
import com.kvdm.fuelled.domain.model.SupplementTiming
import com.kvdm.fuelled.domain.model.label
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import com.kvdm.fuelled.presentation.components.ContentStateContainer
import com.kvdm.fuelled.presentation.components.StatBar
import com.kvdm.fuelled.presentation.theme.FuelledColors
import org.koin.compose.viewmodel.koinViewModel

// ── Supplements: today's stack, grouped by timing, tap-to-take (persisted) ────────────
// The UI-first preview seam, mirroring the Foods/Today exemplars' two entry points:
//   • SupplementsScreen — STATELESS, sample-defaulted. The preview registry renders it with
//     no VM (tap is a no-op in the preview seam).
//   • SupplementsRoute  — the VM-backed tab the nav graph hosts: Loading/Content/Empty/Error
//     are driven by SupplementsViewModel through ContentStateContainer, and tap-to-take
//     persists to Room. A tab: it inherits BaseScreen (insets) from AppShell, so it does not
//     re-wrap it (SHELL-05).

// PREVIEW/DEMO fixture — the screen's preview seam. Not production data: the Room-backed
// SupplementRepositoryImpl seeds its own stack for the VM-backed SupplementsRoute.
val sampleSupplementStack = SupplementStackUi(
    groups = listOf(
        SupplementGroup(
            "Morning",
            listOf(
                Supplement("1", "Creatine", "5 g", SupplementTiming.MORNING, taken = true),
                Supplement("2", "Vitamin D3", "2000 IU", SupplementTiming.MORNING, taken = true),
                // SUPP-08/SUPP-12: a scheduled dose, due today, with its ladder set — the one
                // fixture row that exercises the caption the plain rows never show.
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
            ),
        ),
        SupplementGroup(
            "Pre-workout",
            listOf(
                Supplement("4", "Caffeine", "200 mg", SupplementTiming.PRE_WORKOUT, taken = false),
                Supplement("5", "Beta-alanine", "3 g", SupplementTiming.PRE_WORKOUT, taken = false),
            ),
        ),
        SupplementGroup(
            "Evening",
            listOf(Supplement("6", "Magnesium", "400 mg", SupplementTiming.EVENING, taken = false)),
        ),
    ),
    takenCount = 2,
    total = 6,
    // SUPP-09: the off-day half of the split. Present in the default fixture on purpose — a
    // state that only renders in a variant is a state nobody reviews.
    resting = listOf(
        RestingSupplement(
            supplement = Supplement(
                id = "8",
                name = "Injection pen",
                dose = "0.5 ml",
                timing = SupplementTiming.MORNING,
                taken = false,
                schedule = SupplementSchedule.EveryNDays(2, LocalDate(2026, 8, 5)),
                remindAt = LocalTime(7, 30),
                leads = ReminderLead.DEFAULT,
            ),
            nextDue = LocalDate(2026, 8, 5),
        ),
    ),
    today = LocalDate(2026, 8, 4),
)

/**
 * The VM-backed Supplements tab the nav graph hosts. The Loading/Content/Empty/Error state
 * machine lives in [SupplementsViewModel]; this wrapper only renders it through
 * [ContentStateContainer] (which owns the `supplements_loading`/`supplements_empty`/
 * `supplements_error` arms). Tap-to-take routes to the VM, which persists and re-reads
 * (SUPP-03).
 *
 * No onRetry: the state is observed, so a transient failure recovers on the next emission —
 * the same reason Today and Plan dropped theirs. What sat here was a retry button wired to
 * `{}`: it looked like the way out of an error and did nothing at all (SUPP-05).
 */
@Composable
fun SupplementsRoute(
    viewModel: SupplementsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContentStateContainer(state = state, screenTag = "supplements") { stack ->
        SupplementsScreen(stack = stack, onToggleTaken = viewModel::onToggleTaken)
    }
}

/**
 * The stateless stack — the preview/UI-first seam. Renders a [SupplementStackUi]; defaults to a
 * sample so the preview registry can render it without a VM or Koin. The production path is
 * [SupplementsRoute] + [SupplementsViewModel]; there, tap-to-take persists to Room.
 */
@Composable
fun SupplementsScreen(
    stack: SupplementStackUi = sampleSupplementStack,
    onToggleTaken: (id: String, taken: Boolean) -> Unit = { _, _ -> },
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .semantics { testTag = "supplements_screen" },
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Column {
            Text(
                text = "Supplements",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { testTag = "supplements_title" },
            )
            // SUPP-09: the subtitle names the day, because the stack now DEPENDS on the day.
            Text(
                text = stack.today?.let { "Due ${it.dayLabel()}" } ?: "Today's stack",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { testTag = "supplements_day" },
            )
        }

        TakenSummary(taken = stack.takenCount, total = stack.total, progress = stack.progress)

        stack.groups.forEach { group ->
            TimingGroup(group, onToggleTaken)
        }

        // SUPP-09: what is on the stack but not due today. Rendered rather than hidden — "did I
        // already take it, or is it not a dose day?" is the exact anxiety a Mon/Thu injection
        // creates, and a date answers it where an absence does not.
        if (stack.resting.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.semantics { testTag = "supplements_resting" },
            ) {
                Text(
                    "NOT TODAY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                stack.resting.forEach { RestingRow(it) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** "Monday 4 Aug" — the day the due list belongs to. */
private fun LocalDate.dayLabel(): String {
    val weekday = dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    val month = month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    return "$weekday $day $month"
}

@Composable
private fun TakenSummary(taken: Int, total: Int, progress: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp)
            .semantics { testTag = "supplements_summary" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$taken", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.size(6.dp))
            // SUPP-09: "due today", not "in the stack". The denominator counts only what today
            // actually asks for, so an off-day pen can never read as a dose you missed.
            Text(
                "of $total due today taken",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        StatBar(progress = progress, color = FuelledColors.Primary)
    }
}

@Composable
private fun TimingGroup(group: SupplementGroup, onToggleTaken: (id: String, taken: Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            group.timing.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        group.items.forEach { supp ->
            SupplementRow(supp = supp, onToggle = { onToggleTaken(supp.id, !supp.taken) })
        }
    }
}

@Composable
private fun SupplementRow(supp: Supplement, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                supp.name.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = FuelledColors.Primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(supp.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            // SUPP-09: dose, then the schedule and reminder time ONLY when there is one — so a
            // daily vitamin's row is pixel-identical to what it was before schedules existed,
            // and the feature costs the common case nothing.
            Text(
                supp.caption(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(48.dp).semantics { testTag = "supplements_take_${supp.id}" },
        ) {
            val ring = if (supp.taken) FuelledColors.Primary else MaterialTheme.colorScheme.surfaceContainerHigh
            val tick = if (supp.taken) FuelledColors.OnPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(Modifier.size(28.dp).clip(CircleShape).background(ring), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = if (supp.taken) "Taken" else "Mark taken",
                    tint = tick,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * A supplement that is not due today (SUPP-09): visible, dated, and with NO take control.
 *
 * The missing control is the design, not an omission. Taking a dose off-schedule is a real
 * thing people do, but it is a decision — it belongs in the editor where the schedule can be
 * corrected, not one mis-tap away on the screen you open every morning.
 */
@Composable
private fun RestingRow(resting: RestingSupplement) {
    val supp = resting.supplement
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .alpha(0.72f)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { testTag = "supplements_resting_${supp.id}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(supp.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "${supp.dose}  ·  ${supp.schedule.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            // A schedule with no days selected has no next date, and says so rather than
            // inventing one — the editor can hold that state mid-edit.
            resting.nextDue?.let { "Next · ${it.shortLabel()}" } ?: "No days set",
            style = MaterialTheme.typography.labelLarge,
            color = FuelledColors.Primary,
        )
    }
}

/** "Tue 5 Aug" — compact enough to sit at the end of a row. */
private fun LocalDate.shortLabel(): String {
    val weekday = dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    val month = month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    return "$weekday $day $month"
}

/** Dose, plus the schedule and reminder time when this row has them (SUPP-09). */
private fun Supplement.caption(): String = buildString {
    append(dose)
    if (schedule !is SupplementSchedule.Daily) append("  ·  ${schedule.label}")
    if (remindAt != null && leads.isNotEmpty()) {
        append("  ·  reminds ${remindAt.hour.pad()}:${remindAt.minute.pad()}")
    }
}

private fun Int.pad(): String = toString().padStart(2, '0')
