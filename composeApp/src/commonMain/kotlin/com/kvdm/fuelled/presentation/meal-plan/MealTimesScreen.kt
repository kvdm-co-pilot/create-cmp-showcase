package com.kvdm.fuelled.presentation.mealplan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.presentation.components.AppHeader
import com.kvdm.fuelled.presentation.components.AppTextButton
import com.kvdm.fuelled.presentation.components.ScreenColumn
import com.kvdm.fuelled.presentation.theme.FuelledColors
import com.kvdm.fuelled.presentation.theme.FuelledTokens

// ── Meal times: the set-once alarm sheet (DESIGN DRAFT — feature-design:meal-plan) ───────
// Decision 4 of the brief: every slot has a time, set once and never asked again unless the
// user comes HERE. Water reminders are derived (decision 5), so this screen deliberately has
// no water rows — the note at the bottom says why, in the app's own words.

data class MealTimeUi(val key: String, val label: String, val time: String)

/**
 * What the sheet says about delivery (PLAN-07). Absent when reminders will be delivered — a
 * working feature does not need a status line. Present when the platform will drop them, which
 * is the plain statement the clause requires instead of six alarm times that will never fire.
 * [offersSettings] adds NOTIF-03's tap-through to the system's notification settings — the one
 * sanctioned second chance after a denial, because the app itself never re-prompts (NOTIF-01).
 */
data class MealTimesNotice(val message: String, val offersSettings: Boolean = false)

val sampleMealTimes = listOf(
    MealTimeUi("breakfast", "Breakfast", "07:00"),
    MealTimeUi("morning_snack", "Snack", "09:30"),
    MealTimeUi("lunch", "Lunch", "12:00"),
    MealTimeUi("afternoon_snack", "Snack", "14:30"),
    MealTimeUi("dinner", "Dinner", "17:00"),
    MealTimeUi("evening_snack", "Snack", "19:30"),
)

/**
 * The meal-times editor — DESIGN DRAFT, stateless and stub-driven. One row per slot, one
 * Change affordance per row; the reminder each time drives is implied by the row itself.
 */
@Composable
fun MealTimesScreen(
    times: List<MealTimeUi> = sampleMealTimes,
    notice: MealTimesNotice? = null,
    onBack: () -> Unit = {},
    onChange: (String) -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
) {
    ScreenColumn(screenTag = "meal_times") {
        AppHeader(title = "Meal times", screenTag = "meal_times", onBack = onBack)
        // PLAN-07: when the platform will not deliver, the sheet SAYS SO — above the rows, so
        // it is read before six alarm times imply six working alarms.
        notice?.let {
            Text(
                text = it.message,
                style = MaterialTheme.typography.bodyMedium,
                color = FuelledColors.Warning,
                modifier = Modifier.semantics { testTag = "meal_times_notice" },
            )
            // NOTIF-03: after a denial the app never re-prompts, so the way back on is the
            // system's own switch — offered here, on the surface that just said reminders
            // are off, not buried where the denial is forgotten.
            if (it.offersSettings) {
                AppTextButton(
                    text = "Open notification settings",
                    onClick = onOpenNotificationSettings,
                    modifier = Modifier.semantics { testTag = "meal_times_notice_action" },
                )
            }
            Spacer(Modifier.height(FuelledTokens.GapCard))
        }
        Column(verticalArrangement = Arrangement.spacedBy(FuelledTokens.GapCard)) {
            times.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(FuelledTokens.RadiusCard))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                        .semantics { testTag = "meal_times_${row.key}" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    Text(row.time, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    AppTextButton(
                        text = "Change",
                        onClick = { onChange(row.key) },
                        modifier = Modifier.semantics { testTag = "meal_times_change_${row.key}" },
                    )
                }
            }
        }
        Spacer(Modifier.height(FuelledTokens.GapCard))
        // PLAN-07: the sheet states the prep lead — the reminder arriving half an hour
        // "early" must read as designed, not broken.
        Text(
            text = "Meal reminders arrive 30 minutes before each meal — time to prep.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { testTag = "meal_times_prep_note" },
        )
        Spacer(Modifier.height(FuelledTokens.GapCard))
        Text(
            text = "Water reminders follow your meal times — one 500 ml reminder at the midpoint " +
                "between meals. Change a meal time and its water reminders move with it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { testTag = "meal_times_water_note" },
        )
    }
}
