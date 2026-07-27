package com.kvdm.fuelled.presentation.mealplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.domain.model.MacroProgress
import com.kvdm.fuelled.domain.model.TodayModel
import com.kvdm.fuelled.presentation.brand.FuelledWordmark
import com.kvdm.fuelled.presentation.components.ListItemCard
import com.kvdm.fuelled.presentation.components.ScreenColumn
import com.kvdm.fuelled.presentation.theme.FuelledColors
import kotlinx.datetime.LocalDate
import com.kvdm.fuelled.presentation.today.HeroCard
import com.kvdm.fuelled.presentation.today.ProteinFocus
import com.kvdm.fuelled.presentation.today.dayHeaderLabel

// ── Today as the highlights dashboard (DESIGN DRAFT — feature-design:meal-plan) ──────────
// Decision 13 (docs/features/meal-plan.md): the Today tab belongs to no single feature — it
// is the derived "now" surface across meals, water, and supplements. The hero ring, macro
// bars, and protein focus are the REAL Today components (imported, not copied); the focused
// meal container and next water container are the plan screen's own cards rendered once
// ([PlanMealCard], [WaterRow] — same composables, so the two surfaces cannot drift apart).
// Below them: the supplements bucket highlight and the one link to the full week plan.
// At build time this layout becomes TodayScreen's body; it renders standalone here so the
// design can be judged and signed on its own output.

data class TodayHighlightsUi(
    val today: TodayModel,
    /** The focused slot — the plan's derived state, projected. NEXT, or LATE past grace. */
    val focus: PlanMealUi,
    val nextWater: PlanWaterUi,
    val litresDone: String,
    val litresGoal: String,
    /** PLAN-22/TODAY-14: meal containers holding a vegetable, against the method's 2. */
    val vegDone: Int,
    val vegGoal: Int,
    /** Bucket-based (decision 13): `timing` is a free string + `taken` flag — no clock times. */
    val supplementBucket: String,
    val supplementsTaken: Int,
    val supplementsTotal: Int,
)

// PREVIEW/DEMO fixtures — fixed data, never a clock read (ARCH-12). Mid-day: lunch focused
// and LATE (the dashboard's most communicative state), third water pending, morning stack
// half-taken.
//
// These are declared HERE rather than sliced out of the plan screen's fixtures, even though
// they describe the same day: ARCH-12 keeps a sample symbol inside its own file, and a shared
// preview fixture would only ASSERT the projection rhetorically. The real guarantee that
// Today and the plan screen cannot disagree is TODAY-13 — one derived state, one write path —
// and that is proven on the write path, not in demo data.
private val highlightsToday = TodayModel(
    date = LocalDate(2026, 7, 22),
    consumedKcal = 1461,
    targetKcal = 2400,
    protein = MacroProgress("Protein", 121, 180, "g"),
    carbs = MacroProgress("Carbs", 168, 260, "g"),
    fat = MacroProgress("Fat", 31, 70, "g"),
    meals = emptyList(),
)

val sampleHighlightsMidday = TodayHighlightsUi(
    today = highlightsToday,
    focus = PlanMealUi(
        key = "lunch",
        label = "Lunch",
        time = "12:00",
        state = PlanSlotState.FOCUSED_LATE,
        entries = listOf(
            PlanEntryUi("Chicken breast & rice", "200 g · 150 g", 620, 58),
            PlanEntryUi("Mixed greens", "1 bowl", 90, 3),
        ),
    ),
    nextWater = PlanWaterUi(index = 3, time = "13:15", done = false),
    litresDone = "1.0",
    litresGoal = "3.0",
    vegDone = 1,
    vegGoal = 2,
    supplementBucket = "Morning stack",
    supplementsTaken = 2,
    supplementsTotal = 4,
)

// A fresh day: ring reads the full target as remaining, breakfast holds focus with its add
// affordance as the card body — the whole-day empty state's replacement (decision 2 via 13).
val sampleHighlightsEmpty = TodayHighlightsUi(
    today = highlightsToday.copy(
        consumedKcal = 0,
        protein = MacroProgress("Protein", 0, 180, "g"),
        carbs = MacroProgress("Carbs", 0, 260, "g"),
        fat = MacroProgress("Fat", 0, 70, "g"),
    ),
    focus = PlanMealUi(
        key = "breakfast",
        label = "Breakfast",
        time = "07:00",
        state = PlanSlotState.FOCUSED,
        entries = emptyList(),
    ),
    nextWater = PlanWaterUi(index = 1, time = "08:15", done = false),
    litresDone = "0.0",
    litresGoal = "3.0",
    vegDone = 0,
    vegGoal = 2,
    supplementBucket = "Morning stack",
    supplementsTaken = 0,
    supplementsTotal = 4,
)

/**
 * The highlights dashboard — DESIGN DRAFT, stateless and stub-driven. Open the app, see the
 * next thing to do: the focus card IS the screen. Ticking it advances the projection in
 * place (self-advancing); everything below is one glance or one tap deep.
 */
@Composable
fun TodayHighlightsScreen(model: TodayHighlightsUi = sampleHighlightsMidday) {
    ScreenColumn(screenTag = "today_highlights", scrollable = true) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
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
                    text = "Water ${model.litresDone} / ${model.litresGoal} L",
                    style = MaterialTheme.typography.labelMedium,
                    color = FuelledColors.Info,
                    modifier = Modifier.semantics { testTag = "today_water_total" },
                )
                Spacer(Modifier.width(12.dp))
                // TODAY-14: the method's veg-with-two-meals rule at a glance.
                Text(
                    text = "Veg ${model.vegDone} of ${model.vegGoal}",
                    style = MaterialTheme.typography.labelMedium,
                    color = FuelledColors.Success,
                    modifier = Modifier.semantics { testTag = "today_veg_total" },
                )
            }
            PlanMealCard(model.focus)
            WaterRow(model.nextWater)

            ListItemCard(
                title = model.supplementBucket,
                subtitle = "Supplements · ${model.supplementsTaken} of ${model.supplementsTotal} taken",
                onClick = {},
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
            ListItemCard(
                title = "This week",
                subtitle = "Plan the week — all six meals, every day",
                onClick = {},
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
}
