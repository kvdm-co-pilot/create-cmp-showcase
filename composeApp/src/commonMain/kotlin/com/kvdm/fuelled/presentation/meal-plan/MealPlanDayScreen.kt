package com.kvdm.fuelled.presentation.mealplan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.kvdm.fuelled.presentation.components.TickButton
import com.kvdm.fuelled.presentation.components.enterRise
import com.kvdm.fuelled.presentation.theme.FuelledColors
import com.kvdm.fuelled.presentation.theme.FuelledMotion
import com.kvdm.fuelled.presentation.theme.FuelledTokens
import com.kvdm.fuelled.presentation.theme.LocalMotion
import com.kvdm.fuelled.presentation.theme.spring
import com.kvdm.fuelled.presentation.theme.tween

// ── Meal plan: the structured day (DESIGN DRAFT — feature-design:meal-plan) ──────────────
// The Body-for-LIFE day as a fixed grid (docs/features/meal-plan.md): six meal containers —
// Breakfast · Snack · Lunch · Snack · Dinner · Snack — with a 500 ml water container after
// each, ALWAYS rendered, empty or not. Focus and lateness are derived states the screen only
// displays; ticking is the plan→eaten transition. This file is the design pass: stateless,
// stub-driven, signed on rendered output BEFORE the behavior contract is written. The stub
// models below are presentation-only stand-ins — the domain grows its real shapes in the
// build step, after the spec.

/** One entry row. [id] is the log entry's own id — the remove control (UX-02) addresses it. */
data class PlanEntryUi(
    val id: String,
    val name: String,
    val serving: String,
    val kcal: Int,
    val proteinG: Int,
    /** ENTRY-01: the row's serving multiple — the in-place stepper's current value. */
    val servings: Int = 1,
)

/**
 * DONE, FOCUSED ("next"), FOCUSED_LATE, MISSED, UPCOMING. MISSED (PLAN-19, decision 14) is
 * the quiet third outcome — the day moved past this slot without it being eaten. Muted on
 * purpose: missing a meal is routine in this method, and alarm styling belongs only to the
 * one slot you can still act on in time (the focused one).
 */
enum class PlanSlotState { DONE, FOCUSED, FOCUSED_LATE, MISSED, UPCOMING, BEFORE_START }

data class PlanMealUi(
    val key: String,
    val label: String,
    val time: String,
    val state: PlanSlotState,
    val entries: List<PlanEntryUi>,
    /** Decision 8: ticked with no entries — eaten off-plan / skipped, no food fabricated. */
    val tickedEmpty: Boolean = false,
    /** PLAN-25: its time has arrived. Splits the focused tag's "up now" from "at 09:30". */
    val due: Boolean = true,
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
    /**
     * HIST-04: whether copy-forward is offered. False on a day already lived — copying a past
     * day over the days after it would overwrite real logged history.
     */
    val canCopyForward: Boolean = true,
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
            listOf(PlanEntryUi("s-b1", "Rolled oats & whey", "80 g · 1 scoop", 430, 38), PlanEntryUi("s-b2", "Banana", "1 medium", 105, 1)),
        ),
        PlanMealUi("morning_snack", "Snack", "09:30", PlanSlotState.DONE, emptyList(), tickedEmpty = true),
        PlanMealUi(
            "lunch", "Lunch", "12:00", PlanSlotState.FOCUSED_LATE,
            listOf(PlanEntryUi("s-l1", "Chicken breast & rice", "200 g · 150 g", 620, 58), PlanEntryUi("s-l2", "Mixed greens", "1 bowl", 90, 3)),
        ),
        PlanMealUi("afternoon_snack", "Snack", "14:30", PlanSlotState.UPCOMING, listOf(PlanEntryUi("s-s1", "Greek yogurt 0%", "170 g", 100, 17))),
        PlanMealUi("dinner", "Dinner", "17:00", PlanSlotState.UPCOMING, listOf(PlanEntryUi("s-d1", "Salmon & sweet potato", "180 g · 200 g", 610, 45))),
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
            entries = if (it.key == "morning_snack") listOf(PlanEntryUi("s-ms1", "Almonds", "20 g", 116, 4)) else it.entries,
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
    /** UX-02: remove one entry by its id — the ledger's one delete path (MEAL-06), surfaced. */
    val onDeleteEntry: (String) -> Unit = {},
    /** ENTRY-01: change one entry's serving multiple, in place. */
    val onEntryServings: (String, Int) -> Unit = { _, _ -> },
    /** BFL-05: open the meal builder — compose once, plan it across the week. */
    val onBuildMeal: () -> Unit = {},
    /** PLAN-19 (motion D17): open the retrospective — the week's verdict, from the week itself. */
    val onOpenReview: () -> Unit = {},
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
/** ENTRY-02: what the undo bar says and does, or null when nothing was just removed. */
data class UndoState(val name: String, val onUndo: () -> Unit)

