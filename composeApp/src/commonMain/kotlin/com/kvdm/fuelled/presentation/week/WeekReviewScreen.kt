package com.kvdm.fuelled.presentation.week

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import com.kvdm.fuelled.domain.model.WeekDay
import com.kvdm.fuelled.domain.model.WeekReview
import com.kvdm.fuelled.presentation.components.AppHeader
import com.kvdm.fuelled.presentation.components.ContentStateContainer
import com.kvdm.fuelled.presentation.components.ScreenColumn
import com.kvdm.fuelled.presentation.components.StatBar
import com.kvdm.fuelled.presentation.components.Tag
import com.kvdm.fuelled.presentation.mealplan.litresLabel
import com.kvdm.fuelled.presentation.theme.FuelledColors
import com.kvdm.fuelled.presentation.theme.FuelledTokens
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

// ── Week in review: the holistic results surface (JRN-01..03) ────────────────────────────
// Born from journey J6 ("how am I actually doing?") — the look BACK that Profile's stats
// claimed but no surface backed. Read-only: fixing a past day stays the plan strip's job.
// Two entry points, the UI-first preview seam mirroring every other feature:
//   • WeekReviewScreen — STATELESS, sample-defaulted. The preview registry renders it, no VM.
//   • WeekReviewRoute  — the VM-backed nav destination (`week`), driven by WeekReviewViewModel.

// PREVIEW/DEMO fixture — fixed dates, never a clock read (ARCH-12): a realistic training
// week ending on the fixed "today", with one weak day and one perfect day, so the surface's
// range is visible in one render.
val sampleWeek = WeekReview(
    days = listOf(
        WeekDay(LocalDate(2026, 7, 16), false, 2350, 2400, 176, 180, 6, 6, 3000, 2),
        WeekDay(LocalDate(2026, 7, 17), false, 2180, 2400, 168, 180, 5, 6, 2500, 2),
        WeekDay(LocalDate(2026, 7, 18), false, 2410, 2400, 181, 180, 6, 6, 3000, 3),
        WeekDay(LocalDate(2026, 7, 19), false, 1580, 2400, 92, 180, 2, 6, 1000, 0),
        WeekDay(LocalDate(2026, 7, 20), false, 2290, 2400, 174, 180, 6, 6, 2500, 2),
        WeekDay(LocalDate(2026, 7, 21), false, 2330, 2400, 179, 180, 5, 6, 3000, 2),
        WeekDay(LocalDate(2026, 7, 22), true, 1461, 2400, 121, 180, 2, 6, 1000, 1),
    ),
)

/**
 * The VM-backed week destination the nav graph hosts (`week`, JRN-02). No retry: the state
 * is observed and heals on the source's next emission (RS-01).
 */
@Composable
fun WeekReviewRoute(
    onBack: () -> Unit,
    viewModel: WeekReviewViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContentStateContainer(state = state, screenTag = "week") { week ->
        WeekReviewScreen(week = week, onBack = onBack)
    }
}

/** "Mon 20", or "Today" for the marked day — the strip's idiom, reused mentally not literally. */
internal fun WeekDay.rowLabel(): String =
    if (isToday) "Today"
    else "${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${date.day}"

/**
 * The stateless week — the preview/UI-first seam (JRN-01). Seven rows ascending, today
 * last and marked; each row is one day's honest result. Values only — no advice, no
 * grading colors beyond the app's existing macro language: the audience (intent: lifters
 * who think in macros) reads numbers faster than judgments.
 */
@Composable
fun WeekReviewScreen(
    week: WeekReview = sampleWeek,
    onBack: () -> Unit = {},
) {
    ScreenColumn(screenTag = "week") {
        AppHeader(title = "Week in review", screenTag = "week", onBack = onBack)
        Text(
            text = "Last 7 days · targets are your current goals",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { testTag = "week_caption" },
        )
        Spacer(Modifier.padding(top = FuelledTokens.GapCard))
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(FuelledTokens.GapCard),
        ) {
            // The HEADLINE first: the week's verdict, before a single day card asks to be
            // read. Protein is the audience's daily question, so it leads.
            WeekSummaryCard(week)
            week.days.forEach { day -> WeekDayCard(day) }
            Spacer(Modifier.padding(bottom = 8.dp))
        }
    }
}

/** The week's verdict in one card: protein days hit, meals kept, average intake. */
@Composable
private fun WeekSummaryCard(week: WeekReview) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .semantics { testTag = "week_summary" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryStat(
            value = "${week.proteinDaysHit}/${week.proteinDaysJudged}",
            label = "protein days",
            color = FuelledColors.Protein,
        )
        SummaryStat(
            value = "${week.mealsDone}/${week.mealsTotal}",
            label = "meals kept",
            color = FuelledColors.Primary,
        )
        SummaryStat(
            value = "${week.avgConsumedKcal}",
            label = "avg kcal",
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SummaryStat(value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = color,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One day's results card. Today gets the focused-container border — same signal, same color. */
@Composable
private fun WeekDayCard(day: WeekDay) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .let {
                if (day.isToday) it.border(1.dp, FuelledColors.Primary, RoundedCornerShape(20.dp)) else it
            }
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .semantics { testTag = "week_day_${day.date}" },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = day.rowLabel(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${day.consumedKcal} / ${day.targetKcal} kcal",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        StatBar(
            progress = if (day.proteinGoalG <= 0) 0f else day.proteinG.toFloat() / day.proteinGoalG,
            color = FuelledColors.Protein,
            label = "Protein",
            valueText = "${day.proteinG} / ${day.proteinGoalG}g",
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FuelledTokens.GapCard),
        ) {
            Tag("MEALS", "${day.slotsDone}/${day.slotsTotal}", FuelledColors.Primary)
            Tag("WATER", "${day.waterMl.litresLabel()} L", FuelledColors.Info)
            Tag("VEG", "${day.vegMeals}", FuelledColors.Success)
            Spacer(Modifier.width(0.dp))
        }
    }
}
