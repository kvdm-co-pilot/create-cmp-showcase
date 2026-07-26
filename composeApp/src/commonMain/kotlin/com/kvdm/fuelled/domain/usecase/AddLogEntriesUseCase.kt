package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR
import com.kvdm.fuelled.core.time.logicalDate
import com.kvdm.fuelled.domain.model.LogStatus
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.NewLogEntry
import com.kvdm.fuelled.domain.repository.TodayRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Confirm a tray: write every item to one `(date, slot)` target in a single transaction
 * (MEAL-05), with the status the target implies (MEAL-08).
 *
 * **This is where MEAL-08 lives.** Scheduling and logging are the same write with a different
 * target, so the only decision here is which one this target is: the entries are `LOGGED` when
 * they land on the CURRENT logical day, and `PLANNED` when they land on a future one. A past
 * date is `LOGGED` too — back-filling yesterday's lunch records something that was eaten; only
 * the future is a plan.
 *
 * The current logical day is DERIVED from the clock on every call ([logicalDate], MEAL-01/02)
 * — never read from a stored boundary. The clock, zone, and `dayStartHour` are injected with
 * defaults so tests drive the boundary instead of racing the wall clock; nothing in here calls
 * a global "now".
 */
class AddLogEntriesUseCase(
    private val repository: TodayRepository,
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val dayStartHour: Int = DEFAULT_DAY_START_HOUR,
) {
    suspend operator fun invoke(
        entries: List<NewLogEntry>,
        date: LocalDate,
        slot: MealSlot,
    ): AppResult<Unit> {
        val currentLogicalDay = logicalDate(clock.now(), dayStartHour, zone)
        val status = if (date > currentLogicalDay) LogStatus.PLANNED else LogStatus.LOGGED
        return repository.addEntries(entries, date, slot, status)
    }
}
