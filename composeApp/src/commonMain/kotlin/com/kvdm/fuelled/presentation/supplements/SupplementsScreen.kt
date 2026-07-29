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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.presentation.components.ContentStateContainer
import com.kvdm.fuelled.presentation.components.StatBar
import com.kvdm.fuelled.presentation.theme.FuelledColors
import org.koin.compose.viewmodel.koinViewModel

// ── Supplements: today's stack, grouped by timing, tap-to-take (persisted) ────────────
// The UI-first preview seam, mirroring the Foods/Today exemplars' two entry points:
//   • SupplementsScreen — STATELESS, sample-defaulted. The preview registry renders it with
//     no VM (tap is a no-op in the preview seam).
//   • SupplementsRoute  — the VM-backed tab the nav graph hosts: Loading/Content/Empty/Error
//     are driven by SupplementsViewModel through ContentStateContainer, and tap-to-take
//     persists to Room. A tab: it inherits BaseScreen (insets) from AppShell, so it does not
//     re-wrap it (SHELL-05).

// PREVIEW/DEMO fixture — the screen's preview seam. Not production data: the Room-backed
// SupplementRepositoryImpl seeds its own stack for the VM-backed SupplementsRoute.
val sampleSupplementStack = SupplementStackUi(
    groups = listOf(
        SupplementGroup(
            "Morning",
            listOf(
                Supplement("1", "Creatine", "5 g", "Morning", taken = true),
                Supplement("2", "Vitamin D3", "2000 IU", "Morning", taken = true),
                Supplement("3", "Omega-3", "1 g", "Morning", taken = false),
            ),
        ),
        SupplementGroup(
            "Pre-workout",
            listOf(
                Supplement("4", "Caffeine", "200 mg", "Pre-workout", taken = false),
                Supplement("5", "Beta-alanine", "3 g", "Pre-workout", taken = false),
            ),
        ),
        SupplementGroup(
            "Evening",
            listOf(Supplement("6", "Magnesium", "400 mg", "Evening", taken = false)),
        ),
    ),
    takenCount = 2,
    total = 6,
)

/**
 * The VM-backed Supplements tab the nav graph hosts. The Loading/Content/Empty/Error state
 * machine lives in [SupplementsViewModel]; this wrapper only renders it through
 * [ContentStateContainer] (which owns the `supplements_loading`/`supplements_empty`/
 * `supplements_error`/`supplements_retry` arms). Tap-to-take routes to the VM, which persists
 * and re-reads (SUPP-03).
 */
@Composable
fun SupplementsRoute(
    viewModel: SupplementsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContentStateContainer(state = state, screenTag = "supplements", onRetry = {}) { stack ->
        SupplementsScreen(stack = stack, onToggleTaken = viewModel::onToggleTaken)
    }
}

/**
 * The stateless stack — the preview/UI-first seam. Renders a [SupplementStackUi]; defaults to a
 * sample so the preview registry can render it without a VM or Koin. The production path is
 * [SupplementsRoute] + [SupplementsViewModel]; there, tap-to-take persists to Room.
 */
@Composable
fun SupplementsScreen(
    stack: SupplementStackUi = sampleSupplementStack,
    onToggleTaken: (id: String, taken: Boolean) -> Unit = { _, _ -> },
) {
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

        TakenSummary(taken = stack.takenCount, total = stack.total, progress = stack.progress)

        stack.groups.forEach { group ->
            TimingGroup(group, onToggleTaken)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TakenSummary(taken: Int, total: Int, progress: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp)
            .semantics { testTag = "supplements_summary" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$taken", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.size(6.dp))
            Text(
                "of $total taken",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        StatBar(progress = progress, color = FuelledColors.Primary)
    }
}

@Composable
private fun TimingGroup(group: SupplementGroup, onToggleTaken: (id: String, taken: Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            group.timing.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        group.items.forEach { supp ->
            SupplementRow(supp = supp, onToggle = { onToggleTaken(supp.id, !supp.taken) })
        }
    }
}

@Composable
private fun SupplementRow(supp: Supplement, onToggle: () -> Unit) {
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
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(48.dp).semantics { testTag = "supplements_take_${supp.id}" },
        ) {
            val ring = if (supp.taken) FuelledColors.Primary else MaterialTheme.colorScheme.surfaceContainerHigh
            val tick = if (supp.taken) FuelledColors.OnPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(Modifier.size(28.dp).clip(CircleShape).background(ring), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = if (supp.taken) "Taken" else "Mark taken",
                    tint = tick,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
