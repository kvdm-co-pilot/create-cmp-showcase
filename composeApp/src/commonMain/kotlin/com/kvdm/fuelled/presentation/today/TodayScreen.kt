package com.kvdm.fuelled.presentation.today

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.kvdm.fuelled.domain.model.LogEntry
import com.kvdm.fuelled.domain.model.TodayModel
import com.kvdm.fuelled.presentation.brand.FuelledWordmark
import com.kvdm.fuelled.presentation.components.ContentStateContainer
import com.kvdm.fuelled.presentation.components.ProgressRing
import com.kvdm.fuelled.presentation.components.StatBar
import com.kvdm.fuelled.presentation.theme.FuelledColors
import org.koin.compose.viewmodel.koinViewModel

// ── Today: the daily macro dashboard (the hero screen) ───────────────────────────────
// The UI-first preview seam, mirroring the Foods exemplar's two entry points:
//   • TodayScreen — STATELESS, sample-defaulted. The preview registry renders it with no VM.
//   • TodayRoute  — the VM-backed tab the nav graph hosts: Loading/Content/Error are driven
//     by TodayViewModel through ContentStateContainer.
// Macro colours are a PRESENTATION concern assigned here per-macro; the domain MacroProgress
// carries none (ARCH-02 keeps domain free of Compose types).

// PREVIEW/DEMO fixture — the screen's preview seam. Not production data: the Room-backed
// TodayRepositoryImpl seeds its own realistic day for the VM-backed TodayRoute.
val sampleToday = TodayModel(
    dateLabel = "Wednesday, Jul 23",
    consumedKcal = 1461,
    targetKcal = 2400,
    protein = MacroProgress("Protein", 121, 180, "g"),
    carbs = MacroProgress("Carbs", 168, 260, "g"),
    fat = MacroProgress("Fat", 31, 70, "g"),
    meals = listOf(
        MealGroup("Breakfast", listOf(
            LogEntry("Rolled oats & whey", "80 g · 1 scoop", 430, 38),
            LogEntry("Banana", "1 medium", 105, 1),
        )),
        MealGroup("Lunch", listOf(
            LogEntry("Chicken breast & rice", "200 g · 150 g", 620, 58),
            LogEntry("Mixed greens", "1 bowl", 90, 3),
        )),
        MealGroup("Snack", listOf(
            LogEntry("Greek yogurt 0%", "170 g", 100, 17),
            LogEntry("Almonds", "20 g", 116, 4),
        )),
    ),
)

/**
 * The VM-backed Today tab the nav graph hosts. The Loading/Content/Error state machine lives in
 * [TodayViewModel]; this wrapper only renders it through [ContentStateContainer] (which owns the
 * `today_loading`/`today_error`/`today_retry` arms). A day with no entries is still Content — the
 * stateless [TodayScreen] shows its own `today_empty` affordance while the ring stays visible.
 * A tab: it inherits BaseScreen (insets) from AppShell, so it does not re-wrap it (SHELL-05).
 */
@Composable
fun TodayRoute(
    viewModel: TodayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContentStateContainer(state = state, screenTag = "today", onRetry = viewModel::load) { model ->
        TodayScreen(model = model)
    }
}

/**
 * The stateless dashboard — the preview/UI-first seam. Renders a [TodayModel]; defaults to a
 * sample so the preview registry can render it without a VM or Koin. When the day has no logged
 * entries the log area shows the `today_empty` affordance and the ring reads the full target as
 * remaining (TODAY-04). The production path is [TodayRoute] + [TodayViewModel].
 */
@Composable
fun TodayScreen(model: TodayModel = sampleToday) {
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
                text = model.dateLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { testTag = "today_title" },
            )
        }

        HeroCard(model)
        ProteinFocus(model.protein)

        Text(
            text = "TODAY'S LOG",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (model.meals.isEmpty()) {
            TodayEmptyLog()
        } else {
            model.meals.forEach { MealCard(it) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// The per-macro colours are assigned here, in presentation — the domain model carries none.
private fun MacroProgress.colored(color: Color): Pair<MacroProgress, Color> = this to color

@Composable
private fun HeroCard(model: TodayModel) {
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
private fun ProteinFocus(protein: MacroProgress) {
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

@Composable
private fun MealCard(meal: MealGroup) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(meal.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${meal.kcal} kcal",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        meal.entries.forEach { EntryRow(it) }
    }
}

@Composable
private fun EntryRow(entry: LogEntry) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(entry.serving, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${entry.kcal} kcal", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text("${entry.proteinG}g P", style = MaterialTheme.typography.labelMedium, color = FuelledColors.Primary)
        }
    }
}

/**
 * The empty log affordance (TODAY-04): shown in place of the meal cards when the day has no
 * logged entries. The hero ring above still renders — reading the full target as remaining —
 * so the empty state lives inside the content, not the dataless container Empty arm.
 */
@Composable
private fun TodayEmptyLog() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 32.dp)
            .semantics { testTag = "today_empty" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No meals logged yet",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
