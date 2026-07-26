package com.kvdm.fuelled.presentation.meal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR
import com.kvdm.fuelled.core.time.logicalDate
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.NewLogEntry
import com.kvdm.fuelled.domain.model.slotForLocalTime
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.domain.usecase.AddLogEntriesUseCase
import com.kvdm.fuelled.domain.usecase.GetFoodsUseCase
import com.kvdm.fuelled.domain.usecase.SearchFoodsUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.presentation.components.toContentState
import com.kvdm.fuelled.presentation.foods.toUserMessage
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * One line on its way into the log: a catalog [food] at a serving multiple. The line, not the
 * screen, owns the multiplication — [MealTrayViewModel] never recomputes macros by hand, and
 * the running total (MEAL-09) sums these values.
 */
data class TrayLine(val food: Food, val servings: Int = 1) {
    val kcal: Int get() = food.kcal * servings
    val proteinG: Int get() = food.proteinG * servings
    val carbsG: Int get() = food.carbsG * servings
    val fatG: Int get() = food.fatG * servings
}

/**
 * The tray's running total (MEAL-09): calories PLUS protein, carbs and fat. Four macros, not
 * a bare calorie count — Fuelled's whole design is macro-first, and a tray that only totalled
 * kcal would be the one screen in the app that hides them.
 */
data class TrayTotal(
    val items: Int,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
) {
    val isEmpty: Boolean get() = items == 0

    companion object {
        val Empty = TrayTotal(items = 0, kcal = 0, proteinG = 0, carbsG = 0, fatG = 0)

        fun of(lines: List<TrayLine>): TrayTotal = TrayTotal(
            items = lines.size,
            kcal = lines.sumOf { it.kcal },
            proteinG = lines.sumOf { it.proteinG },
            carbsG = lines.sumOf { it.carbsG },
            fatG = lines.sumOf { it.fatG },
        )
    }
}

/**
 * What the tray holds, with its [total] DERIVED in the constructor rather than tracked
 * alongside the lines. That is MEAL-09 by construction: every add, remove, and serving change
 * produces a new `TrayContents`, so the total cannot go stale — there is no second field to
 * forget to update, and no `total` setter to get out of step with `lines`.
 */
data class TrayContents(val lines: List<TrayLine> = emptyList()) {
    val total: TrayTotal = TrayTotal.of(lines)
    val isEmpty: Boolean get() = lines.isEmpty()

    fun holds(foodId: String): Boolean = lines.any { it.food.id == foodId }
}

/**
 * Where the tray is aimed: one logical [date] and one [slot] (MEAL-10). [currentDay] is the
 * logical day the tray opened on — the anchor the offered dates and their relative labels
 * ("Today", "Tomorrow") are derived from, so nothing downstream has to re-read a clock.
 *
 * Retargeting replaces this value and NOTHING else: the tray's contents live in a separate
 * flow, so "add to Dinner tomorrow" is the identical flow to "add to Lunch today".
 */
data class MealTrayTarget(
    val date: LocalDate,
    val slot: MealSlot,
    val currentDay: LocalDate,
) {
    /** The days the tray offers as targets: yesterday (back-fill), today, tomorrow (plan). */
    val dateOptions: List<LocalDate>
        get() = listOf(
            currentDay.minus(1, DateTimeUnit.DAY),
            currentDay,
            currentDay.plus(1, DateTimeUnit.DAY),
        )
}

/**
 * The target a caller AIMS the tray at when it opens (TODAY-07/TODAY-08): the logical date and
 * the slot, carried from the tap that opened it.
 *
 * Deliberately not a whole [MealTrayTarget]: `currentDay` is not the caller's to state. It is
 * the logical day the tray itself opened on — the anchor the offered dates and their relative
 * labels are derived from — so [MealTrayViewModel] always derives it from its own clock, even
 * when the date and slot arrive from outside.
 */
data class MealTrayInitialTarget(val date: LocalDate, val slot: MealSlot)

/** The confirm's own lifecycle — separate from the food list's [ContentUiState]. */
sealed interface TrayConfirmState {
    data object Idle : TrayConfirmState
    data object Saving : TrayConfirmState
    data object Saved : TrayConfirmState
    data class Error(val message: String) : TrayConfirmState
}

/**
 * The add-to-meal tray's ViewModel (MEAL-09/10/11).
 *
 * No `try`/`catch` — ever (ARCH-07). The foods catalog and the confirm both arrive as typed
 * [AppResult] values that this class folds into sealed UI states, exactly like the exemplar
 * [com.kvdm.fuelled.presentation.foods.FoodsViewModel]; the search runs through
 * [SearchFoodsUseCase] (the repository/DAO), never in the composable.
 *
 * The clock, zone, and `dayStartHour` are injected with production defaults so a test can sit
 * the tray at a chosen instant instead of racing the wall clock. The opening target is derived
 * once, on construction: the logical day from [logicalDate] (MEAL-01/02) and the slot from
 * [slotForLocalTime] (MEAL-04) — both ML-1 functions, called, never re-derived here.
 *
 * [initialTarget] is how a caller AIMS the tray (TODAY-07/TODAY-08). It is a CONSTRUCTOR input,
 * not a retarget after the fact: "add to Dinner" must open on Dinner, never open on the
 * clock's slot and then correct itself — a first frame on the wrong target is exactly the
 * defaulting the clause forbids. Absent (`null`), the clock-derived opening target stands.
 */
