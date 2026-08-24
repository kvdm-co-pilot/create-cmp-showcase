package com.kvdm.fuelled.presentation.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.core.time.systemZone
import com.kvdm.fuelled.core.time.systemClock
import com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR
import com.kvdm.fuelled.core.time.logicalDate
import com.kvdm.fuelled.domain.model.BflCategory
import com.kvdm.fuelled.domain.model.ComposedMeal
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.model.MEAL_PRESETS
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.domain.usecase.GetFoodsUseCase
import com.kvdm.fuelled.domain.usecase.PlanMealUseCase
import com.kvdm.fuelled.presentation.today.toUserMessage
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

/** What the builder is doing right now — separate from what it has composed. */
sealed interface BuildState {
    data object Composing : BuildState
    data object Saving : BuildState
    data class Planned(val days: Int, val slot: MealSlot) : BuildState
    data class Error(val message: String) : BuildState
}

/**
 * The meal builder (BFL-05..08).
 *
 * Composes ONE meal from the catalog's Body-for-LIFE roles and writes it into a slot across
 * however many days you pick. It selects nothing on your behalf (brief decision D4): the app
 * states what your choices add up to, and never chooses food for you to hit a number.
 *
 * No `try`/`catch` (ARCH-07) — failures arrive typed and become copy here.
 */
class MealBuilderViewModel(
    getFoods: GetFoodsUseCase,
    private val planMeal: PlanMealUseCase,
    initialSlot: MealSlot = MealSlot.BREAKFAST,
    clock: Clock = systemClock,
    zone: TimeZone = systemZone(),
    dayStartHour: Int = DEFAULT_DAY_START_HOUR,
) : ViewModel() {

    private val today: LocalDate = logicalDate(clock.now(), dayStartHour, zone)

    private val _catalog = MutableStateFlow<Map<BflCategory, List<Food>>>(emptyMap())

    /** The catalog, grouped by role — the vocabulary the builder offers (BFL-04). */
    val catalog: StateFlow<Map<BflCategory, List<Food>>> = _catalog.asStateFlow()

    init {
        viewModelScope.launch {
            // An unreadable catalog leaves the columns empty rather than raising an error
            // screen: a builder with no proteins to offer says the same thing an error would,
            // and the seeded catalog ships in the binary (BFL-03) so this is close to
            // impossible in practice.
            _catalog.value = when (val result = getFoods()) {
                is AppResult.Success -> result.value.groupBy { it.category }
                is AppResult.Failure -> emptyMap()
            }
        }
    }

    private val _meal = MutableStateFlow(ComposedMeal())
    val meal: StateFlow<ComposedMeal> = _meal.asStateFlow()

    private val _slot = MutableStateFlow(initialSlot)
    val slot: StateFlow<MealSlot> = _slot.asStateFlow()

    /** Which of the next seven days this meal lands on. Today is selected to begin with. */
    private val _days = MutableStateFlow(setOf(today))
    val days: StateFlow<Set<LocalDate>> = _days.asStateFlow()

    private val _state = MutableStateFlow<BuildState>(BuildState.Composing)
    val state: StateFlow<BuildState> = _state.asStateFlow()

    /** The week the day chips offer: today and the next six. */
    val week: List<LocalDate> = (0..6).map { today.plus(it, DateTimeUnit.DAY) }

    /** BFL-05: picking is a TOGGLE — tapping the chosen food again clears its role. */
    fun onPick(food: Food) {
        _meal.value =
            if (_meal.value[food.category]?.id == food.id) _meal.value.without(food.category)
            else _meal.value.with(food)
        _state.value = BuildState.Composing
    }

    /** BFL-07: a preset only fills the selection; every pick stays changeable afterwards. */
    fun onPreset(presetId: String) {
        val preset = MEAL_PRESETS.firstOrNull { it.id == presetId } ?: return
        val byId = catalog.value.values.flatten().associateBy { it.id }
        _meal.value = preset.foodIds.mapNotNull(byId::get)
            .fold(ComposedMeal()) { acc, food -> acc.with(food) }
        _state.value = BuildState.Composing
    }

    fun onSlot(slot: MealSlot) {
        _slot.value = slot
    }

    fun onToggleDay(date: LocalDate) {
        _days.value = if (date in _days.value) _days.value - date else _days.value + date
    }

    /** Every day of the offered week — the "plan my whole week" shortcut. */
    fun onAllDays() {
        _days.value = if (_days.value.size == week.size) setOf(today) else week.toSet()
    }

    /**
     * BFL-06: write it. The guard is HERE rather than on a disabled button, for MEAL-11's
     * reason: a rendering cannot be the contract, and anything holding this ViewModel can
     * call this.
     */
    fun onPlan() {
        val meal = _meal.value
        val days = _days.value
        if (meal.isEmpty || days.isEmpty()) return
        if (_state.value == BuildState.Saving) return

        val slot = _slot.value
        _state.value = BuildState.Saving
        viewModelScope.launch {
            _state.value = when (val result = planMeal(meal, slot, days.sorted())) {
                is AppResult.Success -> BuildState.Planned(result.value, slot)
                is AppResult.Failure -> BuildState.Error(result.error.toUserMessage())
            }
        }
    }

    /** Start again after a successful plan — the selection clears, the slot and days stay. */
    fun onReset() {
        _meal.value = ComposedMeal()
        _state.value = BuildState.Composing
    }
}
