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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.presentation.components.AppHeader
import com.kvdm.fuelled.presentation.components.AppPrimaryButton
import com.kvdm.fuelled.presentation.components.BaseScreen
import com.kvdm.fuelled.presentation.components.ContentStateContainer
import com.kvdm.fuelled.presentation.theme.FuelledColors
import org.koin.compose.viewmodel.koinViewModel

// ── Food detail: the full nutritional breakdown + log action ─────────────────────────
// FoodDetailRoute is the VM-backed nav destination — it resolves the Food by id through
// FoodDetailViewModel (the repository), then renders it. FoodDetailScreen stays stateless
// and sample-defaulted for the preview registry (no VM, no Koin).

/**
 * The VM-backed detail destination the nav graph hosts. Resolves [foodId] via
 * [FoodDetailViewModel] and presents Loading/Content/Error through [ContentStateContainer];
 * a missing id renders the mapped NotFound copy, never a crash.
 */
@Composable
fun FoodDetailRoute(
    foodId: String,
    onBack: () -> Unit,
    onLog: () -> Unit = {},
    viewModel: FoodDetailViewModel = koinViewModel(),
) {
    LaunchedEffect(foodId) { viewModel.load(foodId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    BaseScreen { innerPadding ->
        ContentStateContainer(
            state = state,
            screenTag = "food_detail",
            onRetry = { viewModel.load(foodId) },
        ) { food ->
            FoodDetailContent(
                food = food,
                onBack = onBack,
                onLog = onLog,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

/**
 * The stateless detail — the preview/UI-first seam. Renders a resolved [Food] inside
 * [BaseScreen], defaulting to a sample so the preview registry can render it without a VM.
 */
@Composable
fun FoodDetailScreen(
    food: Food = sampleFoods.first(),
    onBack: () -> Unit = {},
    onLog: () -> Unit = {},
) {
    BaseScreen { innerPadding ->
        FoodDetailContent(
            food = food,
            onBack = onBack,
            onLog = onLog,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun FoodDetailContent(
    food: Food,
    onBack: () -> Unit,
    onLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pKcal = food.proteinG * 4
    val cKcal = food.carbsG * 4
    val fKcal = food.fatG * 9
    val totalMacroKcal = (pKcal + cKcal + fKcal).coerceAtLeast(1)

    Column(
        modifier = modifier
            .fillMaxSize()
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
