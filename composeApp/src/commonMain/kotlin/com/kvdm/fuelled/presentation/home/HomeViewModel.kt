package com.kvdm.fuelled.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Item
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.domain.usecase.GetItemsUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.presentation.components.toContentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * No `try`/`catch` here — ever (ARCH-07). Failures arrive as typed [AppResult.Failure]
 * values from the use case; the ViewModel folds over the result and maps [DomainError]
 * KINDS to user-facing copy. A `CancellationException` thrown while suspended simply
 * cancels this coroutine (structured concurrency) — it never becomes an error state.
 *
 * The state machine is the shared [ContentUiState] (`presentation/components`) — the
 * generalization of this feature's pre-generalization per-feature sealed state (which
 * EH-1 landed): same four arms (Loading/Content/Empty/Error), same fold, made generic once
 * so `ContentStateContainer` can own the non-content arms' rendering instead of every
 * screen hand-folding its own copy.
 */
class HomeViewModel(
    private val getItems: GetItemsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ContentUiState<List<Item>>>(ContentUiState.Loading)
    val state: StateFlow<ContentUiState<List<Item>>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ContentUiState.Loading
            _state.value = when (val result = getItems()) {
                is AppResult.Success -> result.value.toContentState()
                is AppResult.Failure -> ContentUiState.Error(result.error.toUserMessage())
            }
        }
    }
}

/**
 * Presentation owns user-facing copy: error KINDS become strings here, next to the screen
 * that shows them. A raw `Throwable.message` never reaches the UI — the domain carries no
 * display text at all.
 */
internal fun DomainError.toUserMessage(): String = when (this) {
    DomainError.Network -> "Can't reach the server. Check your connection and try again."
    DomainError.NotFound -> "That content isn't available."
    is DomainError.Unexpected -> "Something went wrong. Please try again."
}
