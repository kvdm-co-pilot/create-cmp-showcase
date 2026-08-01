package com.kvdm.fuelled.presentation.mealplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.PlanDay
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.domain.usecase.ArmMealRemindersUseCase
import com.kvdm.fuelled.domain.usecase.CopyDayForwardUseCase
import com.kvdm.fuelled.domain.model.DeletedEntry
import com.kvdm.fuelled.domain.usecase.DeleteLogEntryUseCase
import com.kvdm.fuelled.domain.usecase.RestoreLogEntryUseCase
import com.kvdm.fuelled.domain.usecase.SetEntryServingsUseCase
import com.kvdm.fuelled.domain.usecase.GetPlanDayUseCase
import com.kvdm.fuelled.domain.usecase.SetSlotDoneUseCase
import com.kvdm.fuelled.domain.usecase.SetWaterDoneUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.presentation.today.toUserMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
    private val deleteLogEntry: DeleteLogEntryUseCase,
    private val setEntryServings: SetEntryServingsUseCase,
    private val restoreLogEntry: RestoreLogEntryUseCase,
) : ViewModel() {

    /**
     * ENTRY-02: what the last delete removed, held until it is undone or superseded. It is
     * presentation state, not a read: the day itself already re-derived without the row.
     */
    private val _lastDeleted = MutableStateFlow<DeletedEntry?>(null)
    val lastDeleted: StateFlow<DeletedEntry?> = _lastDeleted.asStateFlow()

    fun clearUndo() { _lastDeleted.value = null }

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
     * The nine days the strip offers: one leading day for back-filling a missed meal, the
     * selected day, and the next seven (PLAN-11).
     *
     * HIST-03: the window follows the SELECTED day, not today. It was today-relative, which
     * was invisible only for as long as nothing could reach a day older than yesterday —
     * `toUi` derives the highlighted chip as `stripDays.indexOf(date).coerceAtLeast(0)`, so a
     * day outside the window silently highlighted the FIRST chip. The moment the Progress
     * surface became a door into any past day (HIST-02), that was a screen confidently
     * showing Sunday's meals under a chip reading "Tue 21". Anchoring on the selection kills
     * it at the source: there is no window the selected day can fall outside of.
     */
    val stripDays: StateFlow<List<LocalDate>> =
        combine(todayStream, _selectedDate) { _, selected -> selected }
            .map { anchor -> (-1..PLAN_DAYS_AHEAD).map { anchor.plus(it, DateTimeUnit.DAY) } }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                (-1..PLAN_DAYS_AHEAD).map { initialDate.plus(it, DateTimeUnit.DAY) },
            )

    /**
     * HIST-04: copy-forward is offered on the current day and future ones only. Pointed
     * backwards it would duplicate a past day's entries over days that have already been
     * lived — overwriting real logged history, silently, from a control whose label promises
     * a planning convenience. There is no confirm dialog because there is no legitimate
     * meaning to confirm.
     */
    val canCopyForward: StateFlow<Boolean> =
        combine(todayStream, _selectedDate) { today, selected -> selected >= today }
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

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
     * Remove one entry from its container (UX-02, MEAL-06's one delete path). No reload and no
     * hand-patched state: the observed day re-derives without the row (RS-01), and a failure
     * goes to the write channel like every other write (RS-04).
     */
    fun deleteEntry(id: String) {
        viewModelScope.launch {
            when (val result = deleteLogEntry(id)) {
                is AppResult.Failure -> _writeError.value = true
                // Holding what was removed is what makes the undo bar honest: it names the
                // food and can put back the exact row, not an approximation of it.
                is AppResult.Success -> _lastDeleted.value = result.value
            }
        }
    }

    /** ENTRY-02: put the last removed entry back exactly, and retire the bar. */
    fun undoDelete() {
        val entry = _lastDeleted.value ?: return
        viewModelScope.launch {
            if (restoreLogEntry(entry) is AppResult.Failure) _writeError.value = true else _lastDeleted.value = null
        }
    }

    /** ENTRY-01: step one entry's servings; at zero it is a removal, undo bar and all. */
    fun setServings(id: String, servings: Int) {
        if (servings < 1) {
            deleteEntry(id)
            return
        }
        viewModelScope.launch {
            if (setEntryServings(id, servings) is AppResult.Failure) _writeError.value = true
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
