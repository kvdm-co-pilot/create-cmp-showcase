package com.kvdm.fuelled.presentation.foods

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.presentation.components.Tag
import com.kvdm.fuelled.presentation.theme.FuelledColors

// ── Foods: the searchable catalog (the exemplar feature) ─────────────────────────────
// Search filters the local catalog; each row opens the Food detail. Stateless over a list;
// the Room-backed repository/ViewModel wires in during the architecture pass.

data class Food(
    val id: String,
    val name: String,
    val brand: String,
    val serving: String,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
)

// PREVIEW/DEMO fixtures — the screen's preview seam (UI-first pattern). Not production
// data: replaced by the Room-backed repository when Foods is wired as the exemplar feature.
val sampleFoods = listOf(
    Food("1", "Chicken breast", "Raw · skinless", "100 g", 165, 31, 0, 4),
    Food("2", "Whey protein", "Gold Standard", "1 scoop · 30 g", 120, 24, 3, 2),
    Food("3", "Rolled oats", "Quaker", "80 g", 303, 11, 54, 6),
    Food("4", "Greek yogurt 0%", "Fage", "170 g", 100, 17, 6, 0),
    Food("5", "Banana", "Medium", "1 · 118 g", 105, 1, 27, 0),
    Food("6", "White rice", "Cooked", "150 g", 195, 4, 42, 0),
    Food("7", "Almonds", "Raw", "20 g", 116, 4, 4, 10),
)

@Composable
fun FoodsScreen(
    foods: List<Food> = sampleFoods,
    onFoodClick: (Food) -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, foods) {
        if (query.isBlank()) foods
        else foods.filter { it.name.contains(query, ignoreCase = true) || it.brand.contains(query, ignoreCase = true) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).semantics { testTag = "foods_screen" },
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Foods",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { testTag = "foods_title" },
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search foods") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FuelledColors.Primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.id }) { food ->
                FoodRow(food, onClick = { onFoodClick(food) })
            }
        }
    }
}

@Composable
private fun FoodRow(food: Food, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(food.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = "${food.brand} · ${food.serving}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tag("P", "${food.proteinG}g", FuelledColors.Protein)
                Tag("C", "${food.carbsG}g", FuelledColors.Carbs)
                Tag("F", "${food.fatG}g", FuelledColors.Fat)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = food.kcal.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text("kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
