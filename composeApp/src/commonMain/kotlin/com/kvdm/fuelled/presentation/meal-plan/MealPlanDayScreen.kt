package com.kvdm.fuelled.presentation.mealplan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.presentation.components.AppButtonDefaults
import com.kvdm.fuelled.presentation.components.AppHeader
import com.kvdm.fuelled.presentation.components.AppIconButton
import com.kvdm.fuelled.presentation.components.AppTextButton
import com.kvdm.fuelled.presentation.components.ScreenColumn
import com.kvdm.fuelled.presentation.components.Tag
import com.kvdm.fuelled.presentation.theme.FuelledColors
import com.kvdm.fuelled.presentation.theme.FuelledTokens

// ── Meal plan: the structured day (DESIGN DRAFT — feature-design:meal-plan) ──────────────
// The Body-for-LIFE day as a fixed grid (docs/features/meal-plan.md): six meal containers —
// Breakfast · Snack · Lunch · Snack · Dinner · Snack — with a 500 ml water container after
// each, ALWAYS rendered, empty or not. Focus and lateness are derived states the screen only
// displays; ticking is the plan→eaten transition. This file is the design pass: stateless,
// stub-driven, signed on rendered output BEFORE the behavior contract is written. The stub
// models below are presentation-only stand-ins — the domain grows its real shapes in the
// build step, after the spec.

data class PlanEntryUi(val name: String, val serving: String, val kcal: Int, val proteinG: Int)

/**
 * DONE, FOCUSED ("next"), FOCUSED_LATE, MISSED, UPCOMING. MISSED (PLAN-19, decision 14) is
 * the quiet third outcome — the day moved past this slot without it being eaten. Muted on
 * purpose: missing a meal is routine in this method, and alarm styling belongs only to the
 * one slot you can still act on in time (the focused one).
 */
enum class PlanSlotState { DONE, FOCUSED, FOCUSED_LATE, MISSED, UPCOMING }

data class PlanMealUi(
    val key: String,
    val label: String,
    val time: String,
    val state: PlanSlotState,
    val entries: List<PlanEntryUi>,
    /** Decision 8: ticked with no entries — eaten off-plan / skipped, no food fabricated. */
    val tickedEmpty: Boolean = false,
) {
    /** Done-ness IS the DONE state — kept derived so the two can never be set to disagree. */
    val done: Boolean get() = state == PlanSlotState.DONE
}

data class PlanWaterUi(val index: Int, val time: String, val done: Boolean)

data class PlanDayUi(
    val stripDays: List<String>,
    val selectedDay: Int,
    val litresDone: String,
    val litresGoal: String,
    /** PLAN-22: meal containers holding at least one vegetable, against the method's 2. */
    val vegDone: Int,
    val vegGoal: Int,
    val meals: List<PlanMealUi>,
    val waters: List<PlanWaterUi>,
)

// PREVIEW/DEMO fixtures — fixed dates and times, never a clock read (ARCH-12; renders must be
// time-of-day independent). Mid-day, the realistic bad morning: overslept past breakfast
// (MISSED, its plan still visible), grabbed something off-plan at 09:30 (ticked empty —
// decision 8), lunch focused and LATE, the rest planned or empty — five of the six slot
// states in one render.
// Decision 13: one leading YESTERDAY chip for back-fill (where the tray's date row went),
// then today + 7 — nine chips, today at index 1.
val samplePlanStrip = listOf("Tue 21", "Today", "Thu 23", "Fri 24", "Sat 25", "Sun 26", "Mon 27", "Tue 28", "Wed 29")

