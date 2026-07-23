package com.kvdm.fuelled.presentation.supplements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.domain.usecase.GetSupplementStackUseCase
import com.kvdm.fuelled.domain.usecase.SetSupplementTakenUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Supplements ViewModel — folds the stack's [AppResult] into the shared [ContentUiState]
 * machine (mirrors FoodsViewModel/TodayViewModel), building a [SupplementStackUi] for Content:
 * the stack GROUPED by timing in a stable order, plus the taken-of-total summary and progress.
 *
 * No `try`/`catch` here — ever (ARCH-07). Failures arrive as typed [AppResult.Failure] values;
 * the ViewModel maps [DomainError] KINDS to user-facing copy. A `CancellationException` thrown
 * while suspended simply cancels this coroutine (structured concurrency) — never an error state.
 *
 * Tap-to-take is a VM action: [onToggleTaken] persists through [SetSupplementTakenUseCase]
 * (the repository/DAO), then RE-READS the stack so the state it renders is the state Room
 * persisted (SUPP-03) — the toggle never mutates a local list the screen holds.
 */
class SupplementsViewModel(
    private val getStack: GetSupplementStackUseCase,
    private val setTaken: SetSupplementTakenUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ContentUiState<SupplementStackUi>>(ContentUiState.Loading)
    val state: StateFlow<ContentUiState<SupplementStackUi>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ContentUiState.Loading
            _state.value = getStack().toUiState()
        }
    }

    /**
     * Persist [taken] for the supplement [id], then reflect the PERSISTED state: on success
     * re-read the stack (no Loading flash — a toggle is not a fresh load); on failure surface
     * the mapped error. The re-read is what makes the summary and progress track the write.
     */
    fun onToggleTaken(id: String, taken: Boolean) {
        viewModelScope.launch {
            when (val result = setTaken(id, taken)) {
                is AppResult.Success -> _state.value = getStack().toUiState()
                is AppResult.Failure -> _state.value = ContentUiState.Error(result.error.toUserMessage())
            }
        }
    }

    private fun AppResult<List<Supplement>>.toUiState(): ContentUiState<SupplementStackUi> = when (this) {
        is AppResult.Success -> value.toStackUi()
        is AppResult.Failure -> ContentUiState.Error(error.toUserMessage())
    }

    /** Group the ordered stack by timing (first-seen order preserved) and total the taken count. */
    private fun List<Supplement>.toStackUi(): ContentUiState<SupplementStackUi> {
        if (isEmpty()) return ContentUiState.Empty
        val groups = groupBy { it.timing }.map { (timing, items) -> SupplementGroup(timing, items) }
        return ContentUiState.Content(
            SupplementStackUi(groups = groups, takenCount = count { it.taken }, total = size),
        )
    }
}

/** One timing bucket of the stack (e.g. "Morning"), its supplements in display order. */
data class SupplementGroup(val timing: String, val items: List<Supplement>)

/**
 * The presentation model the Supplements screen renders: the stack grouped by timing plus the
 * summary the header shows. [progress] is derived, never stored, so it cannot drift from the
 * count/total it is computed from.
 */
data class SupplementStackUi(
    val groups: List<SupplementGroup>,
    val takenCount: Int,
    val total: Int,
) {
    val progress: Float get() = if (total <= 0) 0f else takenCount.toFloat() / total
}

/**
 * Presentation owns user-facing copy: error KINDS become strings here, next to the screen that
 * shows them. A raw `Throwable.message` never reaches the UI — the domain carries no display text.
 */
internal fun DomainError.toUserMessage(): String = when (this) {
    DomainError.Network -> "Can't reach your stack right now. Check your connection and try again."
    DomainError.NotFound -> "We couldn't find your supplement stack."
    is DomainError.Unexpected -> "Something went wrong. Please try again."
}
