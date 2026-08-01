package com.kvdm.fuelled.presentation.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.PlanDay
import com.kvdm.fuelled.domain.model.TodayModel
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.domain.usecase.ArmMealRemindersUseCase
import com.kvdm.fuelled.domain.model.DeletedEntry
import com.kvdm.fuelled.domain.usecase.DeleteLogEntryUseCase
import com.kvdm.fuelled.domain.usecase.RestoreLogEntryUseCase
import com.kvdm.fuelled.domain.usecase.SetEntryServingsUseCase
import com.kvdm.fuelled.domain.usecase.GetPlanDayUseCase
import com.kvdm.fuelled.domain.usecase.GetSupplementStackUseCase
import com.kvdm.fuelled.domain.usecase.GetTodaySummaryUseCase
import com.kvdm.fuelled.domain.usecase.SetSlotDoneUseCase
import com.kvdm.fuelled.domain.usecase.SetWaterDoneUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.domain.model.Supplement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * The Today dashboard ViewModel — the derived "now" across three features (brief decision 13).
 *
 * **TODAY-13 is the load-bearing property here, and it is structural.** Ticking a container from
 * Today calls [SetSlotDoneUseCase] and [SetWaterDoneUseCase] — the plan screen's own use cases,
 * not a Today-shaped copy — and then re-reads [GetPlanDayUseCase], the plan screen's own read.
 * There is no second write path to keep in step, because there is no second write path.
 *
 * No `try`/`catch` (ARCH-07). Failures arrive as typed [AppResult.Failure] and become copy here.
 *
 * A day with nothing logged is still [ContentUiState.Content]: the ring reads the full target as
 * remaining and the focused container carries its own add control (TODAY-04, PLAN-04). The
 * dataless [ContentUiState.Empty] arm would drop that target, so Today does not use it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val getTodaySummary: GetTodaySummaryUseCase,
    private val getPlanDay: GetPlanDayUseCase,
    private val getSupplementStack: GetSupplementStackUseCase,
    private val setSlotDone: SetSlotDoneUseCase,
    private val setWaterDone: SetWaterDoneUseCase,
    private val armReminders: ArmMealRemindersUseCase,
    private val deleteLogEntry: DeleteLogEntryUseCase,
    private val setEntryServings: SetEntryServingsUseCase,
    private val restoreLogEntry: RestoreLogEntryUseCase,
) : ViewModel() {

    /** ENTRY-02: the last removal, held for the undo bar (same shape as the plan screen's). */
    private val _lastDeleted = MutableStateFlow<DeletedEntry?>(null)
    val lastDeleted: StateFlow<DeletedEntry?> = _lastDeleted.asStateFlow()

    fun clearUndo() { _lastDeleted.value = null }

    /**
     * The logical day Today speaks for, as a STREAM — it advances at 04:00 and on every wake,
     * so the dashboard left open overnight speaks for the new day (MEAL-02).
     */
    val todayStream: StateFlow<LocalDate> =
        getPlanDay.currentLogicalDay()
            .stateIn(viewModelScope, SharingStarted.Eagerly, getPlanDay.currentLogicalDayNow())

    /** The day a WRITE targets right now — a one-shot at the moment of the tap, never held. */
    private val today: LocalDate get() = todayStream.value

    /**
     * The dashboard, as a stream of streams.
     *
     * `flatMapLatest` on the day means the boundary tears down yesterday's collection and
     * starts today's. Inside, the three sources are combined and every one of them re-emits on
     * its own: the meal log on any write, the plan on any write OR on the minute (focus, LATE,
     * MISSED), the supplement stack on any tap. Nothing here reloads and nothing polls — this
     * is why adding a food in the tray and pressing back shows the meal, and why the LATE tag
     * appears at 30 minutes past without touching the screen.
     *
     * `WhileSubscribed(5_000)` keeps the upstream alive across a rotation or a quick tab
     * switch, and stops all of it — ticker included — when Today is genuinely gone.
     */
    val state: StateFlow<ContentUiState<TodayHighlights>> =
        todayStream
            .flatMapLatest { day ->
                combine(
                    getTodaySummary(),
                    getPlanDay(day),
                    getSupplementStack(),
                ) { summary, plan, stack -> combineHighlights(summary, plan, stack) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContentUiState.Loading)

    init {
        // PLAN-07: app open is one of the moments the clause names for re-arming, and Today is
        // the screen the app opens on.
        viewModelScope.launch { armReminders() }
    }

    /**
     * Tick the focused container from Today (TODAY-09). The same write the plan screen makes,
     * and the reload re-derives focus — so the container on screen becomes the NEXT one without
     * leaving the dashboard, which is the self-advancing behavior the clause describes.
     */
    fun setSlotDone(slot: MealSlot, done: Boolean) {
        viewModelScope.launch {
            // No reload after the write: Room's stream carries it back into `state` on its own,
            // which is also what makes the same tick land on the plan screen.
            when (setSlotDone(today, slot, done)) {
                is AppResult.Failure -> _writeError.value = true
                is AppResult.Success -> armReminders(doneSlots())
            }
        }
    }

    /** Tick the next water from Today (TODAY-10) — identical to ticking it on the plan screen. */
    fun setWaterDone(index: Int, done: Boolean) {
        viewModelScope.launch {
            if (setWaterDone(today, index, done) is AppResult.Failure) _writeError.value = true
        }
    }

    /**
     * Remove an entry from the focused container (UX-02) — the same one delete path the plan
     * screen writes through (TODAY-13's discipline: never a second write path). The observed
     * summary and plan re-derive without the row (RS-01).
     */
    fun deleteEntry(id: String) {
        viewModelScope.launch {
            when (val result = deleteLogEntry(id)) {
                is AppResult.Failure -> _writeError.value = true
                is AppResult.Success -> _lastDeleted.value = result.value
            }
        }
    }

    /** ENTRY-02: restore the last removed entry — the same one write path as the plan's. */
    fun undoDelete() {
        val entry = _lastDeleted.value ?: return
        viewModelScope.launch {
            if (restoreLogEntry(entry) is AppResult.Failure) _writeError.value = true else _lastDeleted.value = null
        }
    }

    /** ENTRY-01: step the focused container's entry servings in place. */
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
     * A failed WRITE is surfaced separately from the read stream. Overwriting `state` with an
     * error used to throw the whole dashboard away on a failed tick — and, now that state is
     * observed, the next emission would silently overwrite the error a moment later. A write
     * failure is its own transient fact.
     */
    private val _writeError = MutableStateFlow(false)
    val writeFailed: StateFlow<Boolean> = _writeError.asStateFlow()

    fun clearWriteError() { _writeError.value = false }

    /**
     * One read across the three sources.
     *
     * The meal summary and the plan day are both REQUIRED — the ring without the focus, or the
     * focus without the ring, is a half-rendered dashboard, so either failing is an error.
     * Supplements are OPTIONAL: they are one highlight row among several, and losing the whole
     * dashboard because a supplement query failed is the worse trade.
     */
    private fun combineHighlights(
        summary: AppResult<TodayModel>,
        plan: AppResult<PlanDay>,
        stack: AppResult<List<Supplement>>,
    ): ContentUiState<TodayHighlights> {
        if (summary is AppResult.Failure) return ContentUiState.Error(summary.error.toUserMessage())
        if (plan is AppResult.Failure) return ContentUiState.Error(plan.error.toUserMessage())

        return ContentUiState.Content(
            TodayHighlights(
                today = (summary as AppResult.Success<TodayModel>).value,
                plan = (plan as AppResult.Success<PlanDay>).value,
                supplements = (stack as? AppResult.Success)?.value?.currentBucket(),
            ),
        )
    }

    private fun doneSlots(): Set<MealSlot> =
        (state.value as? ContentUiState.Content)?.data
            ?.plan?.slots.orEmpty()
            .filter { it.done }
            .map { it.slot }
            .toSet()
}

/**
 * Presentation owns user-facing copy: error KINDS become strings here, next to the screen that
 * shows them. A raw `Throwable.message` never reaches the UI — the domain carries no display text.
 */
internal fun DomainError.toUserMessage(): String = when (this) {
    DomainError.Network -> "Can't reach your log right now. Check your connection and try again."
    DomainError.NotFound -> "We couldn't find today's summary."
    is DomainError.Unexpected -> "Something went wrong. Please try again."
}