val samplePlanMidday = PlanDayUi(
    stripDays = samplePlanStrip,
    selectedDay = 1,
    litresDone = "1.0",
    litresGoal = "3.0",
    vegDone = 1,
    vegGoal = 2,
    meals = listOf(
        // Overslept: breakfast was PLANNED and is now MISSED — the plan shows what was
        // meant to be eaten, muted, still back-fillable (PLAN-19/PLAN-21).
        PlanMealUi(
            "breakfast", "Breakfast", "07:00", PlanSlotState.MISSED,
            listOf(PlanEntryUi("Rolled oats & whey", "80 g · 1 scoop", 430, 38), PlanEntryUi("Banana", "1 medium", 105, 1)),
        ),
        PlanMealUi("morning_snack", "Snack", "09:30", PlanSlotState.DONE, emptyList(), tickedEmpty = true),
        PlanMealUi(
            "lunch", "Lunch", "12:00", PlanSlotState.FOCUSED_LATE,
            listOf(PlanEntryUi("Chicken breast & rice", "200 g · 150 g", 620, 58), PlanEntryUi("Mixed greens", "1 bowl", 90, 3)),
        ),
        PlanMealUi("afternoon_snack", "Snack", "14:30", PlanSlotState.UPCOMING, listOf(PlanEntryUi("Greek yogurt 0%", "170 g", 100, 17))),
        PlanMealUi("dinner", "Dinner", "17:00", PlanSlotState.UPCOMING, listOf(PlanEntryUi("Salmon & sweet potato", "180 g · 200 g", 610, 45))),
        PlanMealUi("evening_snack", "Snack", "19:30", PlanSlotState.UPCOMING, emptyList()),
    ),
    waters = listOf(
        PlanWaterUi(1, "08:15", done = true),
        PlanWaterUi(2, "10:45", done = true),
        PlanWaterUi(3, "13:15", done = false),
        PlanWaterUi(4, "15:45", done = false),
        PlanWaterUi(5, "18:15", done = false),
        PlanWaterUi(6, "20:45", done = false),
    ),
)

// A fresh day: every container renders anyway — the empty body IS the add affordance
// (decision 2); breakfast holds focus, nothing is late yet.
val samplePlanEmpty = PlanDayUi(
    stripDays = samplePlanStrip,
    selectedDay = 1,
    litresDone = "0.0",
    litresGoal = "3.0",
    vegDone = 0,
    vegGoal = 2,
    meals = listOf(
        PlanMealUi("breakfast", "Breakfast", "07:00", PlanSlotState.FOCUSED, emptyList()),
        PlanMealUi("morning_snack", "Snack", "09:30", PlanSlotState.UPCOMING, emptyList()),
        PlanMealUi("lunch", "Lunch", "12:00", PlanSlotState.UPCOMING, emptyList()),
        PlanMealUi("afternoon_snack", "Snack", "14:30", PlanSlotState.UPCOMING, emptyList()),
        PlanMealUi("dinner", "Dinner", "17:00", PlanSlotState.UPCOMING, emptyList()),
        PlanMealUi("evening_snack", "Snack", "19:30", PlanSlotState.UPCOMING, emptyList()),
    ),
    waters = (1..6).map { PlanWaterUi(it, listOf("08:15", "10:45", "13:15", "15:45", "18:15", "20:45")[it - 1], done = false) },
)

// Tomorrow, planned ahead: entries in every meal, nothing done, no focus — focus and lateness
// belong to the current day only (decision 7).
val samplePlanTomorrow = samplePlanMidday.copy(
    selectedDay = 2,
    litresDone = "0.0",
    meals = samplePlanMidday.meals.map {
        it.copy(
            state = PlanSlotState.UPCOMING,
            tickedEmpty = false,
            entries = if (it.key == "morning_snack") listOf(PlanEntryUi("Almonds", "20 g", 116, 4)) else it.entries,
        )
    },
    waters = samplePlanMidday.waters.map { it.copy(done = false) },
)

/**
 * Every interaction the plan screen offers (PLAN-04/PLAN-10/PLAN-11/PLAN-13/PLAN-20), bundled
 * so the stateless screen keeps a preview-friendly shape — the registry renders it with
 * [PlanDayActions.None] and no ViewModel, and the golden tree stays a pure function of its
 * arguments.
 */
data class PlanDayActions(
    val onSelectDay: (Int) -> Unit,
    val onToggleDone: (String, Boolean) -> Unit,
    val onToggleWater: (Int, Boolean) -> Unit,
    val onAddFood: (String) -> Unit,
    val onCopyForward: () -> Unit,
    val onOpenTimes: () -> Unit,
) {
    companion object {
        /** Inert actions for gallery renders and golden trees: the structure, without wiring. */
        val None = PlanDayActions({}, { _, _ -> }, { _, _ -> }, {}, {}, {})
    }
}

