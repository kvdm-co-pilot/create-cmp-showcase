package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR
import com.kvdm.fuelled.core.time.logicalDate
import com.kvdm.fuelled.domain.model.PlanDay
import com.kvdm.fuelled.domain.repository.MealPlanRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Read one day of the structured plan (PLAN-02/PLAN-15..PLAN-23).
 *
 * **This is the only place the clock is read for the plan.** Both "which day is current"
 * (MEAL-01/MEAL-02) and "what time is it now" — the two inputs every punctuality claim rests on
 * — are derived here from the same instant, so focus and the day boundary can never be computed
 * a tick apart from each other. Everything downstream is a pure function of what this passes in.
 *
 * The clock, zone and `dayStartHour` are injected with production defaults so tests can drive
 * a chosen minute rather than race the wall clock.
 */
class GetPlanDayUseCase(
    private val repository: MealPlanRepository,
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val dayStartHour: Int = DEFAULT_DAY_START_HOUR,
) {
    suspend operator fun invoke(date: LocalDate): AppResult<PlanDay> {
        val instant = clock.now()
        return repository.planDay(
            date = date,
            today = logicalDate(instant, dayStartHour, zone),
            now = instant.toLocalDateTime(zone).time,
        )
    }

    /** The current logical day — what the plan screen opens on and the day strip centres (PLAN-11). */
    fun currentLogicalDay(): LocalDate = logicalDate(clock.now(), dayStartHour, zone)
}
