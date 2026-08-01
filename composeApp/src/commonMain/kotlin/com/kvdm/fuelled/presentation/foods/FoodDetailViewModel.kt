package com.kvdm.fuelled.presentation.foods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR
import com.kvdm.fuelled.core.time.logicalDate
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.NewLogEntry
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.domain.usecase.AddLogEntriesUseCase
import com.kvdm.fuelled.domain.usecase.GetFoodUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * The catalog-first log's own lifecycle (UX-03) — separate from the food's [ContentUiState],
 * exactly as the tray's confirm state is separate from its catalog list.
 */
sealed interface FoodLogState {
    data object Idle : FoodLogState
    data object Saving : FoodLogState
    data class Logged(val slot: MealSlot) : FoodLogState
    data class Error(val message: String) : FoodLogState
}

/**
 * Resolves a single [Food] by id for the detail screen through [GetFoodUseCase] (the
 * repository), rather than reaching into an in-memory sample list. Same discipline as the
 * list VM: no try/catch (ARCH-07), typed [AppResult] folded into a [ContentUiState]; a
 * missing id arrives as `DomainError.NotFound` and renders the mapped copy, never a crash.
 *
 * Also owns the catalog-first write (UX-03): "Log this food" aims at a slot of the CURRENT
 * logical day and writes through [AddLogEntriesUseCase] — the same single write path as the
 * tray (TODAY-13's discipline), never a second one. The clock/zone/dayStartHour are injected
 * with production defaults so a test can sit the screen at a chosen instant (MEAL-01/02).
 */
class FoodDetailViewModel(
    private val getFood: GetFoodUseCase,
    private val addLogEntries: AddLogEntriesUseCase,
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val dayStartHour: Int = DEFAULT_DAY_START_HOUR,
) : ViewModel() {

    private val _state = MutableStateFlow<ContentUiState<Food>>(ContentUiState.Loading)
    val state: StateFlow<ContentUiState<Food>> = _state.asStateFlow()

    private val _logState = MutableStateFlow<FoodLogState>(FoodLogState.Idle)
    val logState: StateFlow<FoodLogState> = _logState.asStateFlow()

    fun load(foodId: String) {
        viewModelScope.launch {
            _state.value = ContentUiState.Loading
            _state.value = when (val result = getFood(foodId)) {
                is AppResult.Success -> ContentUiState.Content(result.value)
                is AppResult.Failure -> ContentUiState.Error(result.error.toUserMessage())
            }
        }
    }

    /**
     * Write the resolved food to [slot] of the current logical day, one serving, `LOGGED`
     * (UX-03). Guarded like the tray's confirm (MEAL-11's stance): no resolved food or an
     * in-flight save reaches no write. The id is deterministic per `(date, slot, food)` —
     * [NewLogEntry]'s contract — so a retry replaces rather than duplicates.
     */
    fun log(slot: MealSlot) {
        val food = (_state.value as? ContentUiState.Content)?.data ?: return
        if (_logState.value == FoodLogState.Saving) return

        _logState.value = FoodLogState.Saving
        viewModelScope.launch {
            val today = logicalDate(clock.now(), dayStartHour, zone)
            val entry = NewLogEntry(
                id = "${today}_${slot.name}_${food.id}",
                name = food.name,
                serving = food.serving,
                kcal = food.kcal,
                proteinG = food.proteinG,
                carbsG = food.carbsG,
                fatG = food.fatG,
                veg = food.veg,
            )
            _logState.value = when (val result = addLogEntries(listOf(entry), today, slot)) {
                is AppResult.Success -> FoodLogState.Logged(slot)
                is AppResult.Failure -> FoodLogState.Error(result.error.toUserMessage())
            }
        }
    }
}
