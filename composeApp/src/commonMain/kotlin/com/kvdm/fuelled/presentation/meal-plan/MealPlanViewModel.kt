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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
@OptIn(ExperimentalCoroutinesApi::class)
class MealPlanViewModel(
    initialDate: LocalDate,
    private val getPlanDay: GetPlanDayUseCase,
    private val setSlotDone: SetSlotDoneUseCase,
    private val setWaterDone: SetWaterDoneUseCase,
    private val copyDayForward: CopyDayForwardUseCase,
    private val armReminders: ArmMealRemindersUseCase,
) : ViewModel() {

    /**
     * The current logical day — the strip's anchor (PLAN-11) — as a STREAM. Held as a value it
     * anchored the strip to the day the screen was opened on, so a plan left open across 04:00
     * offered yesterday's nine days and called yesterday "today".
     */
    val todayStream: StateFlow<LocalDate> =
        getPlanDay.currentLogicalDay()
            .stateIn(viewModelScope, SharingStarted.Eagerly, getPlanDay.currentLogicalDayNow())

    private val today: LocalDate get() = todayStream.value

    /**
     * The nine days the strip offers: one leading yesterday for back-filling a missed meal,
     * today, and the next seven (PLAN-11). This is the feature's ONLY date selector — and it
     * re-derives when the day rolls, so the window moves with the calendar.
     */
    val stripDays: StateFlow<List<LocalDate>> =
        todayStream
            .map { anchor -> (-1..PLAN_DAYS_AHEAD).map { anchor.plus(it, DateTimeUnit.DAY) } }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                (-1..PLAN_DAYS_AHEAD).map { getPlanDay.currentLogicalDayNow().plus(it, DateTimeUnit.DAY) },
            )

    /**
     * The day on screen. Seeded from the route's date ONCE, at construction, and owned by this
     * ViewModel from then on (PLAN-24).
     *
     * The seed is a constructor parameter rather than something the route re-applies, because
     * the route re-enters composition every time the tray is dismissed — and a re-applied nav
     * argument silently threw the strip's selection away, so planning Thursday meant re-picking
     * Thursday after every single food (observed on-device, 2026-07-29). This ViewModel is
     * scoped to the nav entry, so it outlives the trip to the tray and the selection with it;
     * arriving on a genuinely new `plan/{date}` entry builds a new one, correctly aimed.
     */
    private val _selectedDate = MutableStateFlow(initialDate)
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /**
     * The selected day, observed. Switching days re-collects; the day's own writes and the
     * minute tick re-emit within it. Nothing here reloads.
     */
    val state: StateFlow<ContentUiState<PlanDay>> =
        _selectedDate
            .flatMapLatest { date -> getPlanDay(date).map { it.toUiState() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContentUiState.Loading)

    init {
        // PLAN-07: re-arm on app open. Alarms do not survive a reboot and the OS can revoke
        // permissions at any time, so the honest moment to reconcile what is armed with what
        // is stored is every time the user actually opens the feature.
        viewModelScope.launch { armReminders() }
    }

    fun select(date: LocalDate) {
        _selectedDate.value = date
    }

    /**
     * Tick or un-tick a meal container (PLAN-13/PLAN-14).
     *
     * Re-arming afterwards is what cancels that slot's reminder for the day (PLAN-07) — a meal
     * already eaten is never announced — and it reads the day's done set back out of the
     * OBSERVED state rather than trusting the tap, so an un-tick correctly restores the
     * reminder. The state has already carried the write back by then: Room emits before the
     * write call returns.
     */
    fun setDone(slot: MealSlot, done: Boolean) {
        viewModelScope.launch {
            when (setSlotDone(_selectedDate.value, slot, done)) {
                is AppResult.Failure -> _writeError.value = true
                is AppResult.Success ->
                    // Only today's ticks affect today's reminders. Ticking a container on a
                    // day being planned ahead says nothing about what should ring tonight.
                    if (_selectedDate.value == today) armReminders(currentDoneSlots())
            }
        }
    }

    fun setWater(index: Int, done: Boolean) {
        viewModelScope.launch {
            if (setWaterDone(_selectedDate.value, index, done) is AppResult.Failure) _writeError.value = true
        }
    }

    /**
     * Copy the selected day's plan onto the days that follow it (PLAN-20) — how a prepped week
     * actually gets built. The view does not move: the source day stays on screen, unchanged,
     * which is the confirmation that nothing was taken away from it.
     */
    fun copyForward(days: Int) {
        viewModelScope.launch {
            if (copyDayForward(_selectedDate.value, days) is AppResult.Failure) _writeError.value = true
        }
    }

    /**
     * A failed WRITE is its own transient fact, kept OFF the read stream: putting it in `state`
     * used to throw the whole day away on one failed tick, and now that state is observed the
     * next emission would silently swallow it a moment later.
     */
    private val _writeError = MutableStateFlow(false)
    val writeFailed: StateFlow<Boolean> = _writeError.asStateFlow()

    fun clearWriteError() { _writeError.value = false }


    private fun currentDoneSlots(): Set<MealSlot> =
        (state.value as? ContentUiState.Content)?.data
            ?.slots.orEmpty()
            .filter { it.done }
            .map { it.slot }
            .toSet()

    private fun AppResult<PlanDay>.toUiState(): ContentUiState<PlanDay> = when (this) {
        is AppResult.Success -> ContentUiState.Content(value)
        is AppResult.Failure -> ContentUiState.Error(error.toUserMessage())
    }
}
