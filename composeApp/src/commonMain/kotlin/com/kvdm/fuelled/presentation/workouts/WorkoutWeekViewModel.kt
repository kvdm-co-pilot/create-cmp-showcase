package com.kvdm.fuelled.presentation.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR
import com.kvdm.fuelled.core.time.RealTimeSignal
import com.kvdm.fuelled.core.time.TimeSignal
import com.kvdm.fuelled.core.time.days
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.WorkoutDay
import com.kvdm.fuelled.domain.repository.WorkoutRepository
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.presentation.components.ContentUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

/**
 * The Training tab's ViewModel (NAV-06) — the training week as seven dated days.
 *
 * The window is Monday..Sunday of the CURRENT logical week, re-derived from the same
 * [TimeSignal] every other surface anchors on. Not "the last seven days" (which is what
 * Progress's strip covers, WORK-05): Progress looks BACK over a rolling window because it is
 * the retrospective surface, while this tab shows the week you are IN, because it is the plan.
 * Two different questions that happen to both span seven days — deriving them from one signal
 * is what stops them disagreeing at the 04:00 boundary.
 *
 * No `try`/`catch` (ARCH-07): failures arrive as typed [AppResult.Failure] and are folded into
 * the shared [ContentUiState] machine, exactly as the exemplar does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutWeekViewModel(
    private val workouts: WorkoutRepository,
    private val time: TimeSignal = RealTimeSignal(),
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val dayStartHour: Int = DEFAULT_DAY_START_HOUR,
) : ViewModel() {

    val state: StateFlow<ContentUiState<WorkoutWeekUi>> =
        time.days(dayStartHour, zone).flatMapLatest { today ->
            val monday = today.startOfWeek()
            workouts.observeRange(monday, monday.plus(6, DateTimeUnit.DAY))
                .map { result -> result.toUiState(today) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContentUiState.Loading)

    /**
     * WORK-04: mark the CURRENT logical day's session done, or undo it.
     *
     * Deliberately not "mark any day done": the repository's [WorkoutRepository.setDone] writes
     * the current logical day, and a week view that could retro-tick Tuesday would be inventing
     * a fact nobody observed. Ticking here is the same act as ticking Today's card, and lands
     * in the same row — TODAY-13's discipline, applied to training.
     */
    fun onToggleTodayDone(done: Boolean) {
        viewModelScope.launch { workouts.setDone(done) }
    }

    private fun AppResult<List<WorkoutDay>>.toUiState(
        today: LocalDate,
    ): ContentUiState<WorkoutWeekUi> = when (this) {
        is AppResult.Success -> ContentUiState.Content(WorkoutWeekUi(days = value, today = today))
        is AppResult.Failure -> ContentUiState.Error(error.message())
    }

    private fun DomainError.message(): String = when (this) {
        is DomainError.Network -> "Training week unavailable — no connection."
        is DomainError.NotFound -> "No training week yet."
        else -> "Could not load the training week."
    }
}

/** Monday of [this] date's week — the tab's window start. */
internal fun LocalDate.startOfWeek(): LocalDate =
    plus(-(dayOfWeek.ordinal), DateTimeUnit.DAY)

/**
 * The Training tab's Content payload: seven dated days plus the logical day, so the screen can
 * mark which row is today without reading a clock of its own (ARCH-13).
 */
data class WorkoutWeekUi(
    val days: List<WorkoutDay>,
    val today: LocalDate,
) {
    /** Sessions kept of sessions planned, over this week — the header's summary. */
    val planned: Int get() = days.count { it.plan.isTraining }
    val kept: Int get() = days.count { it.plan.isTraining && it.done }
    /** The current logical day's row, when it is in the window — the only tickable one. */
    val todayRow: WorkoutDay? get() = days.firstOrNull { it.date == today }
}
