package com.kvdm.cmpshowcase.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.cmpshowcase.domain.model.Item
import com.kvdm.cmpshowcase.domain.usecase.GetItemsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val items: List<Item> = emptyList(),
    val errorMessage: String? = null,
)

class HomeViewModel(
    private val getItems: GetItemsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = HomeUiState(isLoading = true)
            _state.value = try {
                HomeUiState(isLoading = false, items = getItems())
            } catch (e: Exception) {
                HomeUiState(isLoading = false, errorMessage = e.message ?: "Something went wrong")
            }
        }
    }
}
