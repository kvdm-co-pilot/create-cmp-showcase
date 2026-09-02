package com.kvdm.fuelled.presentation.workouts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvdm.fuelled.domain.model.WorkoutDay
import com.kvdm.fuelled.domain.model.WorkoutDayPlan
import com.kvdm.fuelled.domain.model.WorkoutDayState
import com.kvdm.fuelled.presentation.components.AppHeader
import com.kvdm.fuelled.presentation.components.ContentStateContainer
import com.kvdm.fuelled.presentation.components.ListItemCard
import com.kvdm.fuelled.presentation.components.ScreenColumn
import com.kvdm.fuelled.presentation.components.enterRise
import com.kvdm.fuelled.presentation.theme.FuelledColors
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.koin.compose.viewmodel.koinViewModel

// ── Training: the training week, as a tab (NAV-06) ───────────────────────────────────────
// The UI-first preview seam, mirroring the exemplar's two entry points:
//   • WorkoutWeekScreen — STATELESS, sample-defaulted; the preview registry renders it with no VM.
//   • WorkoutWeekRoute  — the VM-backed tab the nav graph hosts.
//
// This screen answers "what does my training week look like" — the question that had no home
// before it: WORK-03 put the day's session on Today, WORK-05 put a retrospective strip inside
// Progress, and WORK-07 put an editor inside Settings, but the WEEK itself was nowhere.

/**
 * PREVIEW/DEMO fixture — a mid-week state that communicates most: two sessions kept, one
 * missed, today pending and tickable, a rest day in the middle. Fixed dates, never a clock
 * read, so gallery renders and golden diffs stay deterministic (ARCH-12).
 */
val sampleWorkoutWeek = WorkoutWeekUi(
    today = LocalDate(2026, 7, 22),
    days = listOf(
        WorkoutDay(LocalDate(2026, 7, 20), WorkoutDayPlan("Upper body", LocalTime(18, 0)), done = true),
        WorkoutDay(LocalDate(2026, 7, 21), WorkoutDayPlan("Cardio 20 min", LocalTime(18, 0)), done = true),
        WorkoutDay(LocalDate(2026, 7, 22), WorkoutDayPlan("Upper body", LocalTime(18, 0)), done = false),
        WorkoutDay(LocalDate(2026, 7, 23), WorkoutDayPlan("Cardio 20 min", LocalTime(18, 0)), done = false),
        WorkoutDay(LocalDate(2026, 7, 24), WorkoutDayPlan("Lower body", LocalTime(18, 0)), done = false),
        WorkoutDay(LocalDate(2026, 7, 25), WorkoutDayPlan("Cardio 20 min", LocalTime(9, 0)), done = false),
        WorkoutDay(LocalDate(2026, 7, 26), WorkoutDayPlan(), done = false),
    ),
)

/** A rest-heavy week that has not started — the forced-state variant the registry renders. */
val sampleWorkoutWeekFresh = sampleWorkoutWeek.copy(
    days = sampleWorkoutWeek.days.map { it.copy(done = false) },
    today = LocalDate(2026, 7, 20),
)

/**
 * The VM-backed Training tab the nav graph hosts. A tab: it inherits BaseScreen (insets) from
 * AppShell, so it does not re-wrap it (SHELL-05).
 */
@Composable
fun WorkoutWeekRoute(
    viewModel: WorkoutWeekViewModel = koinViewModel(),
    onEditWeek: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContentStateContainer(state = state, screenTag = "training") { model ->
        WorkoutWeekScreen(
            model = model,
            onToggleTodayDone = viewModel::onToggleTodayDone,
            onEditWeek = onEditWeek,
        )
    }
}

/**
 * The stateless training week — the preview/UI-first seam, defaulted to a sample so the
 * registry renders it without a VM or Koin.
 *
 * Every day renders, rest included. On Today a rest day shows NOTHING (workouts D5) because a
 * rest day has nothing to say about right now — but here the shape of the WEEK is the point,
 * and a gap in it would read as missing data rather than as a planned rest.
 */
