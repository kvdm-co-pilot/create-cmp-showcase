package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.LogEntry
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.MealTimes
import com.kvdm.fuelled.domain.model.PlanDay
import com.kvdm.fuelled.domain.model.buildPlanDay
import com.kvdm.fuelled.domain.repository.MealPlanRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Hand-written fake — the template's testing convention (no mocking frameworks). Configurable
 * state, recorded interactions, implements the DOMAIN interface, returns typed
 * [AppResult.Failure] and never throws (ARCH-06).
 *
 * It holds the same three stored facts the real Room source does — times, done-ticks, water
 * ticks — and resolves days through the REAL [buildPlanDay]. That matters: a fake that returned
 * hand-built [PlanDay] values could hand a ViewModel a combination of states the derivation can
 * never actually produce, and the test would pass on a day the app cannot have.
 */
class FakeMealPlanRepository : MealPlanRepository {

    var times: MealTimes = MealTimes()
    var failure: DomainError? = null

    /** Entries per day, keyed by logical date — what the containers are filled from. */
    val entries: MutableMap<LocalDate, Map<MealSlot, List<LogEntry>>> = mutableMapOf()
    val doneSlots: MutableMap<LocalDate, MutableSet<MealSlot>> = mutableMapOf()
    val waterTicks: MutableMap<LocalDate, MutableSet<Int>> = mutableMapOf()

    data class DoneCall(val date: LocalDate, val slot: MealSlot, val done: Boolean)
    data class WaterCall(val date: LocalDate, val index: Int, val done: Boolean)
    data class CopyCall(val from: LocalDate, val to: List<LocalDate>)

    /** Every write ATTEMPT, recorded before `failure` is applied, so failures are observable too. */
    val doneCalls: MutableList<DoneCall> = mutableListOf()
    val waterCalls: MutableList<WaterCall> = mutableListOf()
    val copyCalls: MutableList<CopyCall> = mutableListOf()
    val timeCalls: MutableList<Pair<MealSlot, LocalTime>> = mutableListOf()

    override suspend fun mealTimes(): AppResult<MealTimes> {
        failure?.let { return AppResult.Failure(it) }
        return AppResult.Success(times)
    }

    override suspend fun setMealTime(slot: MealSlot, time: LocalTime): AppResult<MealTimes> {
        timeCalls += slot to time
        failure?.let { return AppResult.Failure(it) }
        // Coerce through the real domain, like the real repository does — so a test that writes
        // an out-of-range time sees the same clamped result the app would store.
        times = times.withTime(slot, time)
        return AppResult.Success(times)
    }

    override suspend fun planDay(date: LocalDate, today: LocalDate, now: LocalTime): AppResult<PlanDay> {
        failure?.let { return AppResult.Failure(it) }
        return AppResult.Success(
            buildPlanDay(
                date = date,
                isCurrentDay = date == today,
                now = now,
                times = times,
                entriesBySlot = entries[date].orEmpty(),
                doneSlots = doneSlots[date].orEmpty(),
                waterTicks = waterTicks[date].orEmpty(),
            ),
        )
    }

    override suspend fun setSlotDone(date: LocalDate, slot: MealSlot, done: Boolean): AppResult<Unit> {
        doneCalls += DoneCall(date, slot, done)
        failure?.let { return AppResult.Failure(it) }
        val set = doneSlots.getOrPut(date) { mutableSetOf() }
        if (done) set += slot else set -= slot
        return AppResult.Success(Unit)
    }

    override suspend fun setWaterDone(date: LocalDate, index: Int, done: Boolean): AppResult<Unit> {
        waterCalls += WaterCall(date, index, done)
        failure?.let { return AppResult.Failure(it) }
        val set = waterTicks.getOrPut(date) { mutableSetOf() }
        if (done) set += index else set -= index
        return AppResult.Success(Unit)
    }

    override suspend fun copyDayForward(from: LocalDate, to: List<LocalDate>): AppResult<Unit> {
        copyCalls += CopyCall(from, to)
        failure?.let { return AppResult.Failure(it) }
        val source = entries[from].orEmpty()
        to.forEach { target -> entries[target] = source }
        return AppResult.Success(Unit)
    }
}
