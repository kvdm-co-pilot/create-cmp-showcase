package com.kvdm.fuelled.presentation.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.domain.model.WeekReview
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.domain.usecase.GetWeekReviewUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.presentation.today.toUserMessage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The week in review's ViewModel (JRN-01) — a pure projection of the observed week stream.
 * No retry control anywhere (RS-01, TODAY-05's reasoning): the state is observed, so a
 * transient failure is replaced by the source's next emission, not by a button. No
 * `try`/`catch` (ARCH-07): failures arrive typed and become copy here.
 */
class WeekReviewViewModel(
    getWeekReview: GetWeekReviewUseCase,
) : ViewModel() {

    val state: StateFlow<ContentUiState<WeekReview>> =
        getWeekReview()
            .map { result ->
                when (result) {
                    is AppResult.Success -> ContentUiState.Content(result.value)
                    is AppResult.Failure -> ContentUiState.Error(result.error.toUserMessage())
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContentUiState.Loading)
}