class MealTrayViewModel(
    private val getFoods: GetFoodsUseCase,
    private val searchFoods: SearchFoodsUseCase,
    private val addLogEntries: AddLogEntriesUseCase,
    initialTarget: MealTrayInitialTarget? = null,
    clock: Clock = Clock.System,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    dayStartHour: Int = DEFAULT_DAY_START_HOUR,
) : ViewModel() {

    private val _state = MutableStateFlow<ContentUiState<List<Food>>>(ContentUiState.Loading)
    val state: StateFlow<ContentUiState<List<Food>>> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _target = MutableStateFlow(
        clock.now().toLocalDateTime(zone).let { openedAt ->
            val currentDay = logicalDate(clock.now(), dayStartHour, zone)
            MealTrayTarget(
                date = initialTarget?.date ?: currentDay,
                slot = initialTarget?.slot ?: slotForLocalTime(openedAt.time),
                currentDay = currentDay,
            )
        },
    )
    val target: StateFlow<MealTrayTarget> = _target.asStateFlow()

    private val _tray = MutableStateFlow(TrayContents())
    val tray: StateFlow<TrayContents> = _tray.asStateFlow()

    private val _confirmState = MutableStateFlow<TrayConfirmState>(TrayConfirmState.Idle)
    val confirmState: StateFlow<TrayConfirmState> = _confirmState.asStateFlow()

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

    /** MEAL-10: retarget the slot. The tray's contents are untouched. */
    fun onSlotSelected(slot: MealSlot) {
        _target.value = _target.value.copy(slot = slot)
        _confirmState.value = TrayConfirmState.Idle
    }

    /** MEAL-10: retarget the date. The tray's contents are untouched. */
    fun onDateSelected(date: LocalDate) {
        _target.value = _target.value.copy(date = date)
        _confirmState.value = TrayConfirmState.Idle
    }

    /** MEAL-09: tick a food in or out; the total is recomputed by [TrayContents]. */
    fun onFoodToggled(food: Food) {
        val lines = _tray.value.lines
        _tray.value = TrayContents(
            if (lines.any { it.food.id == food.id }) lines.filterNot { it.food.id == food.id }
            else lines + TrayLine(food),
        )
        _confirmState.value = TrayConfirmState.Idle
    }

    /**
     * MEAL-09: adjust a line's serving multiple; the total is recomputed by [TrayContents].
     * Below one serving is a removal, not a zero line — a tray line that contributes nothing
     * is the confusing state this coercion exists to avoid.
     */
    fun onServingsChanged(foodId: String, servings: Int) {
        _tray.value = TrayContents(
            _tray.value.lines.map {
                if (it.food.id == foodId) it.copy(servings = servings.coerceAtLeast(1)) else it
            },
        )
        _confirmState.value = TrayConfirmState.Idle
    }

    /**
     * Confirm the tray into its target (MEAL-05's transaction, MEAL-08's status — both owned
     * by [AddLogEntriesUseCase], not re-implemented here).
     *
     * **MEAL-11 lives on the first line.** The empty-tray refusal is a guard in the ViewModel,
     * not a disabled button: a disabled control is a rendering, and a rendering cannot be the
     * clause — anything holding this ViewModel (a test, a restored screen, a stale click that
     * lands after the last item is removed) can call `confirm()`, and none of them may reach
     * the write. So the guard returns BEFORE the coroutine is launched and before the use case
     * is touched, which is what makes "no write can be attempted" testable at the repository.
     */
    fun confirm() {
        val contents = _tray.value
        if (contents.isEmpty) return
        if (_confirmState.value == TrayConfirmState.Saving) return

        val target = _target.value
        _confirmState.value = TrayConfirmState.Saving
        viewModelScope.launch {
            val result = addLogEntries(contents.toNewEntries(target), target.date, target.slot)
            _confirmState.value = when (result) {
                is AppResult.Success -> {
                    _tray.value = TrayContents()
                    TrayConfirmState.Saved
                }
                is AppResult.Failure -> TrayConfirmState.Error(result.error.toUserMessage())
            }
        }
    }

    /**
     * The tray's lines as write-model entries. The id is CLIENT-generated and deterministic
     * per `(date, slot, food)` — [NewLogEntry]'s contract — so a retry after a dropped write
     * replaces the same rows instead of duplicating the meal.
     */
    private fun TrayContents.toNewEntries(target: MealTrayTarget): List<NewLogEntry> =
        lines.map { line ->
            NewLogEntry(
                id = "${target.date}_${target.slot.name}_${line.food.id}",
                name = line.food.name,
                serving = if (line.servings == 1) line.food.serving
                else "${line.servings} × ${line.food.serving}",
                kcal = line.kcal,
                proteinG = line.proteinG,
                carbsG = line.carbsG,
                fatG = line.fatG,
            )
        }

    private fun AppResult<List<Food>>.toUiState(): ContentUiState<List<Food>> = when (this) {
        is AppResult.Success -> value.toContentState()
        is AppResult.Failure -> ContentUiState.Error(error.toUserMessage())
    }
}
