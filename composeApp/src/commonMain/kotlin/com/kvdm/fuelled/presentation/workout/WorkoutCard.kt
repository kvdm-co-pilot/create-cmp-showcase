package com.kvdm.fuelled.presentation.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.domain.model.WorkoutDay
import com.kvdm.fuelled.presentation.components.TickButton
import com.kvdm.fuelled.presentation.theme.FuelledColors

// ── Today's training card (WORK-03/WORK-04) ──────────────────────────────────────────────
// The doing half of the training pillar. Deliberately a COMPONENT hosted by Today rather than
// a screen of its own: training belongs to the day, and a tab you have to visit to tick one
// box is a tab nobody visits. The seeing half lives on Progress (WORK-05), the shaping half in
// Settings (WORK-07) — three surfaces that already exist, no fourth.

/**
 * The card, or nothing at all on a rest day (WORK-03).
 *
 * Rendering nothing is the point. Rest is the plan working — a card saying "no training today"
 * is a zero to stare at, and HIST-05 already established that this app does not draw empty
 * axes for days with nothing in them.
 */
@Composable
fun TodayWorkoutCard(
    day: WorkoutDay,
    onToggleDone: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val label = day.plan.label ?: return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .semantics { testTag = "today_workout" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { testTag = "today_workout_label" },
            )
            Text(
                day.caption(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The registry's tick (MOTION-09) — the same gesture as the meal, water and supplement
        // ticks means the same thing, and a second vocabulary for "I did the thing" would be
        // one to learn for no reason.
        TickButton(
            icon = Icons.Filled.Check,
            checked = day.done,
            contentDescription = if (day.done) "Done" else "Mark done",
            onClick = { onToggleDone(!day.done) },
            checkedTint = FuelledColors.Success,
            uncheckedTint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { testTag = "today_workout_done" },
        )
    }
}

/** "Today's training · reminds 18:00", or "· done" once it is (WORK-03). */
private fun WorkoutDay.caption(): String = buildString {
    append("Today's training")
    when {
        done -> append("  ·  done")
        plan.remindAt != null && plan.leads.isNotEmpty() -> {
            val at = plan.remindAt
            append("  ·  reminds ${at.hour.pad()}:${at.minute.pad()}")
        }
    }
}

private fun Int.pad(): String = toString().padStart(2, '0')
