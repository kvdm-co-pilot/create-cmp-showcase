package com.kvdm.fuelled.presentation.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvdm.fuelled.domain.model.MacroProgress
import com.kvdm.fuelled.domain.model.MealGroup
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.MealTimes
import com.kvdm.fuelled.domain.model.LogEntry
import com.kvdm.fuelled.domain.model.PlanSlotView
import com.kvdm.fuelled.domain.model.TodayModel
import com.kvdm.fuelled.domain.model.VEG_MEAL_GOAL
import com.kvdm.fuelled.domain.model.WATER_DAY_GOAL_ML
import com.kvdm.fuelled.domain.model.buildPlanDay
import com.kvdm.fuelled.presentation.brand.FuelledWordmark
import com.kvdm.fuelled.presentation.components.ContentStateContainer
import com.kvdm.fuelled.presentation.components.ListItemCard
import com.kvdm.fuelled.presentation.components.ProgressRing
import com.kvdm.fuelled.presentation.components.StatBar
import com.kvdm.fuelled.presentation.mealplan.PlanEntryUi
import com.kvdm.fuelled.presentation.mealplan.PlanMealCard
import com.kvdm.fuelled.presentation.mealplan.PlanMealUi
import com.kvdm.fuelled.presentation.mealplan.PlanWaterUi
import com.kvdm.fuelled.presentation.mealplan.UndoBar
import com.kvdm.fuelled.presentation.mealplan.UndoState
import com.kvdm.fuelled.presentation.mealplan.WaterRow
import com.kvdm.fuelled.presentation.mealplan.clockLabel
import com.kvdm.fuelled.presentation.mealplan.litresLabel
import com.kvdm.fuelled.presentation.mealplan.uiKey
import com.kvdm.fuelled.presentation.mealplan.uiState
import com.kvdm.fuelled.presentation.theme.FuelledColors
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.koin.compose.viewmodel.koinViewModel

// ── Today: the daily macro dashboard (the hero screen) ───────────────────────────────
// The UI-first preview seam, mirroring the Foods exemplar's two entry points:
//   • TodayScreen — STATELESS, sample-defaulted. The preview registry renders it with no VM.
//   • TodayRoute  — the VM-backed tab the nav graph hosts: Loading/Content/Error are driven
//     by TodayViewModel through ContentStateContainer.
// Macro colours are a PRESENTATION concern assigned here per-macro; the domain MacroProgress
// carries none (ARCH-02 keeps domain free of Compose types). So are the two strings this
// screen derives from domain VALUES: the logical day's date (TODAY-01) and each meal slot's
// user-facing label (MEAL-03) are formatted HERE — the model carries a LocalDate and a
// MealSlot, never their rendering.

// PREVIEW/DEMO fixture — the screen's preview seam. Not production data: the Room-backed
// TodayRepositoryImpl seeds its own realistic day for the VM-backed TodayRoute. Its date is
// FIXED, not "today", so gallery renders and golden diffs stay deterministic.
val sampleToday = TodayModel(
    date = LocalDate(2026, 7, 22),
    consumedKcal = 1461,
    targetKcal = 2400,
    protein = MacroProgress("Protein", 121, 180, "g"),
    carbs = MacroProgress("Carbs", 168, 260, "g"),
    fat = MacroProgress("Fat", 31, 70, "g"),
    meals = listOf(
        MealGroup(MealSlot.BREAKFAST, listOf(
            LogEntry("s-b1", "Rolled oats & whey", "80 g · 1 scoop", 430, 38),
            LogEntry("s-b2", "Banana", "1 medium", 105, 1),
        )),
        MealGroup(MealSlot.LUNCH, listOf(
            LogEntry("s-l1", "Chicken breast & rice", "200 g · 150 g", 620, 58),
            LogEntry("s-l2", "Mixed greens", "1 bowl", 90, 3),
        )),
        MealGroup(MealSlot.AFTERNOON_SNACK, listOf(
            LogEntry("s-s1", "Greek yogurt 0%", "170 g", 100, 17),
            LogEntry("s-s2", "Almonds", "20 g", 116, 4),
        )),
    ),
)

/**
 * PREVIEW/DEMO fixture for the highlights (decision 13) — the mid-day state that communicates
 * most: breakfast already behind, lunch focused and LATE, third water pending, morning stack
 * half taken. Fixed values, never a clock read, so gallery renders and golden diffs stay
 * deterministic (ARCH-12).
 *
 * The plan half is a real [PlanDay] built through the real [buildPlanDay] at a fixed "now",
 * not hand-assembled. A fixture that bypassed the derivation could show a combination of states
 * the app can never actually produce — which is the failure mode of hand-built fixtures.
 */
