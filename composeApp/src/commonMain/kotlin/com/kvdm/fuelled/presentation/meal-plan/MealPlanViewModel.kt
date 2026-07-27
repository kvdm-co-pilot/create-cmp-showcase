package com.kvdm.fuelled.presentation.mealplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.PlanDay
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.domain.usecase.ArmMealRemindersUseCase
import com.kvdm.fuelled.domain.usecase.CopyDayForwardUseCase
import com.kvdm.fuelled.domain.usecase.GetPlanDayUseCase
import com.kvdm.fuelled.domain.usecase.SetSlotDoneUseCase
import com.kvdm.fuelled.domain.usecase.SetWaterDoneUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.presentation.today.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** How many days after today the strip offers, plus the one leading yesterday (PLAN-11). */
const val PLAN_DAYS_AHEAD: Int = 7

/**
 * The plan screen's ViewModel — one selected day of the structured week (specs/meal-plan.spec.md).
 *
 * Every mutation re-reads the day rather than patching the state in place. That is deliberate
 * and it is the point of the whole feature's shape: ticking lunch changes the day's totals, the
 * focused container, whether the next slot now reads late, and the veg count — all derived. A
 * hand-patched copy would have to reproduce those derivations in the presentation layer, which
 * is exactly where they would eventually disagree with the domain's.
 *
 * No `try`/`catch` (ARCH-07): failures arrive as typed [AppResult.Failure] and become copy here.
 */
class MealPlanViewModel(
    private val getPlanDay: GetPlanDayUseCase,
    private val setSlotDone: SetSlotDoneUseCase,
    private val setWaterDone: SetWaterDoneUseCase,
    private val copyDayForward: CopyDayForwardUseCase,
    private val armReminders: ArmMealRemindersUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ContentUiState<PlanDay>>(ContentUiState.Loading)
    val state: StateFlow<ContentUiState<PlanDay>> = _state.asStateFlow()

    /** The current logical day — the strip's anchor and its initial selection (PLAN-11). */
    val today: LocalDate = getPlanDay.currentLogicalDay()

    /**
     * The nine days the strip offers: one leading yesterday for back-filling a missed meal,
     * today, and the next seven (PLAN-11). This is the feature's ONLY date selector.
     */
    val stripDays: List<LocalDate> =
        (-1..PLAN_DAYS_AHEAD).map { today.plus(it, DateTimeUnit.DAY) }

    private val _selectedDate = MutableStateFlow(today)
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    init {
        load(today)
        // PLAN-07: re-arm on app open. Alarms do not survive a reboot and the OS can revoke
        // permissions at any time, so the honest moment to reconcile what is armed with what
        // is stored is every time the user actually opens the feature.
        viewModelScope.launch { armReminders() }
    }

    fun select(date: LocalDate) {
        _selectedDate.value = date
        load(date)
    }

    fun load(date: LocalDate = _selectedDate.value) {
        viewModelScope.launch {
            _state.value = ContentUiState.Loading
            _state.value = getPlanDay(date).toUiState()
        }
    }

    /**
     * Tick or un-tick a meal container (PLAN-13/PLAN-14).
     *
     * Re-arming afterwards is what cancels that slot's reminder for the day (PLAN-07) — a meal
     * already eaten is never announced — and it reads the day's done set back out of the reload
     * rather than trusting the tap, so an un-tick correctly restores the reminder.
     */
    fun setDone(slot: MealSlot, done: Boolean) {
        viewModelScope.launch {
            when (val result = setSlotDone(_selectedDate.value, slot, done)) {
                is AppResult.Failure -> _state.value = ContentUiState.Error(result.error.toUserMessage())
                is AppResult.Success -> {
                    reload()
                    // Only today's ticks affect today's reminders. Ticking a container on a
                    // day being planned ahead says nothing about what should ring tonight.
                    if (_selectedDate.value == today) armReminders(currentDoneSlots())
                }
            }
        }
    }

    fun setWater(index: Int, done: Boolean) {
        viewModelScope.launch {
            when (val result = setWaterDone(_selectedDate.value, index, done)) {
                is AppResult.Failure -> _state.value = ContentUiState.Error(result.error.toUserMessage())
                is AppResult.Success -> reload()
            }
        }
    }

    /**
     * Copy the selected day's plan onto the days that follow it (PLAN-20) — how a prepped week
     * actually gets built. The view does not move: the source day stays on screen, unchanged,
     * which is the confirmation that nothing was taken away from it.
     */
    fun copyForward(days: Int) {
        viewModelScope.launch {
            when (val result = copyDayForward(_selectedDate.value, days)) {
                is AppResult.Failure -> _state.value = ContentUiState.Error(result.error.toUserMessage())
                is AppResult.Success -> reload()
            }
        }
    }

    /** Re-read without flashing Loading — a tick is not a page load, and blanking the day for a
     *  frame would make the fastest interaction in the app look like the slowest. */
    private suspend fun reload() {
        _state.value = getPlanDay(_selectedDate.value).toUiState()
    }

    private fun currentDoneSlots(): Set<MealSlot> =
        (_state.value as? ContentUiState.Content)?.data
            ?.slots.orEmpty()
            .filter { it.done }
            .map { it.slot }
            .toSet()

    private fun AppResult<PlanDay>.toUiState(): ContentUiState<PlanDay> = when (this) {
        is AppResult.Success -> ContentUiState.Content(value)
        is AppResult.Failure -> ContentUiState.Error(error.toUserMessage())
    }
}
