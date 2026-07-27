package com.kvdm.fuelled.presentation.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.PlanDay
import com.kvdm.fuelled.domain.model.TodayModel
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.domain.usecase.ArmMealRemindersUseCase
import com.kvdm.fuelled.domain.usecase.GetPlanDayUseCase
import com.kvdm.fuelled.domain.usecase.GetSupplementStackUseCase
import com.kvdm.fuelled.domain.usecase.GetTodaySummaryUseCase
import com.kvdm.fuelled.domain.usecase.SetSlotDoneUseCase
import com.kvdm.fuelled.domain.usecase.SetWaterDoneUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
class TodayViewModel(
    private val getTodaySummary: GetTodaySummaryUseCase,
    private val getPlanDay: GetPlanDayUseCase,
    private val getSupplementStack: GetSupplementStackUseCase,
    private val setSlotDone: SetSlotDoneUseCase,
    private val setWaterDone: SetWaterDoneUseCase,
    private val armReminders: ArmMealRemindersUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ContentUiState<TodayHighlights>>(ContentUiState.Loading)
    val state: StateFlow<ContentUiState<TodayHighlights>> = _state.asStateFlow()

    /** The logical day Today speaks for — re-derived on every read, never stored (MEAL-02). */
    val today: LocalDate get() = getPlanDay.currentLogicalDay()

    init {
        load()
        // PLAN-07: app open is one of the moments the clause names for re-arming, and Today is
        // the screen the app opens on.
        viewModelScope.launch { armReminders() }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ContentUiState.Loading
            _state.value = read()
        }
    }

    /**
     * Tick the focused container from Today (TODAY-09). The same write the plan screen makes,
     * and the reload re-derives focus — so the container on screen becomes the NEXT one without
     * leaving the dashboard, which is the self-advancing behavior the clause describes.
     */
    fun setSlotDone(slot: MealSlot, done: Boolean) {
        viewModelScope.launch {
            when (val result = setSlotDone(today, slot, done)) {
                is AppResult.Failure -> _state.value = ContentUiState.Error(result.error.toUserMessage())
                is AppResult.Success -> {
                    _state.value = read()
                    armReminders(doneSlots())
                }
            }
        }
    }

    /** Tick the next water from Today (TODAY-10) — identical to ticking it on the plan screen. */
    fun setWaterDone(index: Int, done: Boolean) {
        viewModelScope.launch {
            when (val result = setWaterDone(today, index, done)) {
                is AppResult.Failure -> _state.value = ContentUiState.Error(result.error.toUserMessage())
                is AppResult.Success -> _state.value = read()
            }
        }
    }

    /**
     * One read across the three sources.
     *
     * The meal summary and the plan day are both REQUIRED — the ring without the focus, or the
     * focus without the ring, is a half-rendered dashboard, so either failing is an error.
     * Supplements are OPTIONAL: they are one highlight row among several, and losing the whole
     * dashboard because a supplement query failed is the worse trade.
     */
    private suspend fun read(): ContentUiState<TodayHighlights> {
        val summary = getTodaySummary()
        if (summary is AppResult.Failure) return ContentUiState.Error(summary.error.toUserMessage())
        val plan = getPlanDay(today)
        if (plan is AppResult.Failure) return ContentUiState.Error(plan.error.toUserMessage())
        val stack = getSupplementStack()

        return ContentUiState.Content(
            TodayHighlights(
                today = (summary as AppResult.Success<TodayModel>).value,
                plan = (plan as AppResult.Success<PlanDay>).value,
                supplements = (stack as? AppResult.Success)?.value?.currentBucket(),
            ),
        )
    }

    private fun doneSlots(): Set<MealSlot> =
        (_state.value as? ContentUiState.Content)?.data
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