val sampleHighlights: TodayHighlights = TodayHighlights(
    today = sampleToday,
    plan = buildPlanDay(
        date = LocalDate(2026, 7, 22),
        isCurrentDay = true,
        now = LocalTime(12, 45),
        times = MealTimes(),
        entriesBySlot = mapOf(
            MealSlot.LUNCH to listOf(
                LogEntry("s-l1", "Chicken breast & rice", "200 g · 150 g", 620, 58),
                LogEntry("s-l2", "Mixed greens", "1 bowl", 90, 3, veg = true),
            ),
        ),
        doneSlots = setOf(MealSlot.BREAKFAST, MealSlot.MORNING_SNACK),
        waterTicks = setOf(1, 2),
    ),
    supplements = SupplementBucket(name = "Morning stack", taken = 2, total = 4),
)

/**
 * A fresh day: the ring reads the full target as remaining, and breakfast holds focus with its
 * own add control as the card body — the whole-day empty state's replacement (TODAY-04/PLAN-04).
 */
val sampleHighlightsEmpty: TodayHighlights = TodayHighlights(
    today = sampleToday.copy(
        consumedKcal = 0,
        protein = MacroProgress("Protein", 0, 180, "g"),
        carbs = MacroProgress("Carbs", 0, 260, "g"),
        fat = MacroProgress("Fat", 0, 70, "g"),
        meals = emptyList(),
    ),
    plan = buildPlanDay(
        date = LocalDate(2026, 7, 22),
        isCurrentDay = true,
        now = LocalTime(6, 30),
        times = MealTimes(),
        entriesBySlot = emptyMap(),
        doneSlots = emptySet(),
        waterTicks = emptySet(),
    ),
    supplements = SupplementBucket(name = "Morning stack", taken = 0, total = 4),
)

/**
 * The logical day's date as the header shows it: "Wednesday, Jul 22" (TODAY-01). Presentation
 * formatting of a domain value — deliberately not a stored label, which is what this screen
 * used to render and what nothing could ever query by.
 */
internal fun LocalDate.dayHeaderLabel(): String {
    val weekday = dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    val month = month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    return "$weekday, $month $day"
}

/**
 * The slot's user-facing label (MEAL-03) — the domain enum carries the value, never the copy.
 * The three snacks all read "Snack": they are distinct *identities* (PLAN-01) so a row always
 * knows its container, but on screen each sits in its own place in the day, so labelling them
 * "Morning snack"/"Afternoon snack" would only repeat what the position already says.
 */
internal val MealSlot.label: String
    get() = when (this) {
        MealSlot.BREAKFAST -> "Breakfast"
        MealSlot.MORNING_SNACK -> "Snack"
        MealSlot.LUNCH -> "Lunch"
        MealSlot.AFTERNOON_SNACK -> "Snack"
        MealSlot.DINNER -> "Dinner"
        MealSlot.EVENING_SNACK -> "Snack"
    }

// `currentMealSlot` lived here: the slot an UNTARGETED add resolved to (MEAL-04/TODAY-08, both
// withdrawn). Every add control now belongs to a specific container and carries that container's
// target, so there is no untargeted tap left for a clock read to answer.

/**
 * The VM-backed Today tab the nav graph hosts. The Loading/Content/Error state machine lives in
 * [TodayViewModel]; this wrapper only renders it through [ContentStateContainer] (which owns the
 * `today_loading`/`today_error`/`today_retry` arms). A day with no entries is still Content —
 * the ring stays visible and reads the full target as remaining (TODAY-04).
 * A tab: it inherits BaseScreen (insets) from AppShell, so it does not re-wrap it (SHELL-05).
 *
 * @param onAddToMeal Where an add control goes: the tray, already targeted at the logical date
 *   and slot the TAP carried (TODAY-07). The nav graph turns it into a route.
 */
