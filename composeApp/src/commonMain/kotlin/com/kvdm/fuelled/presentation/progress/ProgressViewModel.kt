package com.kvdm.fuelled.presentation.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.core.time.systemZone
import com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR
import com.kvdm.fuelled.core.time.RealTimeSignal
import com.kvdm.fuelled.core.time.TimeSignal
import com.kvdm.fuelled.core.time.days
import com.kvdm.fuelled.domain.model.AppState
import com.kvdm.fuelled.domain.model.History
import com.kvdm.fuelled.domain.model.WeightLog
import com.kvdm.fuelled.domain.model.WorkoutDay
import com.kvdm.fuelled.domain.repository.WorkoutRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import com.kvdm.fuelled.domain.usecase.GetHistoryUseCase
import com.kvdm.fuelled.domain.usecase.ObserveAppStateUseCase
import com.kvdm.fuelled.domain.usecase.ObserveWeightLogUseCase
import com.kvdm.fuelled.domain.usecase.RecordWeightUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.presentation.today.toUserMessage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Progress surface's ViewModel (JRN-01, HIST-01) — a pure projection of three observed
 * streams: the history, the weigh-in log, and the app's settings (for the display unit).
 *
 * No retry control anywhere (RS-01, TODAY-05's reasoning): the state is observed, so a
 * transient failure is replaced by the source's next emission, not by a button. No
 * `try`/`catch` (ARCH-07): failures arrive typed and become copy here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(
    getHistory: GetHistoryUseCase,
    observeWeight: ObserveWeightLogUseCase,
    observeAppState: ObserveAppStateUseCase,
    private val recordWeight: RecordWeightUseCase,
    workouts: WorkoutRepository,
    time: TimeSignal = RealTimeSignal(),
    zone: TimeZone = systemZone(),
    dayStartHour: Int = DEFAULT_DAY_START_HOUR,
) : ViewModel() {

    /**
     * WORK-05: the same seven days the history covers, as training.
     *
     * The window is derived from the SAME logical day the history use case anchors on, so the
     * strip and the day cards are describing one week — an independently-computed window would
     * be a second definition of "this week" waiting to disagree at 04:00.
     */
    private val trainingWeek = time.days(dayStartHour, zone).flatMapLatest { today ->
        workouts.observeRange(today.minus(WEEK_DAYS - 1, DateTimeUnit.DAY), today)
    }

    val state: StateFlow<ContentUiState<ProgressUi>> =
        combine(
            getHistory(),
            observeWeight(),
            observeAppState(),
            trainingWeek,
        ) { history, weight, appState, training -> fold(history, weight, appState, training) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContentUiState.Loading)

    /**
     * HIST-06: record today's weight, in KILOGRAMS — the screen converts from the displayed
     * unit before calling, so the store only ever sees one unit (SET-02).
     */
    fun onWeightRecorded(kg: Double) {
        viewModelScope.launch { recordWeight(kg) }
    }

    private fun fold(
        history: AppResult<History>,
        weight: AppResult<WeightLog>,
        appState: AppResult<AppState>,
        training: AppResult<List<WorkoutDay>>,
    ): ContentUiState<ProgressUi> = when (history) {
        // The HISTORY is the surface: without it there is nothing to render, so its failure
        // is the screen's failure.
        is AppResult.Failure -> ContentUiState.Error(history.error.toUserMessage())
        // Weight and settings are NOT. A weigh-in log that fails to load must not take the
        // four-week trend down with it — the section degrades to "none recorded" and the
        // observed stream heals it on the next emission (RS-01). Settings falling back to the
        // default is a unit label, not a reason to hide somebody's month.
        is AppResult.Success -> ContentUiState.Content(
            ProgressUi(
                history = history.value,
                weight = (weight as? AppResult.Success)?.value ?: WeightLog(emptyList()),
                units = (appState as? AppResult.Success)?.value?.settings?.unitSystem
                    ?: ProgressUi().units,
                // WORK-05: optional too. An unreadable training week renders NO training
                // section — never a week of zero sessions, which would read as seven misses.
                training = (training as? AppResult.Success)?.value.orEmpty(),
            ),
        )
    }

    private companion object {
        const val WEEK_DAYS = 7
    }
}
