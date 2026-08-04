package com.kvdm.fuelled.presentation.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvdm.fuelled.domain.model.History
import com.kvdm.fuelled.domain.model.TREND_WEEKS
import com.kvdm.fuelled.domain.model.UnitSystem
import com.kvdm.fuelled.domain.model.WeekDay
import com.kvdm.fuelled.domain.model.WeekReview
import com.kvdm.fuelled.domain.model.WeekTrend
import com.kvdm.fuelled.domain.model.WeightEntry
import com.kvdm.fuelled.domain.model.WeightLog
import com.kvdm.fuelled.domain.model.WorkoutDay
import com.kvdm.fuelled.domain.model.WorkoutDayPlan
import com.kvdm.fuelled.domain.model.WorkoutDayState
import com.kvdm.fuelled.domain.model.weightFromKg
import com.kvdm.fuelled.domain.model.weightToKg
import com.kvdm.fuelled.presentation.components.AppHeader
import com.kvdm.fuelled.presentation.components.AppTextButton
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
//   • ProgressScreen — STATELESS, sample-defaulted. The preview registry renders it, no VM.
//   • ProgressRoute  — the VM-backed nav destination (`week`), driven by ProgressViewModel.

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
 * PREVIEW/DEMO fixture for the trend — the four weeks ending on the fixed "today", 2026-07-22
 * (ARCH-12: fixed dates, never a clock read). Exactly [TREND_DAYS] distinct days, so the
 * chunking yields [TREND_WEEKS] rows and no date appears twice.
 *
 * The FIRST week is deliberately empty: "no data" (HIST-05) is the state a real user sees for
 * their first month, so it belongs in the render a human signs, not only in a test.
 */
private fun emptyDay(date: LocalDate) = WeekDay(date, false, 0, 2400, 0, 180, 0, 6, 0, 0)

private fun loggedDay(date: LocalDate, kcal: Int, protein: Int, meals: Int, waterMl: Int, veg: Int) =
    WeekDay(date, false, kcal, 2400, protein, 180, meals, 6, waterMl, veg)

val sampleHistory: History = History(
    days = buildList {
        // Jun 25 – Jul 1: before this install existed.
        (25..30).forEach { add(emptyDay(LocalDate(2026, 6, it))) }
        add(emptyDay(LocalDate(2026, 7, 1)))
        // Jul 2 – Jul 8: the first week, finding the rhythm.
        add(loggedDay(LocalDate(2026, 7, 2), 1980, 141, 4, 2000, 1))
        add(loggedDay(LocalDate(2026, 7, 3), 2050, 152, 5, 2500, 2))
        add(loggedDay(LocalDate(2026, 7, 4), 1640, 98, 3, 1500, 0))
        add(loggedDay(LocalDate(2026, 7, 5), 2210, 168, 5, 2500, 2))
        add(loggedDay(LocalDate(2026, 7, 6), 2090, 155, 5, 3000, 2))
        add(loggedDay(LocalDate(2026, 7, 7), 1870, 133, 4, 2000, 1))
        add(loggedDay(LocalDate(2026, 7, 8), 2260, 174, 6, 3000, 2))
        // Jul 9 – Jul 15: settled into it.
        add(loggedDay(LocalDate(2026, 7, 9), 2300, 176, 6, 3000, 2))
        add(loggedDay(LocalDate(2026, 7, 10), 2380, 182, 6, 3000, 3))
        add(loggedDay(LocalDate(2026, 7, 11), 2270, 171, 5, 2500, 2))
        add(loggedDay(LocalDate(2026, 7, 12), 2410, 185, 6, 3000, 2))
        add(loggedDay(LocalDate(2026, 7, 13), 2190, 166, 5, 2500, 2))
        add(loggedDay(LocalDate(2026, 7, 14), 2340, 179, 6, 3000, 2))
        add(loggedDay(LocalDate(2026, 7, 15), 2280, 177, 6, 3000, 3))
        // Jul 16 – Jul 22: the week the verdict above is about — the SAME seven rows the day
        // cards render, because they are the same list (HIST-01).
        addAll(sampleWeek.days)
    },
)

