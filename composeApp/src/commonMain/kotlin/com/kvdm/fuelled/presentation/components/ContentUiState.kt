package com.kvdm.fuelled.presentation.components

/**
 * The four-way lifecycle of a data-backed screen: Loading, Error, Empty, Content.
 * Sealed so a `when` over it is exhaustive — a screen cannot forget a state, and cannot
 * render two at once. ViewModels fold repository results into this type;
 * [ContentStateContainer] renders it.
 */
sealed interface ContentUiState<out T> {
    data object Loading : ContentUiState<Nothing>
    data class Error(val message: String) : ContentUiState<Nothing>
    data object Empty : ContentUiState<Nothing>
    data class Content<out T>(val data: T) : ContentUiState<T>
}

/** ViewModel-side helper: the Empty/Content decision made once, not per screen. */
fun <E> List<E>.toContentState(): ContentUiState<List<E>> =
    if (isEmpty()) ContentUiState.Empty else ContentUiState.Content(this)
