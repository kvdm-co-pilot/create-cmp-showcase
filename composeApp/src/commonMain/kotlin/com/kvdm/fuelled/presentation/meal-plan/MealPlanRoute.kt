package com.kvdm.fuelled.presentation.mealplan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvdm.fuelled.domain.model.LogStatus
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.PlanDay
import com.kvdm.fuelled.domain.model.PlanSlotView
import com.kvdm.fuelled.domain.model.VEG_MEAL_GOAL
import com.kvdm.fuelled.domain.model.WATER_DAY_GOAL_ML
import com.kvdm.fuelled.presentation.components.ContentStateContainer
import com.kvdm.fuelled.presentation.today.label
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.koin.compose.viewmodel.koinViewModel

// ── The plan screen's route: domain PlanDay → the screen's PlanDayUi ─────────────────────
// The mapping lives HERE rather than in the ViewModel or the domain, because everything it
// does is presentation: formatting a time as "07:00", a date as "Thu 23", millilitres as
// "1.5", and folding the domain's four independent booleans (done/focused/late/missed) into
// the one PlanSlotState the card renders. The domain carries values; the screen carries their
// rendering (ARCH-02).

/** Two digits, always — `LocalTime.toString()` drops a zero minute ("7:0" reads as broken). */
internal fun LocalTime.clockLabel(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** Millilitres as the litres the screen shows, one decimal: 1500 → "1.5" (PLAN-08). */
internal fun Int.litresLabel(): String = "${this / 1000}.${(this % 1000) / 100}"

/** A strip chip: "Today" for the current logical day, otherwise "Thu 23" (PLAN-11). */
internal fun LocalDate.stripLabel(today: LocalDate): String =
    if (this == today) "Today"
    else "${dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} $day"

/**
 * The slot's rendered state — the domain's four booleans folded into one, in priority order.
 *
 * Done wins over everything: a ticked slot is never missed and never focused, whatever the
 * clock says. Missed comes next, so a slot the day has moved past reads muted rather than
 * competing for attention. Only then does focus apply, split by lateness. The order is what
 * makes the states mutually exclusive on screen even though the domain tracks them separately.
 */
internal fun PlanSlotView.uiState(): PlanSlotState = when {
    done -> PlanSlotState.DONE
    // START-02 sits ABOVE missed in the priority order for the same reason done does: a slot
    // that predates the install was never this app's to judge, so nothing else may label it.
    beforeStart -> PlanSlotState.BEFORE_START
    missed -> PlanSlotState.MISSED
    focused && late -> PlanSlotState.FOCUSED_LATE
    focused -> PlanSlotState.FOCUSED
    else -> PlanSlotState.UPCOMING
}

/** The stable key the test tags and the callbacks address a container by — the enum, lowercased. */
internal val MealSlot.uiKey: String get() = name.lowercase()

/** The inverse, for mapping a tap's key back to the slot it names. */
internal fun mealSlotForKey(key: String): MealSlot? =
    MealSlot.entries.firstOrNull { it.uiKey == key }

/** Domain day → the screen's model (PLAN-02/PLAN-08/PLAN-22). */
internal fun PlanDay.toUi(stripDays: List<LocalDate>, today: LocalDate): PlanDayUi = PlanDayUi(
    stripDays = stripDays.map { it.stripLabel(today) },
    selectedDay = stripDays.indexOf(date).coerceAtLeast(0),
    litresDone = waterMl.litresLabel(),
    litresGoal = WATER_DAY_GOAL_ML.litresLabel(),
    vegDone = vegMeals,
    vegGoal = VEG_MEAL_GOAL,
    meals = slots.map { view ->
        PlanMealUi(
            key = view.slot.uiKey,
            label = view.slot.label,
            time = view.time.clockLabel(),
            state = view.uiState(),
            entries = view.entries.map { entry ->
                PlanEntryUi(
                    id = entry.id,
                    name = entry.name,
                    // PLAN-21: a plan the day moved past without logging says so on its own
                    // row. It is history, not an outstanding promise — and never counted.
                    serving = if (entry.status == LogStatus.PLANNED && !isCurrentDay && date < today) {
                        "${entry.serving} · planned, not eaten"
                    } else {
                        entry.serving
                    },
                    kcal = entry.kcal,
                    proteinG = entry.proteinG,
                    servings = entry.servings,
                )
            },
            tickedEmpty = view.tickedEmpty,
            due = view.due,
        )
    },
    waters = water.map { PlanWaterUi(index = it.index, time = it.time.clockLabel(), done = it.done) },
)

/**
 * The VM-backed plan screen the nav graph hosts (`plan/{date}`, PLAN-11).
 *
 * The route's date arrives as the ViewModel's SEED, not as a parameter here (PLAN-24). There is
 * deliberately no route-level date left to re-apply: this composable re-enters composition every
 * time the tray closes, and the effect that used to re-aim it discarded whatever day the strip
 * had selected. The selection lives in the nav-entry-scoped ViewModel, which survives the trip.
 *
 * @param onAddToMeal where an add control goes: the tray, already targeted at this container's
 *   logical day and slot (PLAN-04) — the tap carries the target, so the tray never asks.
 */
@Composable
fun MealPlanRoute(
    onAddToMeal: (LocalDate, MealSlot) -> Unit,
    onOpenTimes: () -> Unit,
    viewModel: MealPlanViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lastDeleted by viewModel.lastDeleted.collectAsStateWithLifecycle()

    // The strip window and the anchor day are observed too — a plan left open across 04:00
    // re-centres instead of offering yesterday's nine days.
    val stripDays by viewModel.stripDays.collectAsStateWithLifecycle()
    // HIST-04: withheld on a past day, where it would overwrite lived history.
    val canCopyForward by viewModel.canCopyForward.collectAsStateWithLifecycle()
    val anchorDay by viewModel.todayStream.collectAsStateWithLifecycle()

    // No onRetry: the state is observed, so a transient failure recovers on the next emission.
    ContentStateContainer(
        state = state,
        screenTag = "meal_plan",
    ) { day ->
        MealPlanDayScreen(
            day = day.toUi(stripDays, anchorDay).copy(canCopyForward = canCopyForward),
            actions = PlanDayActions(
                onSelectDay = { index -> stripDays.getOrNull(index)?.let(viewModel::select) },
                onToggleDone = { key, done -> mealSlotForKey(key)?.let { viewModel.setDone(it, done) } },
                onToggleWater = viewModel::setWater,
                onAddFood = { key -> mealSlotForKey(key)?.let { onAddToMeal(day.date, it) } },
                // The rest of the week: the six days after this one, which is what "prep the
                // week" means for a day strip that only ever shows seven ahead (PLAN-11/PLAN-20).
                onCopyForward = { viewModel.copyForward(days = 6) },
                onOpenTimes = onOpenTimes,
                // UX-02: the observed state re-derives the day after the delete (RS-01) —
                // totals, focus, and the veg count follow without a reload.
                onDeleteEntry = viewModel::deleteEntry,
                onEntryServings = viewModel::setServings,
            ),
            undo = lastDeleted?.let { UndoState(it.name, viewModel::undoDelete) },
        )
    }
}
