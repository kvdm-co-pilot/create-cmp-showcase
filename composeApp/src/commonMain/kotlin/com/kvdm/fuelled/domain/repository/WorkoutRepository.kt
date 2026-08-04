package com.kvdm.fuelled.domain.repository

import com.kvdm.fuelled.domain.model.WorkoutDay
import com.kvdm.fuelled.domain.model.WorkoutDayPlan
import com.kvdm.fuelled.domain.model.WorkoutWeek
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * Domain-facing contract for training (WORK-01). Presentation depends on THIS, never on the
 * concrete Room-backed source. One-shot operations return AppResult — they never throw
 * (ARCH-06): failures cross the boundary as typed DomainError values, translated inside the
 * data implementation.
 *
 * Two nouns, deliberately: the WEEK is the plan (what you intend to train, and when to be
 * reminded), and a DAY is a fact (you did it, on this logical date). Keeping the fact out of
 * the plan is what lets the plan change without rewriting history — edit Wednesday from Lower
 * body to Rest and last Wednesday is still a session you did.
 */
interface WorkoutRepository {

    /** The training week, seeded to the classic split on first run (WORK-08). */
    suspend fun week(): AppResult<WorkoutWeek>

    /** The week as a stream — editing a day in Settings re-renders Today without a reload. */
    fun observeWeek(): Flow<AppResult<WorkoutWeek>>

    /** WORK-07: set one day of the week — its label, its time, its rungs. */
    suspend fun saveDay(day: DayOfWeek, plan: WorkoutDayPlan): AppResult<Unit>

    /**
     * The current logical day's session, plan and done-mark joined (WORK-03). Observed, so the
     * card follows a tick made anywhere and rolls over at the day boundary on its own.
     */
    fun observeToday(): Flow<AppResult<WorkoutDay>>

    /** WORK-04: mark the current logical day's session done, or undo it. Persists per day. */
    suspend fun setDone(done: Boolean): AppResult<Unit>

    /**
     * The days from [from] to [to] inclusive, each with its plan and done-mark (WORK-05).
     * Feeds the week strip and the day cards from ONE read — never a second aggregate query
     * (HIST-01's rule).
     */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<AppResult<List<WorkoutDay>>>

    /**
     * Which dates in [from]..[to] are marked done (NOTIF-08/WORK-06).
     *
     * The arm path needs it to skip a session already finished, and DELIVERY needs it to ask
     * again at the moment of firing — an alarm set last night knows nothing about the session
     * done this morning.
     */
    suspend fun doneBetween(from: LocalDate, to: LocalDate): AppResult<Set<LocalDate>>
}
