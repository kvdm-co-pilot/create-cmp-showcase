package com.kvdm.fuelled.domain.repository

import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.MealTimes
import com.kvdm.fuelled.domain.model.PlanDay
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Domain-facing contract for the structured day (specs/meal-plan.spec.md). Presentation depends
 * on THIS, never on the Room-backed source. Every operation returns `AppResult` and never throws
 * (ARCH-06).
 *
 * Note what is missing: there is no `setWaterTime`, no `setFocus`, no `markMissed`. Water times
 * are midpoints of the meal times (PLAN-09) and focus/lateness/missed-ness are functions of now
 * (PLAN-15/PLAN-19) — offering a setter for any of them would create a second truth that could
 * disagree with the first.
 */
interface MealPlanRepository {

    /** The six slot times — stored where set, Body-for-LIFE defaults everywhere else (PLAN-05). */
    suspend fun mealTimes(): AppResult<MealTimes>

    /**
     * Move one slot to [time] (PLAN-06). The domain coerces it between its neighbours, so a
     * caller cannot store a timetable that runs backwards; the coerced value is what comes back
     * in the next [mealTimes]. No other slot moves, and the day's water re-derives for free.
     */
    suspend fun setMealTime(slot: MealSlot, time: LocalTime): AppResult<MealTimes>

    /**
     * One day, fully resolved (PLAN-02). [today] is the current logical day, passed in so the
     * repository can decide whether [date] gets punctuality claims at all (PLAN-23) without
     * every caller re-deriving the boundary.
     */
    suspend fun planDay(date: LocalDate, today: LocalDate, now: LocalTime): AppResult<PlanDay>

    /**
     * One day's plan as a STREAM, re-emitted on every input that can change it:
     *
     * - the day's log rows (a meal added in the tray),
     * - its done-ticks and water ticks (ticked here or on Today — either surface moves both),
     * - the slot times (the times sheet re-derives every water midpoint),
     * - and the clock, each minute, because focus / LATE / MISSED are time-derived and a
     *   screen that read the clock once announces a lateness that stopped being true.
     *
     * `today` and `now` are NOT parameters here: taking them as arguments is what froze them.
     * They come from the injected time signal, per emission.
     */
    fun observePlanDay(date: LocalDate): Flow<AppResult<PlanDay>>

    /**
     * Tick a slot done (PLAN-13/PLAN-14): its `PLANNED` entries become `LOGGED` in the same
     * transaction, and an empty container records the completion without fabricating a food.
     */
    suspend fun setSlotDone(date: LocalDate, slot: MealSlot, done: Boolean): AppResult<Unit>

    /** Tick a water container (PLAN-10). Per day, so a new logical day starts at 0.0 L. */
    suspend fun setWaterDone(date: LocalDate, index: Int, done: Boolean): AppResult<Unit>

    /**
     * Copy [from]'s planned entries onto [to] as fresh `PLANNED` entries (PLAN-20) — a prepped
     * week is one day built by hand and one copy. The source day is unchanged, and every copy
     * gets its own identity so deleting tomorrow's chicken does not delete today's.
     */
    suspend fun copyDayForward(from: LocalDate, to: List<LocalDate>): AppResult<Unit>
}
