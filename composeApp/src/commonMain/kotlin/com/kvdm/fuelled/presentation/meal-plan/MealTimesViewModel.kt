package com.kvdm.fuelled.presentation.mealplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.MealTimes
import com.kvdm.fuelled.domain.model.ReminderMode
import com.kvdm.fuelled.domain.notification.ReminderScheduler
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.domain.usecase.ArmMealRemindersUseCase
import com.kvdm.fuelled.domain.usecase.GetMealTimesUseCase
import com.kvdm.fuelled.domain.usecase.SetMealTimeUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.presentation.today.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime

/**
 * What the times sheet renders: the six times, and the truth about whether reminders will
 * actually be delivered (PLAN-07).
 *
 * [reminderMode] is on the state rather than hidden in the scheduler because the clause requires
 * the sheet to *say* when reminders are off. A screen that could only show times would imply
 * six working alarms on a device that had denied notifications — the exact silent failure the
 * clause names.
 */
data class MealTimesUiState(
    val times: MealTimes,
    val reminderMode: ReminderMode,
)

/**
 * The meal-times sheet's ViewModel (PLAN-05/PLAN-06/PLAN-07).
 *
 * Note there is no water anywhere in here. Water times are midpoints of these six (PLAN-09), so
 * offering a water row to edit would create a second setting that could disagree with the first
 * — the sheet explains the derivation in words instead.
 */
class MealTimesViewModel(
    private val getMealTimes: GetMealTimesUseCase,
    private val setMealTime: SetMealTimeUseCase,
    private val armReminders: ArmMealRemindersUseCase,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow<ContentUiState<MealTimesUiState>>(ContentUiState.Loading)
    val state: StateFlow<ContentUiState<MealTimesUiState>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ContentUiState.Loading
            // Opening the sheet also re-arms (PLAN-07): it is one of the moments the clause
            // names, and it means the mode shown below is the mode that was actually just used
            // rather than a second, independent guess at the platform's answer.
            armReminders()
            _state.value = when (val times = getMealTimes()) {
                is AppResult.Failure -> ContentUiState.Error(times.error.toUserMessage())
                is AppResult.Success -> ContentUiState.Content(
                    MealTimesUiState(times = times.value, reminderMode = scheduler.capability().mode),
                )
            }
        }
    }

    /**
     * Change one slot's time (PLAN-06). The stored value comes back COERCED into the window
     * between its neighbours, and that is what the sheet then shows — so a user who drags dinner
     * before lunch sees where it actually landed rather than a value that silently did not take.
     */
    fun setTime(slot: MealSlot, time: LocalTime) {
        viewModelScope.launch {
            when (val result = setMealTime(slot, time)) {
                is AppResult.Failure -> _state.value = ContentUiState.Error(result.error.toUserMessage())
                is AppResult.Success -> _state.value = ContentUiState.Content(
                    MealTimesUiState(times = result.value, reminderMode = scheduler.capability().mode),
                )
            }
        }
    }
}
