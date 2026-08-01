package com.kvdm.fuelled.presentation.foods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.domain.usecase.DeleteFoodUseCase
import com.kvdm.fuelled.domain.usecase.GetFoodUseCase
import com.kvdm.fuelled.domain.usecase.SaveFoodUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the editor is doing right now — separate from the food being edited (CAT-01). */
sealed interface FoodEditState {
    data object Editing : FoodEditState
    data object Saving : FoodEditState
    data object Saved : FoodEditState
    data class Error(val message: String) : FoodEditState
}

/**
 * The custom-food editor (CAT-01) — create a food the catalog doesn't have, or edit one you
 * made. The seeded catalog is reference data and is NOT editable: its numbers are what other
 * people's entries were measured against, and a catalog you can silently rewrite is one you
 * cannot trust a week later.
 *
 * The id is minted ONCE, at construction, from the caller's seed. That makes save idempotent
 * — a double-tap replaces the same row rather than creating twins — which is the same
 * reasoning as the tray's client-generated entry ids (MEAL-05).
 */
class FoodEditorViewModel(
    private val getFood: GetFoodUseCase,
    private val saveFood: SaveFoodUseCase,
    private val deleteFood: DeleteFoodUseCase,
) : ViewModel() {

    private val _food = MutableStateFlow<Food?>(null)
    val food: StateFlow<Food?> = _food.asStateFlow()

    private val _state = MutableStateFlow<FoodEditState>(FoodEditState.Editing)
    val state: StateFlow<FoodEditState> = _state.asStateFlow()

    /** Load an existing food for editing; a blank id means "new", and leaves the form empty. */
    fun load(foodId: String) {
        if (foodId.isBlank()) return
        viewModelScope.launch {
            when (val result = getFood(foodId)) {
                is AppResult.Success -> _food.value = result.value
                is AppResult.Failure -> _state.value = FoodEditState.Error(result.error.toUserMessage())
            }
        }
    }

    /**
     * CAT-01: save. The guards live HERE, before the write (MEAL-11's stance): a food with no
     * name is unfindable, and non-positive calories make every total it touches a lie. A
     * refused save leaves the form exactly as typed — nothing is silently corrected.
     */
    fun save(
        id: String,
        name: String,
        brand: String,
        serving: String,
        kcal: Int?,
        proteinG: Int?,
        carbsG: Int?,
        fatG: Int?,
        veg: Boolean,
    ) {
        if (name.isBlank() || serving.isBlank()) return
        if (kcal == null || kcal <= 0) return
        if (_state.value == FoodEditState.Saving) return

        _state.value = FoodEditState.Saving
        viewModelScope.launch {
            val food = Food(
                id = id,
                name = name.trim(),
                brand = brand.trim().ifBlank { "Custom" },
                serving = serving.trim(),
                kcal = kcal,
                proteinG = proteinG ?: 0,
                carbsG = carbsG ?: 0,
                fatG = fatG ?: 0,
                veg = veg,
                // An edit keeps whatever the food already was; a new food is custom by
                // definition — nothing else can reach this editor.
                favourite = _food.value?.favourite ?: false,
                custom = true,
            )
            _state.value = when (val result = saveFood(food)) {
                is AppResult.Success -> FoodEditState.Saved
                is AppResult.Failure -> FoodEditState.Error(result.error.toUserMessage())
            }
        }
    }

    /** CAT-01: delete a custom food. Past log rows keep their own snapshot — history stands. */
    fun delete(id: String) {
        viewModelScope.launch {
            _state.value = when (val result = deleteFood(id)) {
                is AppResult.Success -> FoodEditState.Saved
                is AppResult.Failure -> FoodEditState.Error(result.error.toUserMessage())
            }
        }
    }
}