@Composable
fun TodayRoute(
    viewModel: TodayViewModel = koinViewModel(),
    onAddToMeal: (LocalDate, MealSlot) -> Unit = { _, _ -> },
    onOpenPlan: (LocalDate) -> Unit = {},
    onOpenSupplements: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lastDeleted by viewModel.lastDeleted.collectAsStateWithLifecycle()
    // No onRetry: the state is observed, so a transient failure recovers on the next
    // emission rather than waiting for a human to press a button.
    ContentStateContainer(state = state, screenTag = "today") { model ->
        TodayScreen(
            model = model,
            actions = TodayActions(
                onAddToMeal = onAddToMeal,
                onToggleFocusDone = viewModel::setSlotDone,
                onToggleWater = viewModel::setWaterDone,
                onOpenPlan = { onOpenPlan(model.plan.date) },
                onOpenSupplements = onOpenSupplements,
                onDeleteEntry = viewModel::deleteEntry,
                onEntryServings = viewModel::setServings,
            ),
            undo = lastDeleted?.let { UndoState(it.name, viewModel::undoDelete) },
        )
    }
}

/**
 * Every interaction Today offers (TODAY-07/TODAY-09..TODAY-12), bundled so the stateless screen
 * keeps a preview-friendly shape and its golden tree stays a pure function of its arguments.
 */
data class TodayActions(
    val onAddToMeal: (LocalDate, MealSlot) -> Unit = { _, _ -> },
    val onToggleFocusDone: (MealSlot, Boolean) -> Unit = { _, _ -> },
    val onToggleWater: (Int, Boolean) -> Unit = { _, _ -> },
    val onOpenPlan: () -> Unit = {},
    val onOpenSupplements: () -> Unit = {},
    /** UX-02: remove an entry from the focused container — the same delete as the plan's. */
    val onDeleteEntry: (String) -> Unit = {},
    /** ENTRY-01: step an entry's servings in place, without leaving the dashboard. */
    val onEntryServings: (String, Int) -> Unit = { _, _ -> },
)

/**
 * The stateless dashboard — the preview/UI-first seam, defaulted to a sample so the registry
 * renders it without a VM or Koin. The production path is [TodayRoute] + [TodayViewModel].
 *
 * **Today is the highlights, not the day** (decision 13). It shows what is true right now: the
 * ring and macros, the ONE focused container (TODAY-09), the next water (TODAY-10), the veg
 * count (TODAY-14), the supplement bucket (TODAY-11), and one link into the full week
 * (TODAY-12). The week itself lives on the plan screen and is never rendered here.
 *
 * The focused container and the water row are the plan screen's OWN composables
 * ([PlanMealCard], [WaterRow]), imported rather than reimplemented — so the two surfaces cannot
 * drift apart visually, the same way TODAY-13 stops them drifting behaviourally.
 */
@Composable
fun TodayScreen(
    model: TodayHighlights = sampleHighlights,
    actions: TodayActions = TodayActions(),
    undo: UndoState? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .semantics { testTag = "today_screen" },
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            FuelledWordmark(markSize = 26.dp)
            Spacer(Modifier.weight(1f))
            Text(
                text = model.today.date.dayHeaderLabel().uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { testTag = "today_title" },
            )
        }

        HeroCard(model.today)
        ProteinFocus(model.today.protein)

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "UP NEXT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { testTag = "today_up_next" },
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Water ${model.plan.waterMl.litresLabel()} / ${WATER_DAY_GOAL_ML.litresLabel()} L",
                style = MaterialTheme.typography.labelMedium,
                color = FuelledColors.Info,
                modifier = Modifier.semantics { testTag = "today_water_total" },
            )
            Spacer(Modifier.width(12.dp))
            // TODAY-14: the method's veg-with-two-meals rule at a glance.
            Text(
                text = "Veg ${model.plan.vegMeals} of $VEG_MEAL_GOAL",
                style = MaterialTheme.typography.labelMedium,
                color = FuelledColors.Success,
                modifier = Modifier.semantics { testTag = "today_veg_total" },
            )
        }

        // TODAY-09: exactly one container — the focused one. A day whose six slots are all done
        // or missed has no focus at all (PLAN-17), and says so rather than showing an empty card.
        model.focus?.let { focus ->
            PlanMealCard(
                meal = focus.toUi(),
                onToggleDone = { actions.onToggleFocusDone(focus.slot, !focus.done) },
                onAddFood = { actions.onAddToMeal(model.plan.date, focus.slot) },
                onDeleteEntry = actions.onDeleteEntry,
                // TODAY-07 names this surface's add control `today_add_<slot>`; the same
                // composable on the plan screen tags itself `plan_add_<slot>`.
                tagPrefix = "today",
            )
        } ?: Text(
            text = "That's the day — six for six. Next up is tomorrow's breakfast.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { testTag = "today_day_complete" },
        )

        // TODAY-10: the next water not yet ticked. Absent once all six are done — six ticked
        // containers and nothing left to prompt is a finished goal, not an empty row.
        // ENTRY-02: the undo sits directly under the container it was removed from.
        undo?.let { UndoBar(name = it.name, onUndo = it.onUndo, tagPrefix = "today") }

        model.nextWater?.let { water ->
            WaterRow(
                water = PlanWaterUi(index = water.index, time = water.time.clockLabel(), done = water.done),
                onToggle = { actions.onToggleWater(water.index, !water.done) },
                tagPrefix = "today",
            )
        }

        // TODAY-11: bucket-based, because Supplement carries a free-text `timing` and a taken
        // flag — no clock time — so "the current bucket" is genuinely underivable by hour.
        model.supplements?.let { bucket ->
            ListItemCard(
                title = bucket.name,
                subtitle = "Supplements · ${bucket.taken} of ${bucket.total} taken",
                onClick = actions.onOpenSupplements,
                leading = { Icon(Icons.Filled.Medication, contentDescription = null, tint = FuelledColors.Info) },
                trailing = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.semantics { testTag = "today_supplements" },
            )
        }

        // TODAY-12: the one control into the full week. Today never renders the week itself.
        ListItemCard(
            title = "This week",
            subtitle = "Plan the week — all six meals, every day",
            onClick = actions.onOpenPlan,
            leading = { Icon(Icons.Filled.Today, contentDescription = null, tint = FuelledColors.Primary) },
            trailing = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier.semantics { testTag = "today_plan_link" },
        )
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * The focused container as the plan screen's card model. Reuses the plan's own mapper so the
 * DONE/FOCUSED/LATE/MISSED rendering is decided in exactly one place (see MealPlanRoute.kt).
 */
