package com.kvdm.fuelled.presentation.foods

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.presentation.components.AppHeader
import com.kvdm.fuelled.presentation.components.AppPrimaryButton
import com.kvdm.fuelled.presentation.components.BaseScreen
import com.kvdm.fuelled.presentation.theme.FuelledColors

// ── Food detail: the full nutritional breakdown + log action ─────────────────────────

@Composable
fun FoodDetailScreen(
    foodId: String = sampleFoods.first().id,
    onBack: () -> Unit = {},
    onLog: () -> Unit = {},
) {
    // PREVIEW/DEMO: resolve from the in-memory sample catalog. Replaced by the
    // ViewModel + repository (Room) when Foods is wired as the exemplar feature —
    // see docs UI-first pattern; the nav layer already passes only the id.
    val food = sampleFoods.firstOrNull { it.id == foodId } ?: sampleFoods.first()
    val pKcal = food.proteinG * 4
    val cKcal = food.carbsG * 4
    val fKcal = food.fatG * 9
    val totalMacroKcal = (pKcal + cKcal + fKcal).coerceAtLeast(1)

    BaseScreen { innerPadding ->
      Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        AppHeader(title = food.name, screenTag = "food_detail", onBack = onBack)

        Text(
            text = "${food.brand} · ${food.serving}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Hero calories
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = food.kcal.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = " kcal",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            Text(
                text = "per ${food.serving}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            // Stacked macro proportion bar
            Row(
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            ) {
                MacroSegment(pKcal, totalMacroKcal, FuelledColors.Protein)
                MacroSegment(cKcal, totalMacroKcal, FuelledColors.Carbs)
                MacroSegment(fKcal, totalMacroKcal, FuelledColors.Fat)
            }
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MacroStat("Protein", food.proteinG, pKcal * 100 / totalMacroKcal, FuelledColors.Protein)
                MacroStat("Carbs", food.carbsG, cKcal * 100 / totalMacroKcal, FuelledColors.Carbs)
                MacroStat("Fat", food.fatG, fKcal * 100 / totalMacroKcal, FuelledColors.Fat)
            }
        }

        AppPrimaryButton(
            text = "Log this food",
            onClick = onLog,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
      }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.MacroSegment(part: Int, total: Int, color: Color) {
    if (part <= 0) return
    Spacer(
        modifier = Modifier
            .weight(part.toFloat() / total)
            .height(10.dp)
            .background(color),
    )
}

@Composable
private fun MacroStat(label: String, grams: Int, pct: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${grams}g", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        Text("$pct%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
