package com.kvdm.fuelled.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Profile
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.domain.usecase.GetProfileUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Profile ViewModel — folds the use case's [AppResult] into the shared [ContentUiState]
 * state machine (mirrors TodayViewModel).
 *
 * No `try`/`catch` here — ever (ARCH-07). Failures arrive as typed [AppResult.Failure] values;
 * the ViewModel maps [DomainError] KINDS to user-facing copy. A `CancellationException` thrown
 * while suspended simply cancels this coroutine (structured concurrency) — never an error state.
 *
 * A profile always exists (the source seeds one on first run), so there is no dataless [Empty]
 * arm — the machine is Loading, Content, or Error only (PROF-01/PROF-05).
 */
class ProfileViewModel(
    private val getProfile: GetProfileUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ContentUiState<Profile>>(ContentUiState.Loading)
    val state: StateFlow<ContentUiState<Profile>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ContentUiState.Loading
            _state.value = getProfile().toUiState()
        }
    }

    private fun AppResult<Profile>.toUiState(): ContentUiState<Profile> = when (this) {
        is AppResult.Success -> ContentUiState.Content(value)
        is AppResult.Failure -> ContentUiState.Error(error.toUserMessage())
    }
}

/**
 * Presentation owns user-facing copy: error KINDS become strings here, next to the screen that
 * shows them. A raw `Throwable.message` never reaches the UI — the domain carries no display text.
 */
internal fun DomainError.toUserMessage(): String = when (this) {
    DomainError.Network -> "Can't load your profile right now. Check your connection and try again."
    DomainError.NotFound -> "We couldn't find your profile."
    is DomainError.Unexpected -> "Something went wrong. Please try again."
}