private fun PlanSlotView.toUi(): PlanMealUi = PlanMealUi(
    key = slot.uiKey,
    label = slot.label,
    time = time.clockLabel(),
    state = uiState(),
    entries = entries.map { PlanEntryUi(it.id, it.name, it.serving, it.kcal, it.proteinG, it.servings) },
    tickedEmpty = tickedEmpty,
    // PLAN-25. Carried, not defaulted: this mapper and the plan's are two hands on the same
    // card, and a field only one of them fills is a field the two surfaces disagree about —
    // Today went on saying "up now" for a 12:00 lunch at 11:32 while the plan already said
    // "at 12:00" (observed on-device, 2026-07-29).
    due = due,
)

// The per-macro colours are assigned here, in presentation — the domain model carries none.
private fun MacroProgress.colored(color: Color): Pair<MacroProgress, Color> = this to color

@Composable
internal fun HeroCard(model: TodayModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressRing(
            progress = if (model.targetKcal <= 0) 0f else model.consumedKcal.toFloat() / model.targetKcal,
            modifier = Modifier.size(132.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = model.remainingKcal.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text("kcal left", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            listOf(
                model.protein.colored(FuelledColors.Protein),
                model.carbs.colored(FuelledColors.Carbs),
                model.fat.colored(FuelledColors.Fat),
            ).forEach { (m, color) ->
                StatBar(
                    progress = if (m.target <= 0) 0f else m.current.toFloat() / m.target,
                    color = color,
                    label = m.label,
                    valueText = "${m.current} / ${m.target}${m.unit}",
                )
            }
        }
    }
}

@Composable
internal fun ProteinFocus(protein: MacroProgress) {
    val toGo = (protein.target - protein.current).coerceAtLeast(0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("PROTEIN", style = MaterialTheme.typography.labelSmall, color = FuelledColors.Primary)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${protein.current}",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = " / ${protein.target} g",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (toGo == 0) "goal hit" else "${toGo}g",
                style = MaterialTheme.typography.titleLarge,
                color = FuelledColors.Primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (toGo == 0) "nice work" else "to go",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// `MealCard` and `EntryRow` lived here: the day's log rendered as one card per meal group.
// Decision 13 made Today the HIGHLIGHTS surface — one focused container, not the whole day —
// so the card the screen now renders is the plan's own [PlanMealCard], imported rather than
// kept as a near-identical twin. Its `today_add_<slot>` tag (TODAY-07) survives via that
// card's tagPrefix. Deleted rather than left unused: a second meal card is exactly the thing
// that later gets edited instead of the real one.

// `TodayEmptyLog` lived here: the whole-day empty state (`today_empty`) and its untargeted add
// control (`today_empty_add`) — TODAY-04's old form and TODAY-08, withdrawn. An empty day is no
// longer a special screen state: it shows its focused container like any other day, and that
// container's own empty body is the add control (TODAY-04, PLAN-04).