@Composable
fun WorkoutWeekScreen(
    model: WorkoutWeekUi = sampleWorkoutWeek,
    onToggleTodayDone: (Boolean) -> Unit = {},
    onEditWeek: () -> Unit = {},
) {
    ScreenColumn(screenTag = "training") {
        AppHeader(title = "Training", screenTag = "training")
        Text(
            text = model.summaryLabel(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { testTag = "training_summary" },
        )

        // D8: the day cards rise in a stagger.
        model.days.forEachIndexed { i, day ->
            val state = day.state(model.today)
            val isToday = day.date == model.today
            ListItemCard(
                title = day.date.weekdayLabel() + (day.plan.label?.let { " · $it" } ?: " · Rest"),
                subtitle = day.caption(state),
                // WORK-04: only the current logical day is tickable — a week view that could
                // retro-tick Tuesday would be inventing a fact nobody observed. Every other
                // row opens the week editor instead of silently doing nothing.
                onClick = { if (isToday && day.plan.isTraining) onToggleTodayDone(!day.done) else onEditWeek() },
                leading = {
                    Icon(
                        Icons.Filled.FitnessCenter,
                        contentDescription = null,
                        tint = when (state) {
                            WorkoutDayState.DONE -> FuelledColors.Primary
                            WorkoutDayState.MISSED -> FuelledColors.Error
                            WorkoutDayState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                            WorkoutDayState.REST -> MaterialTheme.colorScheme.outline
                        },
                    )
                },
                trailing = {
                    if (state == WorkoutDayState.DONE) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = FuelledColors.Primary)
                    }
                },
                // Keyed by WEEKDAY, not by date. `training_day_2026-07-20` is unpredictable to
                // anything that does not already know today's date, which makes it unusable as
                // an e2e selector — the flows select by testTag, never by display text, so the
                // tag has to be something a flow can name in advance. The current day also
                // carries `training_today`, so a flow can reach "the tickable row" without
                // computing which weekday that is.
                modifier = Modifier.enterRise(i).semantics {
                    testTag = if (isToday) "training_today" else "training_day_${day.date.dayOfWeek.name.lowercase().take(3)}"
                },
            )
        }

        // The editor lives in Settings (workouts WORK-07, navigation-ia OD3). This used to be a
        // line of grey text saying so, which made the editor reachable only by tapping a row
        // that ISN'T today — an affordance you find by accident or not at all.
        ListItemCard(
            title = "Shape the week",
            subtitle = "Labels, times and rest days — in Settings",
            onClick = onEditWeek,
            leading = {
                Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailing = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier.enterRise(model.days.size).semantics { testTag = "training_edit_week" },
        )
    }
}

/**
 * The row's supporting line: the state in words, plus the reminder time when there is one.
 * Presentation formatting of domain values — the model carries a [WorkoutDayState] and a
 * [LocalTime], never their rendering.
 */
private fun WorkoutDay.caption(state: WorkoutDayState): String = when (state) {
    WorkoutDayState.DONE -> "Done"
    WorkoutDayState.MISSED -> "Missed"
    WorkoutDayState.REST -> "Rest day"
    WorkoutDayState.PENDING -> plan.remindAt?.let { "Reminder ${it.clock()}" } ?: "Planned"
}

private fun LocalTime.clock(): String = "${hour.pad()}:${minute.pad()}"

private fun Int.pad(): String = toString().padStart(2, '0')

/** "Mon", "Tue", … — presentation formatting of the date's weekday. */
private fun LocalDate.weekdayLabel(): String =
    dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)

/**
 * The week's tally in words: what you kept, what you missed, what is still ahead.
 *
 * Only the parts that are non-zero appear — "2 kept · 4 to come" mid-week, "5 kept · 1 missed"
 * once the week is over, "6 kept" on a perfect one. A summary that always printed all three
 * would put "0 missed" in front of someone who has missed nothing, which is the kind of line
 * that makes a screen feel like it is keeping score against you.
 */
internal fun WorkoutWeekUi.summaryLabel(): String {
    val parts = buildList {
        add("$kept kept")
        if (missed > 0) add("$missed missed")
        if (toCome > 0) add("$toCome to come")
    }
    return parts.joinToString(" · ")
}
