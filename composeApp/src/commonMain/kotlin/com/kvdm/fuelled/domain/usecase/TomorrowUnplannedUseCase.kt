package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.core.time.systemZone
import com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR
import com.kvdm.fuelled.core.time.RealTimeSignal
import com.kvdm.fuelled.core.time.TimeSignal
import com.kvdm.fuelled.core.time.currentDay
import com.kvdm.fuelled.domain.repository.MealPlanRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * The nudge's one question, asked in one place: does the NEXT logical day have zero planned
 * entries (NOTIF-04/NOTIF-05)?
 *
 * Both the arm path and the delivery-time re-check (NOTIF-06) go through this, so "unplanned"
 * cannot mean two different things at the two moments it is asked. Emptiness is judged on
 * ENTRIES, never on slot count — the derivation returns all six slots for every date whether
 * or not anything was ever written for it.
 *
 * A failed read answers `false` — tomorrow is treated as planned, and no nudge exists
 * (NOTIF-05). Nagging a user whose plan merely failed to load is the worse error in both
 * directions: the nudge's whole claim is "nothing is planned", and this is the one caller
 * that must not guess.
 */
class TomorrowUnplannedUseCase(
    private val repository: MealPlanRepository,
    private val time: TimeSignal = RealTimeSignal(),
    private val zone: kotlinx.datetime.TimeZone = systemZone(),
    private val dayStartHour: Int = DEFAULT_DAY_START_HOUR,
) {
    suspend operator fun invoke(): Boolean {
        val today = time.currentDay(dayStartHour, zone)
        val tomorrow = today.plus(DatePeriod(days = 1))
        val now = time.now().toLocalDateTime(zone).time
        return when (val day = repository.planDay(tomorrow, today, now)) {
            is AppResult.Failure -> false
            is AppResult.Success -> day.value.slots.all { it.entries.isEmpty() }
        }
    }
}
