package com.kvdm.fuelled.presentation.supplements

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.presentation.theme.FuelledColors

// ── Supplements: today's stack, grouped by timing, tap-to-take ────────────────────────

data class Supplement(val id: String, val name: String, val dose: String, val timing: String, val defaultTaken: Boolean)

val sampleSupplements = listOf(
    Supplement("1", "Creatine", "5 g", "Morning", true),
    Supplement("2", "Vitamin D3", "2000 IU", "Morning", true),
    Supplement("3", "Omega-3", "1 g", "Morning", false),
    Supplement("4", "Caffeine", "200 mg", "Pre-workout", false),
    Supplement("5", "Beta-alanine", "3 g", "Pre-workout", false),
    Supplement("6", "Magnesium", "400 mg", "Evening", false),
)

@Composable
fun SupplementsScreen(supplements: List<Supplement> = sampleSupplements) {
    val taken = remember { mutableStateListOf<String>().apply { addAll(supplements.filter { it.defaultTaken }.map { it.id }) } }
    val groups = remember(supplements) { supplements.groupBy { it.timing } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .semantics { testTag = "supplements_screen" },
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Column {
            Text(
                text = "Supplements",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { testTag = "supplements_title" },
            )
            Text(
                text = "Today's stack",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TakenSummary(taken.size, supplements.size)

        groups.forEach { (timing, items) ->
            TimingGroup(timing, items, taken)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TakenSummary(taken: Int, total: Int) {
    val progress = if (total <= 0) 0f else taken.toFloat() / total
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$taken", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                " of $total taken",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Box(Modifier.fillMaxWidth(progress).height(8.dp).clip(RoundedCornerShape(4.dp)).background(FuelledColors.Primary))
        }
    }
}

@Composable
private fun TimingGroup(timing: String, items: List<Supplement>, taken: MutableList<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(timing.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEach { supp ->
            SupplementRow(supp, isTaken = taken.contains(supp.id), onToggle = {
                if (taken.contains(supp.id)) taken.remove(supp.id) else taken.add(supp.id)
            })
        }
    }
}

@Composable
private fun SupplementRow(supp: Supplement, isTaken: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                supp.name.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = FuelledColors.Primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(supp.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(supp.dose, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onToggle, modifier = Modifier.size(48.dp)) {
            val ring = if (isTaken) FuelledColors.Primary else MaterialTheme.colorScheme.surfaceContainerHigh
            val tick = if (isTaken) FuelledColors.OnPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(Modifier.size(28.dp).clip(CircleShape).background(ring), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = if (isTaken) "Taken" else "Mark taken", tint = tick, modifier = Modifier.size(18.dp))
            }
        }
    }
}
