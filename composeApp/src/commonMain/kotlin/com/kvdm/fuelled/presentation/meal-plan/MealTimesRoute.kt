package com.kvdm.fuelled.presentation.mealplan

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.MealTimes
import com.kvdm.fuelled.domain.model.ReminderMode
import com.kvdm.fuelled.presentation.components.BaseScreen
import com.kvdm.fuelled.presentation.components.AppPrimaryButton
import com.kvdm.fuelled.presentation.components.AppTextButton
import com.kvdm.fuelled.presentation.components.ContentStateContainer
import com.kvdm.fuelled.presentation.today.label
import kotlinx.datetime.LocalTime
import org.koin.compose.viewmodel.koinViewModel

/**
 * The times sheet's route (PLAN-05/PLAN-06/PLAN-07).
 *
 * The picker is CONSTRAINED, not validated after the fact: it opens on the slot's current time
 * and any value outside the window between its neighbours is coerced by the domain on write, so
 * the sheet then re-renders showing where the time actually landed. Both halves matter — the
 * clause asks the sheet to offer no out-of-range value, and the domain to guarantee it anyway.
 */
@Composable
fun MealTimesRoute(
    onBack: () -> Unit,
    viewModel: MealTimesViewModel = koinViewModel(),
) {
    // SHELL-05: a destination registered directly on the NavHost owns its insets — tabs inherit
    // theirs from AppShell. The wrapper used to sit at the call site in AppNavHost.kt; harness
    // 0.14 requires it HERE, so a destination added later cannot ship inset-less because whoever
    // registered it forgot to wrap it.
    BaseScreen {
        val state by viewModel.state.collectAsStateWithLifecycle()
        var editing by remember { mutableStateOf<MealSlot?>(null) }

        ContentStateContainer(
            state = state,
            screenTag = "meal_times",
            onRetry = { viewModel.load() },
        ) { data ->
            MealTimesScreen(
                times = data.times.toUi(),
                notice = data.reminderMode.toNotice(),
                onBack = onBack,
                onChange = { key -> editing = mealSlotForKey(key) },
                onOpenNotificationSettings = { viewModel.openNotificationSettings() },
            )

            editing?.let { slot ->
                SlotTimeDialog(
                    slot = slot,
                    current = data.times[slot],
                    range = data.times.validTimeRange(slot),
                    onDismiss = { editing = null },
                    onConfirm = { time ->
                        viewModel.setTime(slot, time)
                        editing = null
                    },
                )
            }
        }
    }
}

/** The six stored times as the sheet's rows (PLAN-05). */
internal fun MealTimes.toUi(): List<MealTimeUi> = inSlotOrder().map { (slot, time) ->
    MealTimeUi(key = slot.uiKey, label = slot.label, time = time.clockLabel())
}

/**
 * PLAN-07's honest statement. Only the modes that mean "this will not behave as the six rows
 * below imply" say anything — an exactly-armed schedule needs no commentary, and a notice on
 * every screen is a notice nobody reads.
 */
internal fun ReminderMode.toNotice(): MealTimesNotice? = when (this) {
    ReminderMode.EXACT -> null
    ReminderMode.WINDOWED_INEXACT -> MealTimesNotice(
        "Reminders are on, but your device may deliver them up to about 15 minutes late. " +
            "Allow exact alarms in system settings to pin them to these times.",
    )
    // NOTIF-03: the OFF statement carries the way back on. The app asked once and will not
    // ask again (NOTIF-01), so this tap-through to the system switch is the second chance.
    ReminderMode.UNAVAILABLE -> MealTimesNotice(
        "Reminders are OFF — notifications are not allowed for Fuelled, so none of these times " +
            "will alert you. Your meal times still drive the plan.",
        offersSettings = true,
    )
}

/**
 * The picker, bounded to the slot's window (PLAN-06). Confirm is disabled outside it rather
 * than silently snapping: a control that moves your value without saying so teaches you not to
 * trust it, and the label states the window so the constraint is legible before you hit it.
 */
// M3's TimePicker is still experimental in Compose Multiplatform. Opted in HERE, on the one
// composable that uses it, rather than module-wide — so the day this API changes, the compiler
// points at exactly this dialog instead of at nothing.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotTimeDialog(
    slot: MealSlot,
    current: LocalTime,
    range: ClosedRange<LocalTime>,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val picker = rememberTimePickerState(
        initialHour = current.hour,
        initialMinute = current.minute,
        is24Hour = true,
    )
    val picked = LocalTime(picker.hour, picker.minute)
    val inRange = picked in range

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("${slot.label} — between ${range.start.clockLabel()} and ${range.endInclusive.clockLabel()}") },
        text = { TimePicker(state = picker) },
        confirmButton = {
            AppPrimaryButton(
                text = "Set",
                onClick = { onConfirm(picked) },
                enabled = inRange,
            )
        },
        dismissButton = { AppTextButton(text = "Cancel", onClick = onDismiss) },
    )
}
