package com.kvdm.fuelled.presentation.foods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.domain.usecase.GetFoodsUseCase
import com.kvdm.fuelled.domain.usecase.SearchFoodsUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.presentation.components.toContentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Foods catalog ViewModel — the exemplar VM for a searchable, data-backed list.
 *
 * No `try`/`catch` here — ever (ARCH-07). Failures arrive as typed [AppResult.Failure] values
 * from the use cases; the ViewModel folds over the result and maps [DomainError] KINDS to
 * user-facing copy. A `CancellationException` thrown while suspended simply cancels this
 * coroutine (structured concurrency) — it never becomes an error state.
 *
 * Search is VM state: [query] is held here and the filter runs through [SearchFoodsUseCase]
 * (the repository/DAO), never in the composable — the screen only reports keystrokes.
 */
class FoodsViewModel(
    private val getFoods: GetFoodsUseCase,
    private val searchFoods: SearchFoodsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ContentUiState<List<Food>>>(ContentUiState.Loading)
    val state: StateFlow<ContentUiState<List<Food>>> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ContentUiState.Loading
            _state.value = getFoods().toUiState()
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        viewModelScope.launch {
            _state.value = ContentUiState.Loading
            _state.value = searchFoods(newQuery).toUiState()
        }
    }

    private fun AppResult<List<Food>>.toUiState(): ContentUiState<List<Food>> = when (this) {
        is AppResult.Success -> value.toContentState()
        is AppResult.Failure -> ContentUiState.Error(error.toUserMessage())
    }
}

/**
 * Presentation owns user-facing copy: error KINDS become strings here, next to the screens
 * that show them. A raw `Throwable.message` never reaches the UI — the domain carries no
 * display text at all.
 */
internal fun DomainError.toUserMessage(): String = when (this) {
    DomainError.Network -> "Can't reach the server. Check your connection and try again."
    DomainError.NotFound -> "That food isn't in the catalog."
    is DomainError.Unexpected -> "Something went wrong. Please try again."
}
