package com.kvdm.fuelled.presentation.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.TodayModel
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.domain.usecase.GetTodaySummaryUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Today dashboard ViewModel — folds the use case's [AppResult] into the shared
 * [ContentUiState] state machine (mirrors FoodsViewModel).
 *
 * No `try`/`catch` here — ever (ARCH-07). Failures arrive as typed [AppResult.Failure] values;
 * the ViewModel maps [DomainError] KINDS to user-facing copy. A `CancellationException` thrown
 * while suspended simply cancels this coroutine (structured concurrency) — never an error state.
 *
 * A valid day always carries a goal (the calorie ring's target), so a day with no logged
 * entries is still [ContentUiState.Content] — a [TodayModel] whose `meals` is empty and whose
 * `consumedKcal` is 0. The screen renders the empty log affordance (`today_empty`) while the
 * ring keeps reading the full target as remaining (TODAY-04); the dataless
 * [ContentUiState.Empty] arm would drop that target, so Today doesn't use it.
 */
class TodayViewModel(
    private val getTodaySummary: GetTodaySummaryUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ContentUiState<TodayModel>>(ContentUiState.Loading)
    val state: StateFlow<ContentUiState<TodayModel>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ContentUiState.Loading
            _state.value = getTodaySummary().toUiState()
        }
    }

    private fun AppResult<TodayModel>.toUiState(): ContentUiState<TodayModel> = when (this) {
        is AppResult.Success -> ContentUiState.Content(value)
        is AppResult.Failure -> ContentUiState.Error(error.toUserMessage())
    }
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
