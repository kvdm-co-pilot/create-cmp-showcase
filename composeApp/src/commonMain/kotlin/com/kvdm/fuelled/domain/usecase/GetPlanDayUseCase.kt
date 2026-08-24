package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.core.time.systemZone
import com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR
import com.kvdm.fuelled.core.time.logicalDate
import com.kvdm.fuelled.domain.model.PlanDay
import com.kvdm.fuelled.domain.repository.MealPlanRepository
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.core.time.RealTimeSignal
import com.kvdm.fuelled.core.time.TimeSignal
import com.kvdm.fuelled.core.time.currentDay
import com.kvdm.fuelled.core.time.days
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

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
    private val time: TimeSignal = RealTimeSignal(),
    private val zone: TimeZone = systemZone(),
    private val dayStartHour: Int = DEFAULT_DAY_START_HOUR,
) {
    /**
     * Observe one day. The repository derives `today` and `now` per emission from the same
     * signal, so focus and the day boundary still cannot be computed a tick apart — that
     * guarantee moved with the derivation rather than being lost.
     */
    operator fun invoke(date: LocalDate): Flow<AppResult<PlanDay>> = repository.observePlanDay(date)

    /**
     * The CURRENT logical day, as a stream — what the plan screen opens on and the day strip
     * centres (PLAN-11), and what Today speaks for. A screen that held this as a value showed
     * yesterday's date after a night with the app open (observed on-device 2026-07-28).
     */
    fun currentLogicalDay(): Flow<LocalDate> = time.days(dayStartHour, zone)

    /** The current logical day as a one-shot — for a WRITE's target, never for held state. */
    fun currentLogicalDayNow(): LocalDate = time.currentDay(dayStartHour, zone)
}
