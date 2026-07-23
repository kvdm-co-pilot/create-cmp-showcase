package com.kvdm.fuelled.presentation.foods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.domain.usecase.GetFoodUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Resolves a single [Food] by id for the detail screen through [GetFoodUseCase] (the
 * repository), rather than reaching into an in-memory sample list. Same discipline as the
 * list VM: no try/catch (ARCH-07), typed [AppResult] folded into a [ContentUiState]; a
 * missing id arrives as `DomainError.NotFound` and renders the mapped copy, never a crash.
 */
class FoodDetailViewModel(
    private val getFood: GetFoodUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ContentUiState<Food>>(ContentUiState.Loading)
    val state: StateFlow<ContentUiState<Food>> = _state.asStateFlow()

    fun load(foodId: String) {
        viewModelScope.launch {
            _state.value = ContentUiState.Loading
            _state.value = when (val result = getFood(foodId)) {
                is AppResult.Success -> ContentUiState.Content(result.value)
                is AppResult.Failure -> ContentUiState.Error(result.error.toUserMessage())
            }
        }
    }
}