/**
 * The structured day — stateless, so the preview registry renders every state without a
 * ViewModel. Decision 13: this is its OWN routed screen (`plan/{date}`), not Today's body —
 * Today shows the highlights projection ([TodayHighlightsScreen]) and links here.
 */
@Composable
fun MealPlanDayScreen(
    day: PlanDayUi = samplePlanMidday,
    actions: PlanDayActions = PlanDayActions.None,
) {
    ScreenColumn(screenTag = "meal_plan") {
        AppHeader(
            title = "Meal plan",
            screenTag = "meal_plan",
            actions = {
                AppTextButton(
                    text = "Times",
                    onClick = actions.onOpenTimes,
                    modifier = Modifier.semantics { testTag = "plan_open_times" },
                )
            },
        )
        DayStrip(days = day.stripDays, selected = day.selectedDay, onSelect = actions.onSelectDay)
        Spacer(Modifier.height(6.dp))
        Row {
            Text(
                text = "Water ${day.litresDone} / ${day.litresGoal} L",
                style = MaterialTheme.typography.labelMedium,
                color = FuelledColors.Info,
                modifier = Modifier.semantics { testTag = "plan_water_total" },
            )
            Spacer(Modifier.width(12.dp))
            // PLAN-22: the method's veg-with-two-meals rule, surfaced, never enforced.
            Text(
                text = "Veg ${day.vegDone} of ${day.vegGoal}",
                style = MaterialTheme.typography.labelMedium,
                color = FuelledColors.Success,
                modifier = Modifier.semantics { testTag = "plan_veg_total" },
            )
        }
        Spacer(Modifier.height(FuelledTokens.GapCard))
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(FuelledTokens.GapCard),
        ) {
            day.meals.forEachIndexed { i, meal ->
                PlanMealCard(
                    meal = meal,
                    onToggleDone = { actions.onToggleDone(meal.key, !meal.done) },
                    onAddFood = { actions.onAddFood(meal.key) },
                )
                day.waters.getOrNull(i)?.let { water ->
                    WaterRow(
                        water = water,
                        onToggle = { actions.onToggleWater(water.index, !water.done) },
                    )
                }
            }
            // PLAN-20: the affordance that makes a planned week survivable. It sits at the
            // BOTTOM, after the day it copies — you reach it having just built the thing it
            // repeats, which is the only moment the offer makes sense.
            AppTextButton(
                text = "Copy this day to the rest of the week",
                onClick = actions.onCopyForward,
                modifier = Modifier.semantics { testTag = "plan_copy_forward" },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** The week strip: the one date selector (the tray's date pills are gone — the tap aims). */
@Composable
private fun DayStrip(days: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .semantics { testTag = "plan_days" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        days.forEachIndexed { i, label ->
            val isSelected = i == selected
            Box(
                modifier = Modifier
                    .height(AppButtonDefaults.MinTouchTarget)
                    .clip(RoundedCornerShape(FuelledTokens.RadiusPill))
                    .background(if (isSelected) FuelledColors.Primary else MaterialTheme.colorScheme.secondary)
                    .selectable(selected = isSelected, onClick = { onSelect(i) })
                    .semantics { testTag = "plan_day_$i" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) FuelledColors.OnPrimary else MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

/**
 * One slot's container — the Today MealCard's visual language (20 dp surface card, titleMedium
 * slot label, AppIconButton add) carried over so the grid reads as the same app. What is NEW is
 * state: the focused card gets a Primary border ("NEXT") or Warning border ("LATE"), a done
 * card swaps its tick for a Success DONE tag, and an empty body is the add affordance itself.
 */
@Composable
internal fun PlanMealCard(
    meal: PlanMealUi,
    onToggleDone: () -> Unit = {},
    onAddFood: () -> Unit = {},
    // The card is shared with Today (decision 13 — Today renders the plan's projection), and
    // each surface names its own tags: `plan_add_lunch` there, `today_add_lunch` here
    // (TODAY-07). One composable, so the two can never drift visually; two tag namespaces, so
    // a test can still say which surface it is asserting on.
    tagPrefix: String = "plan",
) {
    val borderColor = when (meal.state) {
        PlanSlotState.FOCUSED -> FuelledColors.Primary
        PlanSlotState.FOCUSED_LATE -> FuelledColors.Warning
        else -> null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .let { if (borderColor != null) it.border(1.dp, borderColor, RoundedCornerShape(20.dp)) else it }
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .semantics { testTag = "${tagPrefix}_slot_${meal.key}" },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(meal.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(meal.time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            when (meal.state) {
                PlanSlotState.DONE -> Tag("DONE", "", FuelledColors.Success)
                PlanSlotState.FOCUSED -> Tag("NEXT", "up now", FuelledColors.Primary)
                PlanSlotState.FOCUSED_LATE -> Tag("LATE", "since ${meal.time}", FuelledColors.Warning)
                // Muted, deliberately: the day moved on (PLAN-19). The tick and add stay
                // live below — a missed meal is back-fillable, not closed.
                PlanSlotState.MISSED -> Tag("MISSED", "", MaterialTheme.colorScheme.onSurfaceVariant)
                PlanSlotState.UPCOMING -> {}
            }
            Spacer(Modifier.width(6.dp))
            // The tick is present in EVERY state, including DONE — where it is the un-tick.
            // A completion you cannot reverse turns a mis-tap into a permanently wrong day,
            // and un-ticking clears only the completion: the entries it logged stay logged,
            // because they were eaten (see MealPlanRepositoryImpl.setSlotDone).
            AppIconButton(
                icon = Icons.Filled.Check,
                contentDescription = if (meal.done) "Undo ${meal.label} done" else "Mark ${meal.label} done",
                onClick = onToggleDone,
                tint = if (meal.done) FuelledColors.Success else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { testTag = "${tagPrefix}_done_${meal.key}" },
            )
            // PLAN-19: a MISSED container keeps its add control — it is back-fillable, not
            // closed. Only the empty-body case moves the affordance into the body below.
            if (meal.entries.isNotEmpty()) {
                AppIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "Add food to ${meal.label}",
                    onClick = onAddFood,
                    tint = FuelledColors.Primary,
                    modifier = Modifier.semantics { testTag = "${tagPrefix}_add_${meal.key}" },
                )
            }
        }
        when {
            meal.entries.isNotEmpty() -> meal.entries.forEach { PlanEntryRow(it) }
            meal.tickedEmpty -> Text(
                "Eaten off-plan",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // PLAN-04: the empty body IS the add control, and the tap carries this container's
            // day and slot — the tray never asks which meal this was for.
            else -> AppTextButton(
                text = "Add food",
                onClick = onAddFood,
                modifier = Modifier.semantics { testTag = "${tagPrefix}_add_${meal.key}" },
            )
        }
    }
}

@Composable
private fun PlanEntryRow(entry: PlanEntryUi) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(entry.serving, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${entry.kcal} kcal", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text("${entry.proteinG}g P", style = MaterialTheme.typography.labelMedium, color = FuelledColors.Protein)
        }
    }
}

/**
 * The 500 ml container between meals — deliberately slimmer than a meal card so the six of
 * them read as the rhythm between meals, not six more meals. Its reminder time is derived
 * from the neighbouring meal times (decision 5); the tick is the only interaction.
 */
@Composable
internal fun WaterRow(water: PlanWaterUi, onToggle: () -> Unit = {}, tagPrefix: String = "plan") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FuelledTokens.RadiusCard))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .semantics { testTag = "${tagPrefix}_water_${water.index}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Water", style = MaterialTheme.typography.bodyLarge, color = FuelledColors.Info)
        Spacer(Modifier.width(10.dp))
        Text(
            "500 ml · ${water.time}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        // Tappable in both states, for the same reason the meal tick is: six of these a day
        // means mis-taps, and 0.5 L you did not drink is a lie the day's total would keep.
        AppIconButton(
            icon = Icons.Filled.Check,
            contentDescription = if (water.done) "Undo water ${water.index}" else "Mark water ${water.index} done",
            onClick = onToggle,
            tint = if (water.done) FuelledColors.Success else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { testTag = "${tagPrefix}_water_done_${water.index}" },
        )
    }
}
