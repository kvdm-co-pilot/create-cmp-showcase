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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
 * (the repository/DAO) and stops there. What the screen renders is whatever Room then emits
 * (SUPP-03) — the toggle never mutates a local list, and never re-reads either: the write IS
 * the emission.
 */
class SupplementsViewModel(
    private val getStack: GetSupplementStackUseCase,
    private val setTaken: SetSupplementTakenUseCase,
) : ViewModel() {

    /**
     * Observed, not loaded. Room re-emits on every `setTaken`, so the summary and the progress
     * follow the write with no re-read — and the same emission moves Today's supplement bucket
     * row, which a local re-read could never have done.
     */
    val state: StateFlow<ContentUiState<SupplementStackUi>> =
        getStack()
            .map { it.toUiState() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContentUiState.Loading)

    /**
     * Persist [taken] for the supplement [id], then reflect the PERSISTED state: on success
     * re-read the stack (no Loading flash — a toggle is not a fresh load); on failure surface
     * the mapped error. The re-read is what makes the summary and progress track the write.
     */
    fun onToggleTaken(id: String, taken: Boolean) {
        viewModelScope.launch {
            if (setTaken(id, taken) is AppResult.Failure) _writeError.value = true
        }
    }

    /** A failed WRITE is its own transient fact — the read stream would overwrite it. */
    private val _writeError = MutableStateFlow(false)
    val writeFailed: StateFlow<Boolean> = _writeError.asStateFlow()

    fun clearWriteError() { _writeError.value = false }

    private fun AppResult<List<Supplement>>.toUiState(): ContentUiState<SupplementStackUi> = when (this) {
        is AppResult.Success -> value.toStackUi()
        is AppResult.Failure -> ContentUiState.Error(error.toUserMessage())
    }

    /** Group the ordered stack by timing (first-seen order preserved) and total the taken count. */
    private fun List<Supplement>.toStackUi(): ContentUiState<SupplementStackUi> {
        if (isEmpty()) return ContentUiState.Empty
        // SET-06: the group's NAME is the timing's display label — one fact, so the
        // bucket a supplement lands in and the heading above it cannot disagree.
        val groups = groupBy { it.timing }.map { (timing, items) -> SupplementGroup(timing.label, items) }
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