/** PREVIEW fixture: two weigh-ins, so the change line (HIST-08) is visible in the render. */
val sampleWeightLog: WeightLog = WeightLog(
    listOf(
        WeightEntry(LocalDate(2026, 6, 25), 84.2),
        WeightEntry(LocalDate(2026, 7, 22), 82.8),
    ),
)

/**
 * PREVIEW fixture: a training week with all four dot states in it (WORK-05).
 *
 * Deliberately not a perfect week. A fixture where everything is ticked renders one state and
 * proves one state; this one carries a done day, a missed day, a rest day and today-pending,
 * so every arm of the strip is visible in the gallery and pinned by the golden tree.
 *
 * The dates end on the same day [sampleHistory]'s week does — the two are ALIGNED reads, and a
 * fixture that let them drift would be showing a shape production can never produce.
 */
val sampleTrainingWeek: List<WorkoutDay> = listOf(
    WorkoutDay(LocalDate(2026, 7, 16), WorkoutDayPlan("Upper body"), done = true),
    WorkoutDay(LocalDate(2026, 7, 17), WorkoutDayPlan("Cardio"), done = true),
    WorkoutDay(LocalDate(2026, 7, 18), WorkoutDayPlan("Lower body"), done = false),
    WorkoutDay(LocalDate(2026, 7, 19), WorkoutDayPlan(), done = false),
    WorkoutDay(LocalDate(2026, 7, 20), WorkoutDayPlan("Upper body"), done = true),
    WorkoutDay(LocalDate(2026, 7, 21), WorkoutDayPlan("Cardio"), done = true),
    WorkoutDay(LocalDate(2026, 7, 22), WorkoutDayPlan("Lower body"), done = false),
)

/**
 * The VM-backed Progress destination the nav graph hosts (`progress`, JRN-02/HIST-01). No
 * retry: the state is observed and heals on the source's next emission (RS-01).
 */
@Composable
fun ProgressRoute(
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    viewModel: ProgressViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContentStateContainer(state = state, screenTag = "week") { progress ->
        ProgressScreen(
            progress = progress,
            onBack = onBack,
            onOpenDay = onOpenDay,
            onRecordWeight = viewModel::onWeightRecorded,
        )
    }
}

/**
 * Everything the surface renders (HIST-01), in one shape so the screen stays stateless and
 * the registry can render it without a ViewModel.
 *
 * The history and the weight log arrive already ALIGNED to the same window — both anchored on
 * the current logical day by their use cases — so the trend and the weight beside it can never
 * be describing different fortnights.
 */
data class ProgressUi(
    val history: History = sampleHistory,
    val weight: WeightLog = sampleWeightLog,
    val units: UnitSystem = UnitSystem.METRIC,
    /**
     * WORK-05: the same seven days, as training.
     *
     * A third aligned read beside the history and the weight log — anchored on the same
     * current logical day, so the strip and the day cards cannot be describing different
     * weeks. Empty when the week could not be read, which renders as no training section at
     * all rather than as a week of zero sessions.
     */
    val training: List<WorkoutDay> = sampleTrainingWeek,
)

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
fun ProgressScreen(
    progress: ProgressUi = ProgressUi(),
    onBack: () -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
    onRecordWeight: (Double) -> Unit = {},
) {
    val week = progress.history.week
    ScreenColumn(screenTag = "week") {
        AppHeader(title = "Progress", screenTag = "week", onBack = onBack)
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
            // HIST-01's order is the order of the questions people actually ask, widest
            // first: "how am I doing?" → "am I getting anywhere?" → "is it working?" →
            // "what happened on Sunday?". The day cards are LAST because they are the
            // detail you drill into, not the thing you open the screen for.
            WeekSummaryCard(week)
            // WORK-05: training joins the verdict, immediately after it — it is a result of
            // the week, not a detail of a day, and it answers the same "how am I doing?".
            if (progress.training.any { it.isTraining }) {
                TrainingSummaryCard(progress.training)
            }
            TrendSection(progress.history)
            WeightSection(log = progress.weight, units = progress.units, onRecord = onRecordWeight)
            SectionLabel("THE LAST SEVEN DAYS", "week_days_label")
            week.days.forEach { day ->
                WeekDayCard(
                    day = day,
                    training = progress.training.firstOrNull { it.date == day.date },
                    onOpen = { onOpenDay(day.date) },
                )
            }
            Spacer(Modifier.padding(bottom = 8.dp))
        }
    }
}

