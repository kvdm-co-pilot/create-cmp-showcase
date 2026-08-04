package com.kvdm.fuelled.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvdm.fuelled.domain.model.AppState
import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.domain.model.UnitSystem
import com.kvdm.fuelled.domain.model.WorkoutDayPlan
import com.kvdm.fuelled.domain.model.WorkoutWeek
import com.kvdm.fuelled.domain.repository.WorkoutRepository
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.domain.usecase.SaveWorkoutDayUseCase
import com.kvdm.fuelled.domain.usecase.DeleteSupplementUseCase
import kotlinx.datetime.DayOfWeek
import com.kvdm.fuelled.domain.usecase.GetSupplementStackUseCase
import com.kvdm.fuelled.domain.usecase.ObserveAppStateUseCase
import com.kvdm.fuelled.domain.usecase.SaveSupplementUseCase
import com.kvdm.fuelled.domain.usecase.SetPrepLeadUseCase
import com.kvdm.fuelled.domain.usecase.SetUnitSystemUseCase
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.presentation.today.toUserMessage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings (SET-01..08) — a projection of two observed streams (the app-state row and the
 * supplement stack) and five writes.
 *
 * Every write goes through the SAME use cases the rest of the app uses: the stack this screen
 * edits is the stack the Supplements tab renders and Today counts, so a supplement added here
 * appears there with no reload (RS-01). No `try`/`catch` (ARCH-07) — failures arrive typed.
 */
class SettingsViewModel(
    observeAppState: ObserveAppStateUseCase,
    getStack: GetSupplementStackUseCase,
    private val setUnitSystem: SetUnitSystemUseCase,
    private val setPrepLead: SetPrepLeadUseCase,
    private val saveSupplement: SaveSupplementUseCase,
    private val deleteSupplement: DeleteSupplementUseCase,
    private val saveWorkoutDay: SaveWorkoutDayUseCase,
    workouts: WorkoutRepository,
) : ViewModel() {

    val state: StateFlow<ContentUiState<SettingsUi>> =
        combine(
            observeAppState(),
            getStack(),
            workouts.observeWeek(),
        ) { appState, stack, week -> fold(appState, stack, week) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContentUiState.Loading)

    fun onUnitSystem(system: UnitSystem) {
        viewModelScope.launch { setUnitSystem(system) }
    }

    /** SET-08: the use case re-arms every reminder as part of the write — not tomorrow. */
    fun onPrepLead(minutes: Int) {
        viewModelScope.launch { setPrepLead(minutes) }
    }

    /** SET-04/SUPP-08/SUPP-12: one save carries the whole row — schedule and ladder included. */
    fun onSaveSupplement(supplement: Supplement) {
        viewModelScope.launch { saveSupplement(supplement) }
    }

    fun onDeleteSupplement(id: String) {
        viewModelScope.launch { deleteSupplement(id) }
    }

    /** WORK-07: the use case re-arms as part of the write, so a new time is live at once. */
    fun onSaveWorkoutDay(day: DayOfWeek, plan: WorkoutDayPlan) {
        viewModelScope.launch { saveWorkoutDay(day, plan) }
    }

    private fun fold(
        appState: AppResult<AppState>,
        stack: AppResult<List<Supplement>>,
        week: AppResult<WorkoutWeek>,
    ): ContentUiState<SettingsUi> = when {
        // Settings ARE the app-state row: without it there is nothing to render or change.
        appState is AppResult.Failure -> ContentUiState.Error(appState.error.toUserMessage())
        appState is AppResult.Success -> ContentUiState.Content(
            SettingsUi(
                settings = appState.value.settings,
                // A stack that fails to load renders as empty rather than taking the units
                // and the reminder lead down with it — you can still fix your settings while
                // one source is unhappy (RS-01 heals it on the next emission).
                stack = (stack as? AppResult.Success)?.value.orEmpty(),
                // WORK-07: same tolerance — an unreadable week falls back to the seeded split
                // rather than rendering seven blank rows that look like a wiped setting.
                week = (week as? AppResult.Success)?.value ?: WorkoutWeek.DEFAULT,
            ),
        )
        else -> ContentUiState.Loading
    }
}