@Composable
fun MealPlanDayScreen(
    day: PlanDayUi = samplePlanMidday,
    actions: PlanDayActions = PlanDayActions.None,
    undo: UndoState? = null,
    // ENTRY-03: which row's editor starts open. Ephemeral view state — it never round-trips
    // the domain — but seeded from a parameter so the registry can render the editing state.
    initialExpandedEntryId: String? = null,
) {
    var expandedEntryId by rememberSaveable { mutableStateOf(initialExpandedEntryId) }
    ScreenColumn(screenTag = "meal_plan") {
        AppHeader(
            title = "Meal plan",
            screenTag = "meal_plan",
            actions = {
                // PLAN-19 (motion D17): "how did last week go" is one thought away from the
                // week you are planning, so the retrospective gets a door here. Profile's
                // stats row keeps its own.
                AppTextButton(
                    text = "Review",
                    onClick = actions.onOpenReview,
                    modifier = Modifier.semantics { testTag = "plan_review" },
                )
                Spacer(Modifier.width(8.dp))
                // BFL-05: the fast path to a planned week sits ON the surface a week is
                // planned from. Copy-forward repeats a day you already built by hand; this is
                // how you build it.
                AppTextButton(
                    text = "Build",
                    onClick = actions.onBuildMeal,
                    modifier = Modifier.semantics { testTag = "plan_build_meal" },
                )
                Spacer(Modifier.width(8.dp))
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
        undo?.let {
            Spacer(Modifier.height(6.dp))
            UndoBar(name = it.name, onUndo = it.onUndo)
        }
        Spacer(Modifier.height(FuelledTokens.GapCard))
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(FuelledTokens.GapCard),
        ) {
            // D8: the slot cards rise in a stagger, each water row with the card above it.
            day.meals.forEachIndexed { i, meal ->
                PlanMealCard(
                    meal = meal,
                    onToggleDone = { actions.onToggleDone(meal.key, !meal.done) },
                    onAddFood = { actions.onAddFood(meal.key) },
                    onDeleteEntry = actions.onDeleteEntry,
                    onEntryServings = actions.onEntryServings,
                    expandedEntryId = expandedEntryId,
                    onToggleEntryExpanded = { id -> expandedEntryId = if (expandedEntryId == id) null else id },
                    modifier = Modifier.enterRise(i),
                )
                day.waters.getOrNull(i)?.let { water ->
                    WaterRow(
                        water = water,
                        onToggle = { actions.onToggleWater(water.index, !water.done) },
                        modifier = Modifier.enterRise(i),
                    )
                }
            }
            // PLAN-20: the affordance that makes a planned week survivable. It sits at the
            // BOTTOM, after the day it copies — you reach it having just built the thing it
            // repeats, which is the only moment the offer makes sense.
            if (day.canCopyForward) {
                AppTextButton(
                    text = "Copy this day to the rest of the week",
                    onClick = actions.onCopyForward,
                    modifier = Modifier.semantics { testTag = "plan_copy_forward" },
                )
            }
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
    onDeleteEntry: (String) -> Unit = {},
    onEntryServings: (String, Int) -> Unit = { _, _ -> },
    // ENTRY-03: which row's editor is open, hoisted so this card stays a pure function of its
    // arguments — the registry can render the open state and the golden tree can hold it.
    // At most ONE id, so opening a second row closes the first: you correct one thing at a
    // time, and an accordion never leaves a column of half-open editors behind you.
    expandedEntryId: String? = null,
    onToggleEntryExpanded: (String) -> Unit = {},
    // The card is shared with Today (decision 13 — Today renders the plan's projection), and
    // each surface names its own tags: `plan_add_lunch` there, `today_add_lunch` here
    // (TODAY-07). One composable, so the two can never drift visually; two tag namespaces, so
    // a test can still say which surface it is asserting on.
    tagPrefix: String = "plan",
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotion.current
    val borderColor = when (meal.state) {
        PlanSlotState.FOCUSED -> FuelledColors.Primary
        PlanSlotState.FOCUSED_LATE -> FuelledColors.Warning
        else -> null
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .let { if (borderColor != null) it.border(1.dp, borderColor, RoundedCornerShape(20.dp)) else it }
            // ENTRY-03: the card grows and shrinks with its entry editor on `Settle`.
            .animateContentSize(animationSpec = motion.spring(FuelledMotion.Springs.Settle))
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
                // PLAN-25: "up now" is a claim about the clock, so it waits for the clock.
                // Before its time the honest line is when it is due — a 09:30 snack seen at
                // 07:02 is next, not now.
                PlanSlotState.FOCUSED ->
                    Tag("NEXT", if (meal.due) "up now" else "at ${meal.time}", FuelledColors.Primary)
                PlanSlotState.FOCUSED_LATE -> Tag("LATE", "since ${meal.time}", FuelledColors.Warning)
                // Muted, deliberately: the day moved on (PLAN-19). The tick and add stay
                // live below — a missed meal is back-fillable, not closed.
                PlanSlotState.MISSED -> Tag("MISSED", "", MaterialTheme.colorScheme.onSurfaceVariant)
                // START-02: before this app existed. NOT missed — the app has no standing to
                // say you skipped a meal it never saw (journey J3). Still back-fillable.
                PlanSlotState.BEFORE_START -> Tag("BEFORE YOU STARTED", "", MaterialTheme.colorScheme.onSurfaceVariant)
                PlanSlotState.UPCOMING -> {}
            }
            Spacer(Modifier.width(6.dp))
            // The tick is present in EVERY state, including DONE — where it is the un-tick.
            // A completion you cannot reverse turns a mis-tap into a permanently wrong day,
            // and un-ticking clears only the completion: the entries it logged stay logged,
            // because they were eaten (see MealPlanRepositoryImpl.setSlotDone).
            TickButton(
                icon = Icons.Filled.Check,
                checked = meal.done,
                contentDescription = if (meal.done) "Undo ${meal.label} done" else "Mark ${meal.label} done",
                onClick = onToggleDone,
                checkedTint = FuelledColors.Success,
                uncheckedTint = MaterialTheme.colorScheme.onSurfaceVariant,
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
            meal.entries.isNotEmpty() -> meal.entries.forEach {
                PlanEntryRow(
                    entry = it,
                    expanded = it.id == expandedEntryId,
                    onToggleExpanded = { onToggleEntryExpanded(it.id) },
                    onDelete = { onDeleteEntry(it.id) },
                    onServings = { n -> onEntryServings(it.id, n) },
                    tagPrefix = tagPrefix,
                )
            }
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

/**
 * One logged (or planned) entry (UX-02, ENTRY-01/03).
 *
 * ENTRY-03 — progressive disclosure. The row reads as a ledger line by default and becomes an
 * editor when you ask: the tap reveals the serving stepper and the remove. A day is six
 * containers; rendering a stepper and an X on every entry of every one of them turned the
 * plan into a wall of controls for a screen people mostly READ. What the collapsed row must
 * never hide is the FACT — the serving label states the multiple ("2 x 100 g") whether the
 * editor is open or not.
 *
 * The reveal is a labelled tap on the row itself, never a swipe: a hidden gesture is not an
 * affordance, and a destructive action a screen reader cannot reach is not a control
 * (UX-04's standing rule). Expanded, both controls are labelled 48 dp [AppIconButton]s.
 */
@Composable
private fun PlanEntryRow(
    entry: PlanEntryUi,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDelete: () -> Unit,
    onServings: (Int) -> Unit,
    tagPrefix: String = "plan",
) {
    val motion = LocalMotion.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Two lines of text measure 39 dp — under the floor. Making the ROW the tap
                // target is what makes the reveal discoverable, so the row has to earn a real
                // target rather than the text's natural height.
                .heightIn(min = AppButtonDefaults.MinTouchTarget)
                .clickable(
                    onClickLabel = if (expanded) "Hide edit controls" else "Edit ${entry.name}",
                    onClick = onToggleExpanded,
                )
                .semantics { testTag = "${tagPrefix}_entry_${entry.id}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(entry.serving, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${entry.kcal} kcal", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text("${entry.proteinG}g P", style = MaterialTheme.typography.labelMedium, color = FuelledColors.Protein)
            }
        }
        // The editor's nodes are present exactly when it is open (the golden and the tests
        // read them at rest); the reveal itself is a Standard expand, the close a Quick shrink.
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(motion.tween(FuelledMotion.Duration.Standard, FuelledMotion.Easings.Enter)) +
                expandVertically(motion.tween(FuelledMotion.Duration.Standard, FuelledMotion.Easings.Enter)),
            exit = fadeOut(motion.tween(FuelledMotion.Duration.Quick, FuelledMotion.Easings.Exit)) +
                shrinkVertically(motion.tween(FuelledMotion.Duration.Quick, FuelledMotion.Easings.Exit)),
        ) {
            // ENTRY-01: the serving is editable WHERE IT IS. Fixing "I actually had two" was
            // delete-and-re-add before this — four taps and a trip to the tray for a number
            // already on screen.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Tinted so the editor reads as belonging to the row above it. Untinted it
                    // floated between two entries, and in a container of several rows "which
                    // one am I editing?" is the only question the disclosure must never raise.
                    .clip(RoundedCornerShape(FuelledTokens.RadiusCard))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconButton(
                    icon = Icons.Filled.Remove,
                    contentDescription = "One serving less of ${entry.name}",
                    onClick = { onServings(entry.servings - 1) },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { testTag = "${tagPrefix}_entry_minus_${entry.id}" },
                )
                Text(
                    "${entry.servings}x",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { testTag = "${tagPrefix}_entry_servings_${entry.id}" },
                )
                AppIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "One serving more of ${entry.name}",
                    onClick = { onServings(entry.servings + 1) },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { testTag = "${tagPrefix}_entry_plus_${entry.id}" },
                )
                Spacer(Modifier.weight(1f))
                // The remove travels WITH the stepper: "change this row" is one intent, and
                // splitting its two halves — one always on screen, one behind a tap — would
                // be the incoherent version of this disclosure. The undo bar (ENTRY-02) is
                // what makes an immediate, dialog-free removal safe.
                AppIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "Remove ${entry.name}",
                    onClick = onDelete,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { testTag = "${tagPrefix}_entry_delete_${entry.id}" },
                )
            }
        }
    }
}

