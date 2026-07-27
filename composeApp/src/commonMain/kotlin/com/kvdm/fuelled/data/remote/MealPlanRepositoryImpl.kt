package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.data.local.LogEntryEntity
import com.kvdm.fuelled.data.local.MealPlanDao
import com.kvdm.fuelled.data.local.TodayDao
import com.kvdm.fuelled.data.local.localTime
import com.kvdm.fuelled.data.local.mealSlot
import com.kvdm.fuelled.data.local.slotTimeEntity
import com.kvdm.fuelled.data.local.toDomain
import com.kvdm.fuelled.data.local.waterTickEntity
import com.kvdm.fuelled.data.suspendRunCatching
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.MealTimes
import com.kvdm.fuelled.domain.model.PlanDay
import com.kvdm.fuelled.domain.model.buildPlanDay
import com.kvdm.fuelled.domain.repository.MealPlanRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * The Room-backed structured day (specs/meal-plan.spec.md).
 *
 * Reads three small stored facts — slot times, done-ticks, water ticks — plus the day's log
 * rows, and hands them to the pure [buildPlanDay] to resolve. All the interesting behavior
 * (focus, lateness, missed-ness, water times, the veg count) lives in the domain, where it is
 * testable without a database; this class's whole job is fetching and mapping.
 *
 * Like every repository here it is the ONLY exception-translation point: each DAO call runs
 * inside `suspendRunCatching`, which maps infrastructure exceptions to typed `DomainError`
 * values and always rethrows `CancellationException` (ARCH-06).
 */
class MealPlanRepositoryImpl(
    private val planDao: MealPlanDao,
    private val todayDao: TodayDao,
) : MealPlanRepository {

    override suspend fun mealTimes(): AppResult<MealTimes> = suspendRunCatching {
        storedTimes()
    }

    /**
     * PLAN-06. The coercion is the DOMAIN's — [MealTimes.withTime] clamps into the window
     * between the neighbouring slots — and what gets stored is the coerced result, not the
     * requested one. Doing it this way round means no caller (a sheet, a test, a future import)
     * can write a timetable that runs backwards, whether or not it remembered to check.
     */
    override suspend fun setMealTime(slot: MealSlot, time: LocalTime): AppResult<MealTimes> =
        suspendRunCatching {
            val updated = storedTimes().withTime(slot, time)
            planDao.upsertSlotTime(slotTimeEntity(slot, updated[slot]))
            updated
        }

    override suspend fun planDay(date: LocalDate, today: LocalDate, now: LocalTime): AppResult<PlanDay> =
        suspendRunCatching {
            val key = date.toString()
            val entries = todayDao.entries(key)
            buildPlanDay(
                date = date,
                // PLAN-23: only the current logical day makes punctuality claims. A day being
                // planned ahead, or a day already gone, renders its containers and its ticks
                // and says nothing about lateness.
                isCurrentDay = date == today,
                now = now,
                times = storedTimes(),
                entriesBySlot = entries.groupBy({ it.mealSlot }, { it.toDomain() }),
                doneSlots = planDao.doneSlots(key).map { MealSlot.valueOf(it.slot) }.toSet(),
                waterTicks = planDao.waterTicks(key).map { it.waterIndex }.toSet(),
            )
        }

    override suspend fun setSlotDone(date: LocalDate, slot: MealSlot, done: Boolean): AppResult<Unit> =
        suspendRunCatching {
            if (done) {
                // PLAN-13 + PLAN-14 in one transaction: record the tick and log whatever was
                // planned in that container. An empty container matches no rows and stays empty.
                planDao.markSlotDone(date.toString(), slot.name)
            } else {
                // Un-ticking clears the completion only. The entries it logged stay LOGGED:
                // they were eaten, and the tick was the claim about the container, not about
                // the food. Reversing an accidental tap must not un-eat a meal.
                planDao.clearDoneSlot(date.toString(), slot.name)
            }
        }

    override suspend fun setWaterDone(date: LocalDate, index: Int, done: Boolean): AppResult<Unit> =
        suspendRunCatching {
            if (done) planDao.insertWaterTick(waterTickEntity(date, index))
            else planDao.clearWaterTick(date.toString(), index)
        }

    override suspend fun copyDayForward(from: LocalDate, to: List<LocalDate>): AppResult<Unit> =
        suspendRunCatching {
            val source = planDao.plannedEntries(from.toString())
            if (source.isEmpty()) return@suspendRunCatching
            planDao.insertCopiedEntries(
                to.flatMap { target ->
                    source.map { row -> row.copiedTo(target, from) }
                },
            )
        }

    /** Slot times as the domain sees them: stored where set, defaults everywhere else (PLAN-05). */
    private suspend fun storedTimes(): MealTimes =
        MealTimes(planDao.slotTimes().associate { it.mealSlot to it.localTime })

    private companion object {
        /**
         * One copied row (PLAN-20): same food, same slot, new day, fresh identity.
         *
         * The id follows the tray's own scheme — `<date>_<slot>_<foodId>` — so a copy-forward
         * is IDEMPOTENT for the same reason the tray's confirm is: running it twice replaces
         * the same rows instead of doubling tomorrow's dinner. A row whose id does not carry
         * the source date (hand-written, or from an older scheme) still gets a deterministic
         * target-day id rather than a random one.
         *
         * `status` is forced back to `PLANNED` rather than copied: a copy is a plan for a day
         * that has not happened, even when its source has since been eaten.
         */
        fun LogEntryEntity.copiedTo(target: LocalDate, source: LocalDate): LogEntryEntity {
            val sourcePrefix = source.toString()
            val newId = if (id.startsWith(sourcePrefix)) target.toString() + id.removePrefix(sourcePrefix)
            else "${target}_${slot}_$id"
            return copy(
                id = newId,
                logicalDate = target.toString(),
                status = com.kvdm.fuelled.domain.model.LogStatus.PLANNED.name,
            )
        }
    }
}
