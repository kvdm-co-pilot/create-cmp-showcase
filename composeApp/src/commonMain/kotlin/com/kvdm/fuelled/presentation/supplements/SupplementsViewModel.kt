package com.kvdm.fuelled.presentation.supplements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR
import com.kvdm.fuelled.core.time.RealTimeSignal
import com.kvdm.fuelled.core.time.TimeSignal
import com.kvdm.fuelled.core.time.currentDay
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.domain.model.SupplementSchedule
import com.kvdm.fuelled.domain.model.isDueOn
import com.kvdm.fuelled.domain.model.nextDueOnOrAfter
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
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
    /**
     * The clock, injected (never read statically) — due-ness depends on the logical day, so a
     * test that could not fix "today" could not assert on a Monday-and-Thursday schedule at all.
     */
    private val time: TimeSignal = RealTimeSignal(),
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val dayStartHour: Int = DEFAULT_DAY_START_HOUR,
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

    /**
     * Split the ordered stack into what is DUE today and what is merely on the stack, group the
     * due half by timing (first-seen order preserved), and total the taken count (SUPP-09).
     *
     * The split is the whole feature. A Monday-and-Thursday injection sitting in Tuesday's
     * Morning bucket, untaken, is the app claiming a missed dose that was never due — so the
     * denominator counts only what today actually asks for, and the rest is stated as resting
     * with the date it next comes round.
     *
     * The screen is Empty only when the stack itself is empty. A day where NOTHING is due is
     * still Content: the resting list is the answer to "did I miss something?", and an empty
     * state would replace that answer with silence.
     */
    private fun List<Supplement>.toStackUi(): ContentUiState<SupplementStackUi> {
        if (isEmpty()) return ContentUiState.Empty
        val today = time.currentDay(dayStartHour, zone)
        val (due, resting) = partition { it.schedule.isDueOn(today) }
        // SET-06: the group's NAME is the timing's display label — one fact, so the
        // bucket a supplement lands in and the heading above it cannot disagree.
        val groups = due.groupBy { it.timing }.map { (timing, items) -> SupplementGroup(timing.label, items) }
        return ContentUiState.Content(
            SupplementStackUi(
                groups = groups,
                takenCount = due.count { it.taken },
                total = due.size,
                resting = resting.map { supplement ->
                    RestingSupplement(
                        supplement = supplement,
                        // Tomorrow onward: today is already known not to be a due day.
                        nextDue = supplement.schedule.nextDueOnOrAfter(today.plus(1, DateTimeUnit.DAY)),
                    )
                },
                today = today,
            ),
        )
    }
}

/** One timing bucket of the stack (e.g. "Morning"), its supplements in display order. */
data class SupplementGroup(val timing: String, val items: List<Supplement>)

/**
 * A supplement that is NOT due today (SUPP-09), and when it next is.
 *
 * [nextDue] is null when the schedule never comes round again — an [SupplementSchedule.OnDays]
 * with no days selected, which the editor can hold mid-edit. The row says so rather than
 * inventing a date.
 */
data class RestingSupplement(
    val supplement: Supplement,
    val nextDue: LocalDate?,
)

/**
 * The presentation model the Supplements screen renders: today's due stack grouped by timing,
 * the summary the header shows, and what is resting. [progress] is derived, never stored, so it
 * cannot drift from the count/total it is computed from.
 */
data class SupplementStackUi(
    val groups: List<SupplementGroup>,
    val takenCount: Int,
    val total: Int,
    val resting: List<RestingSupplement> = emptyList(),
    val today: LocalDate? = null,
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
