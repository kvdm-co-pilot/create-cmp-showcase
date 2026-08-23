package com.kvdm.fuelled.presentation.workouts

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.WorkoutDayPlan
import com.kvdm.fuelled.domain.model.WorkoutWeek
import com.kvdm.fuelled.presentation.components.ContentUiState
import com.kvdm.fuelled.testing.TEST_NOW
import com.kvdm.fuelled.testing.TEST_ZONE
import com.kvdm.fuelled.testing.fakes.FakeTimeSignal
import com.kvdm.fuelled.testing.fakes.FakeWorkoutRepository
import com.kvdm.fuelled.testing.keepCollecting
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * The Training tab (NAV-06) — the training week as seven dated days, anchored on the logical
 * week the rest of the app anchors on. TEST_NOW is Wednesday 2026-07-22, so the window is
 * Mon 2026-07-20 .. Sun 2026-07-26 and "today" is the third row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutWeekViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeWorkoutRepository(today = LocalDate(2026, 7, 22))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = WorkoutWeekViewModel(
        workouts = repository,
        time = FakeTimeSignal(TEST_NOW),
        zone = TEST_ZONE,
    )

    // SPEC: NAV-06
    @Test
    fun `the week is the seven days of the current logical week, Monday first`() = runTest(dispatcher) {
        val vm = viewModel()
        keepCollecting(vm.state)
        advanceUntilIdle()

        val ui = assertIs<ContentUiState.Content<WorkoutWeekUi>>(vm.state.value).data
        assertEquals(7, ui.days.size, "a training week is always all seven days")
        assertEquals(LocalDate(2026, 7, 20), ui.days.first().date, "Monday first")
        assertEquals(LocalDate(2026, 7, 26), ui.days.last().date, "Sunday last")
        assertEquals(LocalDate(2026, 7, 22), ui.today)
    }

    // SPEC: NAV-06
    @Test
    fun `the summary counts sessions kept against sessions planned, ignoring rest days`() =
        runTest(dispatcher) {
            repository.week = WorkoutWeek(
                mapOf(
                    DayOfWeek.MONDAY to WorkoutDayPlan("Upper body", LocalTime(18, 0)),
                    DayOfWeek.TUESDAY to WorkoutDayPlan("Cardio", LocalTime(18, 0)),
                    DayOfWeek.WEDNESDAY to WorkoutDayPlan("Upper body", LocalTime(18, 0)),
                    // Thursday..Sunday left as rest — they must not inflate the denominator.
                ),
            )
            repository.done = setOf(LocalDate(2026, 7, 20))

            val vm = viewModel()
            keepCollecting(vm.state)
            advanceUntilIdle()

            val ui = assertIs<ContentUiState.Content<WorkoutWeekUi>>(vm.state.value).data
            assertEquals(3, ui.planned, "rest days are not planned sessions")
            assertEquals(1, ui.kept)
        }

    // SPEC: NAV-06
    @Test
    fun `ticking today writes through the repository rather than mutating a local list`() =
        runTest(dispatcher) {
            val vm = viewModel()
            keepCollecting(vm.state)
            advanceUntilIdle()

            vm.onToggleTodayDone(true)
            advanceUntilIdle()

            assertEquals(true, repository.lastSetDone, "the tick persists; the screen renders what comes back")
        }

    // SPEC: NAV-07
    @Test
    fun `a repository failure folds into Error, never a thrown exception`() = runTest(dispatcher) {
        repository.failure = DomainError.Unexpected()
        val vm = viewModel()
        keepCollecting(vm.state)
        advanceUntilIdle()

        assertTrue(vm.state.value is ContentUiState.Error, "typed failures cross the boundary as state")
    }
}
