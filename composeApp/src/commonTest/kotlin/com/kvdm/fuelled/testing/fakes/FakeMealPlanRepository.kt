package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.LogEntry
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.MealTimes
import com.kvdm.fuelled.domain.model.PlanDay
import com.kvdm.fuelled.domain.model.buildPlanDay
import com.kvdm.fuelled.domain.repository.MealPlanRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import com.kvdm.fuelled.testing.fakes.FakeTimeSignal

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
class FakeMealPlanRepository(
    /**
     * The signal `observePlanDay` derives `today` and `now` from — the same substitution the
     * real repository takes, so a test drives the day boundary and the LATE threshold here too
     * rather than racing the wall clock.
     */
    private val time: FakeTimeSignal? = null,
    private val zone: kotlinx.datetime.TimeZone = kotlinx.datetime.TimeZone.UTC,
    private val dayStartHour: Int = com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR,
) : MealPlanRepository {

    var times: MealTimes = MealTimes()
        set(value) { field = value; revision.value += 1 }
    var failure: DomainError? = null
        set(value) { field = value; revision.value += 1 }

    /**
     * Fails WRITES only, leaving reads healthy — the RS-04 case: a tick that cannot persist
     * against a day that still renders. `failure` poisons both directions, which cannot
     * distinguish "the write failed" from "the screen lost its data".
     */
    var writeFailure: DomainError? = null

    /** Bump after mutating [entries] / [doneSlots] / [waterTicks] directly in a test. */
    val revision = MutableStateFlow(0)

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

    /**
     * The observable read. Emits on [revision] (any stored change a test makes) AND on the
     * time signal's ticks when one is supplied — the two reasons the real answer moves.
     */
    override fun observePlanDay(date: LocalDate): Flow<AppResult<PlanDay>> {
        val ticks = time?.ticks
        return if (ticks == null) {
            revision.map { planDayNow(date, null) }
        } else {
            kotlinx.coroutines.flow.combine(revision, ticks) { _, instant -> planDayNow(date, instant) }
        }
    }

    private fun planDayNow(date: LocalDate, instant: kotlin.time.Instant?): AppResult<PlanDay> {
        failure?.let { return AppResult.Failure(it) }
        val today = instant?.let { com.kvdm.fuelled.core.time.logicalDate(it, dayStartHour, zone) } ?: date
        val now = instant?.let { it.toLocalDateTime(zone).time } ?: LocalTime(0, 0)
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

    override suspend fun mealTimes(): AppResult<MealTimes> {
        failure?.let { return AppResult.Failure(it) }
        return AppResult.Success(times)
    }

    override suspend fun setMealTime(slot: MealSlot, time: LocalTime): AppResult<MealTimes> {
        timeCalls += slot to time
        failure?.let { return AppResult.Failure(it) }
        // Coerce through the real domain, like the real repository does — so a test that writes
        // an out-of-range time sees the same clamped result the app would store. The `times`
        // setter bumps `revision` AFTER the store, like Room's invalidation tracker fires after
        // the transaction commits — a bump BEFORE the mutation lets an immediate-dispatch
        // collector observe pre-write state and never hear about the write itself.
        times = times.withTime(slot, time) // the setter bumps `revision` after the store
        delay(1) // see `committed`: the emission lands during the write's suspension
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

    // Every write mutates, THEN bumps `revision`, THEN genuinely suspends — the shape of a
    // real Room write, faked honestly:
    //
    // - Bump AFTER the mutation, never before: Room's invalidation tracker fires after the
    //   transaction commits. A bump-first fake, under an immediate dispatcher (Main.immediate
    //   in the Compose UI tests), runs the collector synchronously INSIDE the bump: it re-reads
    //   pre-write state, and the actual mutation never gets an emission — the exact staleness
    //   this refactor exists to kill, injected by the fake.
    // - [committed] suspends after the bump: a real write crosses into the database and back,
    //   and the invalidation emission lands during that suspension — which is the ordering the
    //   ViewModels' post-write reads (the re-arm's done set, PLAN-07) depend on. A fake that
    //   returned without suspending would hand the caller pre-emission state under a deferred
    //   test dispatcher, an artifact no production dispatcher produces.
    private suspend fun committed(): AppResult<Unit> {
        revision.value += 1
        delay(1)
        return AppResult.Success(Unit)
    }

    override suspend fun setSlotDone(date: LocalDate, slot: MealSlot, done: Boolean): AppResult<Unit> {
        doneCalls += DoneCall(date, slot, done)
        failure?.let { return AppResult.Failure(it) }
        writeFailure?.let { return AppResult.Failure(it) }
        val set = doneSlots.getOrPut(date) { mutableSetOf() }
        if (done) set += slot else set -= slot
        return committed()
    }

    override suspend fun setWaterDone(date: LocalDate, index: Int, done: Boolean): AppResult<Unit> {
        waterCalls += WaterCall(date, index, done)
        failure?.let { return AppResult.Failure(it) }
        writeFailure?.let { return AppResult.Failure(it) }
        val set = waterTicks.getOrPut(date) { mutableSetOf() }
        if (done) set += index else set -= index
        return committed()
    }

    override suspend fun copyDayForward(from: LocalDate, to: List<LocalDate>): AppResult<Unit> {
        copyCalls += CopyCall(from, to)
        failure?.let { return AppResult.Failure(it) }
        val source = entries[from].orEmpty()
        to.forEach { target -> entries[target] = source }
        return committed()
    }
}