/**
 * ENTRY-02: the undo bar. It appears where the removal happened, states WHAT went, and puts
 * it back on one tap. Chosen over a floating snackbar deliberately: these screens are plain
 * scrolling columns with no Scaffold, and an undo you have to catch before it fades is worse
 * than one that waits until you act.
 */
@Composable
internal fun UndoBar(name: String, onUndo: () -> Unit, tagPrefix: String = "plan") {
    val motion = LocalMotion.current
    // D8: slides up on `Settle` as it appears. Its content is composed from the first frame
    // (the target state is already true), so the tree at rest is unchanged.
    AnimatedVisibility(
        visibleState = remember { MutableTransitionState(false).apply { targetState = true } },
        enter = slideInVertically(motion.spring(FuelledMotion.Springs.Settle)) { it } +
            fadeIn(motion.tween(FuelledMotion.Duration.Standard)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(FuelledTokens.RadiusCard))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .semantics { testTag = "${tagPrefix}_undo" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Removed $name",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            AppTextButton(
                text = "Undo",
                onClick = onUndo,
                modifier = Modifier.semantics { testTag = "${tagPrefix}_undo_action" },
            )
        }
    }
}

/**
 * The 500 ml container between meals — deliberately slimmer than a meal card so the six of
 * them read as the rhythm between meals, not six more meals. Its reminder time is derived
 * from the neighbouring meal times (decision 5); the tick is the only interaction.
 */
@Composable
internal fun WaterRow(
    water: PlanWaterUi,
    onToggle: () -> Unit = {},
    tagPrefix: String = "plan",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
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
        TickButton(
            icon = Icons.Filled.Check,
            checked = water.done,
            contentDescription = if (water.done) "Undo water ${water.index}" else "Mark water ${water.index} done",
            onClick = onToggle,
            checkedTint = FuelledColors.Info,
            uncheckedTint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { testTag = "${tagPrefix}_water_done_${water.index}" },
        )
    }
}