/** A quiet divider between the surface's four answers — they are different questions. */
@Composable
private fun SectionLabel(text: String, tag: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { testTag = tag },
    )
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

/**
 * One day's results card — and a DOOR (HIST-02).
 *
 * The whole finding behind this slice: the card could tell you Sunday was 1580 kcal and 92 g
 * protein and then offered nothing to do about it. It opens that day's plan rather than a
 * read-only viewer, because the reason you open Sunday is usually to fix it — back-fill the
 * meal you forgot to log (history decision D2).
 *
 * Today gets the focused-container border — same signal, same color.
 */
@Composable
private fun WeekDayCard(day: WeekDay, training: WorkoutDay? = null, onOpen: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .let {
                if (day.isToday) it.border(1.dp, FuelledColors.Primary, RoundedCornerShape(20.dp)) else it
            }
            .clickable(onClickLabel = "Open ${day.rowLabel()}", onClick = onOpen)
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
            // WORK-05: the training tag exists only on a TRAINING day. A rest day shows no
            // tag rather than a zero — the day asked nothing, so there is nothing to score.
            training?.takeIf { it.isTraining }?.let { session ->
                Tag(
                    "TRAINED",
                    if (session.done) "Yes" else "—",
                    if (session.done) FuelledColors.Success else FuelledColors.Info,
                )
            }
            Spacer(Modifier.width(0.dp))
        }
    }
}

// ── Training (WORK-05) ───────────────────────────────────────────────────────────────────

/**
 * The week's training in one card: sessions kept, and the seven days at a glance.
 *
 * The dot strip carries FOUR states, not two, because "not done" collapses three different
 * facts that mean very different things: a rest day asked nothing, today has not happened
 * yet, and a past training day genuinely went missing. Only the last is a miss.
 */
@Composable
private fun TrainingSummaryCard(days: List<WorkoutDay>) {
    val today = days.lastOrNull()?.date ?: return
    val training = days.filter { it.isTraining }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp)
            .semantics { testTag = "week_training" },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${training.count { it.done }}",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "of ${training.size} workouts this week",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEach { day -> TrainingDot(day, today) }
        }
    }
}

@Composable
private fun TrainingDot(day: WorkoutDay, today: LocalDate) {
    val state = day.state(today)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (state == WorkoutDayState.DONE) FuelledColors.Primary
                    else MaterialTheme.colorScheme.surface,
                )
                .semantics { testTag = "week_training_${day.date}" },
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                WorkoutDayState.DONE -> Icon(
                    Icons.Filled.Check,
                    contentDescription = "Trained",
                    tint = FuelledColors.OnPrimary,
                    modifier = Modifier.size(18.dp),
                )
                WorkoutDayState.MISSED -> Text(
                    "—",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WorkoutDayState.PENDING -> Box(
                    Modifier.size(10.dp).clip(CircleShape).background(FuelledColors.Primary),
                )
                WorkoutDayState.REST -> Text(
                    "R",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            day.date.dayOfWeek.name.take(1),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── The trend (HIST-05) ─────────────────────────────────────────────────────────────────

/**
 * Four weeks, oldest first. Built from [StatBar] and [Tag] — the catalog the rest of the app
 * is drawn with — rather than a charting dependency: at four rows a bar per week reads better
 * than a line chart, and pulling in a library to draw four bars is how a token catalog stops
 * being the source of truth for how things look (history brief, rejected outright).
 */
@Composable
private fun TrendSection(history: History) {
    SectionLabel("THE LAST $TREND_WEEKS WEEKS", "trend_label")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .semantics { testTag = "trend_card" },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        history.weeks.forEachIndexed { i, week ->
            TrendWeekRow(week = week, isCurrent = i == history.weeks.lastIndex)
        }
    }
}

@Composable
private fun TrendWeekRow(week: WeekTrend, isCurrent: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = "trend_week_${week.start}" },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isCurrent) "This week" else "${week.start.day} ${week.start.monthAbbrev()}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            )
            Spacer(Modifier.weight(1f))
            Text(
                // HIST-05/decision D6: a week you had not started tracking has NO data. An
                // app that reports "0 kcal" for a week it was not installed for is not being
                // rigorous, it is lying — and a trend opening with two zero bars reads as
                // failure rather than absence.
                text = if (week.hasData) "${week.avgConsumedKcal} kcal avg" else "no data",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (week.hasData) {
            StatBar(
                progress = if (week.targetKcal <= 0) 0f else week.avgConsumedKcal.toFloat() / week.targetKcal,
                color = FuelledColors.Primary,
                label = "vs ${week.targetKcal}",
                valueText = "${week.proteinDaysHit}/${week.proteinDaysJudged} protein · ${week.mealsDone}/${week.mealsTotal} meals",
            )
        }
    }
}

