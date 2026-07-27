package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.LogStatus
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.NewLogEntry
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.testing.fakes.FakeTodayRepository
import com.kvdm.fuelled.testing.fakes.FixedClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * The tray-confirm use case — where MEAL-08 lives. Scheduling and logging are the SAME write
 * with a different target, so the only thing under test is which one this target is.
 *
 * Driven entirely by a [FixedClock]: the decision turns on the CURRENT logical day, and a test
 * that read the wall clock would pass or fail depending on the hour it ran at. One case sits a
 * minute before the 04:00 day start on purpose — that is where a naive calendar comparison
 * quietly writes PLANNED for a meal being eaten right now.
 */
class AddLogEntriesUseCaseTest {

    private val zone = TimeZone.UTC
    private val repository = FakeTodayRepository()

    private val logicalToday = LocalDate(2026, 7, 22)
    private val tomorrow = LocalDate(2026, 7, 23)

    private fun useCase(at: LocalDateTime) = AddLogEntriesUseCase(
        repository = repository,
        clock = FixedClock(at.toInstant(zone)),
        zone = zone,
        dayStartHour = DEFAULT_DAY_START_HOUR,
    )

    private val tray = listOf(
        NewLogEntry("t1", "Chicken breast", "200 g", kcal = 330, proteinG = 62, carbsG = 0, fatG = 7),
        NewLogEntry("t2", "Basmati rice", "150 g", kcal = 195, proteinG = 4, carbsG = 43, fatG = 1),
    )

    // SPEC: MEAL-08
    @Test
    fun `a confirm targeting the current logical day writes LOGGED`() = runTest {
        val addToToday = useCase(at = LocalDateTime(2026, 7, 22, 13, 0))

        val result = addToToday(tray, logicalToday, MealSlot.LUNCH)

        assertEquals(AppResult.Success(Unit), result)
        val call = repository.addCalls.single()
        assertEquals(LogStatus.LOGGED, call.status)
        assertEquals(logicalToday, call.date)
        assertEquals(MealSlot.LUNCH, call.slot)
        assertEquals(tray, call.entries, "every tray item goes to the one target")
    }

    // SPEC: MEAL-08
    @Test
    fun `a confirm targeting a future logical day writes PLANNED`() = runTest {
        val scheduleTomorrow = useCase(at = LocalDateTime(2026, 7, 22, 13, 0))

        scheduleTomorrow(tray, tomorrow, MealSlot.DINNER)

        val call = repository.addCalls.single()
        assertEquals(LogStatus.PLANNED, call.status)
        assertEquals(tomorrow, call.date, "the same write, a different target")
    }

    // SPEC: MEAL-08
    @Test
    fun `just before the day start, the previous date is still the current logical day - so it writes LOGGED`() =
        runTest {
            // 03:59 on the 23rd. The calendar says the 23rd; the logical day is still the 22nd,
            // so a 1am snack filed under the 22nd is something being eaten, not a plan.
            val lateSnack = useCase(at = LocalDateTime(2026, 7, 23, 3, 59))

            lateSnack(tray, logicalToday, MealSlot.EVENING_SNACK)

            assertEquals(LogStatus.LOGGED, repository.addCalls.single().status)
        }

    // SPEC: MEAL-08
    @Test
    fun `just before the day start, the calendar's date is still a FUTURE logical day - so it writes PLANNED`() =
        runTest {
            // The same 03:59 instant: the 23rd has not begun as a logical day yet, so targeting
            // it is scheduling. This is the exact pair a naive `date > now.date` gets backwards.
            val lateSnack = useCase(at = LocalDateTime(2026, 7, 23, 3, 59))

            lateSnack(tray, tomorrow, MealSlot.BREAKFAST)

            assertEquals(LogStatus.PLANNED, repository.addCalls.single().status)
        }

    // SPEC: MEAL-05
    @Test
    fun `passes a typed failure through untouched`() = runTest {
        repository.failure = DomainError.Network
        val addToToday = useCase(at = LocalDateTime(2026, 7, 22, 13, 0))

        assertEquals(AppResult.Failure(DomainError.Network), addToToday(tray, logicalToday, MealSlot.LUNCH))
    }
}
