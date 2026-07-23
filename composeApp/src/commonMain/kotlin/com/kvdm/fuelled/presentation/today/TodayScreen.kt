package com.kvdm.fuelled.presentation.today

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.presentation.brand.FuelledWordmark
import com.kvdm.fuelled.presentation.theme.FuelledColors

// ── Today: the daily macro dashboard ────────────────────────────────────────────────
// The hero screen. Calorie ring (consumed vs target) + protein-first macro bars + the
// day's log. Stateless: it renders a [TodayModel]; a ViewModel/Room source wires in later
// (the exemplar-feature conversation). Sample data keeps the preview honest for now.

data class MacroProgress(val label: String, val current: Int, val target: Int, val unit: String, val color: Color)
data class LogEntry(val name: String, val serving: String, val kcal: Int, val proteinG: Int)
data class MealGroup(val name: String, val entries: List<LogEntry>) {
    val kcal: Int get() = entries.sumOf { it.kcal }
}
data class TodayModel(
    val dateLabel: String,
    val consumedKcal: Int,
    val targetKcal: Int,
    val protein: MacroProgress,
    val carbs: MacroProgress,
    val fat: MacroProgress,
    val meals: List<MealGroup>,
) {
    val remainingKcal: Int get() = (targetKcal - consumedKcal).coerceAtLeast(0)
}

val sampleToday = TodayModel(
    dateLabel = "Wednesday, Jul 23",
    consumedKcal = 1840,
    targetKcal = 2400,
    protein = MacroProgress("Protein", 148, 180, "g", FuelledColors.Protein),
    carbs = MacroProgress("Carbs", 190, 260, "g", FuelledColors.Carbs),
    fat = MacroProgress("Fat", 52, 70, "g", FuelledColors.Fat),
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
        model.meals.forEach { MealCard(it) }
        Spacer(Modifier.height(8.dp))
    }
}

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
        CalorieRing(
            consumed = model.consumedKcal,
            target = model.targetKcal,
            remaining = model.remainingKcal,
            modifier = Modifier.size(132.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MacroBar(model.protein)
            MacroBar(model.carbs)
            MacroBar(model.fat)
        }
    }
}

@Composable
private fun CalorieRing(consumed: Int, target: Int, remaining: Int, modifier: Modifier = Modifier) {
    val progress = if (target <= 0) 0f else (consumed.toFloat() / target).coerceIn(0f, 1f)
    val track = FuelledColors.OutlineVariant
    val arc = FuelledColors.Primary
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round))
            drawArc(color = arc, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = remaining.toString(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "kcal left",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MacroBar(m: MacroProgress) {
    val progress = if (m.target <= 0) 0f else (m.current.toFloat() / m.target).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(m.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${m.current} / ${m.target}${m.unit}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            Box(
                Modifier.fillMaxWidth(progress).height(8.dp).clip(RoundedCornerShape(4.dp)).background(m.color),
            )
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