/** "Jul" — the month, short. Kept here because it is presentation's idiom, not the domain's. */
private fun LocalDate.monthAbbrev(): String =
    month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)

// ── Weight (HIST-06..08) ────────────────────────────────────────────────────────────────

/**
 * The outcome variable. Everything above measures INPUT; this is the only thing on the
 * surface that says whether the input is doing anything.
 *
 * With nothing recorded it states exactly that and offers the control (HIST-07) — no chart,
 * no zero, no empty axes. An app that renders an empty graph is telling you that you have
 * failed at something you never opted into.
 */
@Composable
private fun WeightSection(log: WeightLog, units: UnitSystem, onRecord: (Double) -> Unit) {
    var entering by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    val suffix = if (units == UnitSystem.METRIC) "kg" else "lb"

    SectionLabel("WEIGHT", "weight_label")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .semantics { testTag = "weight_card" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val latest = log.latest
        if (latest == null) {
            Text(
                text = "No weigh-ins yet. Record one and this becomes the line that says whether any of the above is working.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { testTag = "weight_empty" },
            )
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${units.weightFromKg(latest.kg).oneDecimal()} $suffix",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { testTag = "weight_latest" },
                )
                Spacer(Modifier.weight(1f))
                // HIST-08: one reading makes no claim about change. "0.0 kg in 4 weeks" off a
                // single data point is a result the app invented.
                log.change?.let { change ->
                    val converted = units.weightFromKg(change)
                    Text(
                        text = "${if (converted >= 0) "+" else "−"}${kotlin.math.abs(converted).oneDecimal()} $suffix in $TREND_WEEKS weeks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { testTag = "weight_change" },
                    )
                }
            }
        }

        if (entering) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The label rides M3's own slot, never a sibling Text: a field labelled by
                // a neighbour reads to a screen reader as an unlabelled clickable (found by
                // audit_a11y on the food editor before it shipped, 2026-08-01).
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text("Today's weight ($suffix)") },
                    modifier = Modifier.weight(1f).semantics { testTag = "weight_input" },
                )
                Spacer(Modifier.width(8.dp))
                AppTextButton(
                    text = "Save",
                    onClick = {
                        typed.trim().replace(',', '.').toDoubleOrNull()
                            ?.let { onRecord(units.weightToKg(it)) }
                        typed = ""
                        entering = false
                    },
                    modifier = Modifier.semantics { testTag = "weight_save" },
                )
            }
        } else {
            AppTextButton(
                text = if (log.latest == null) "Record your weight" else "Record today's weight",
                onClick = { entering = true },
                modifier = Modifier.semantics { testTag = "weight_add" },
            )
        }
    }
}

/** One decimal, without pulling a formatter in — weights are read, not computed with. */
private fun Double.oneDecimal(): String {
    val rounded = kotlin.math.round(this * 10) / 10
    val whole = rounded.toLong()
    val tenth = kotlin.math.abs(kotlin.math.round(rounded * 10) - whole * 10).toLong()
    return "$whole.$tenth"
}
