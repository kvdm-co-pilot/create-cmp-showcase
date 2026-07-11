package com.kvdm.cmpshowcase.presentation.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.cmpshowcase.domain.model.Favorite
import com.kvdm.cmpshowcase.domain.usecase.GetFavoritesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val isLoading: Boolean = true,
    val items: List<Favorite> = emptyList(),
    val errorMessage: String? = null,
)

class FavoritesViewModel(
    private val getFavorites: GetFavoritesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = FavoritesUiState(isLoading = true)
            _state.value = try {
                FavoritesUiState(isLoading = false, items = getFavorites())
            } catch (e: Exception) {
                FavoritesUiState(isLoading = false, errorMessage = e.message ?: "Something went wrong")
            }
        }
    }
}
