package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.core.time.systemZone
import com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR
import com.kvdm.fuelled.core.time.RealTimeSignal
import com.kvdm.fuelled.core.time.TimeSignal
import com.kvdm.fuelled.core.time.currentDay
import com.kvdm.fuelled.core.time.days
import com.kvdm.fuelled.core.time.parseIsoDateOrNull
import com.kvdm.fuelled.data.asAppResult
import com.kvdm.fuelled.data.local.WorkoutDao
import com.kvdm.fuelled.data.local.toEntity
import com.kvdm.fuelled.data.local.toEntities
import com.kvdm.fuelled.data.local.toWeek
import com.kvdm.fuelled.data.local.workoutDoneEntity
import com.kvdm.fuelled.data.suspendRunCatching
import com.kvdm.fuelled.domain.model.WorkoutDay
import com.kvdm.fuelled.domain.model.WorkoutDayPlan
import com.kvdm.fuelled.domain.model.WorkoutWeek
import com.kvdm.fuelled.domain.repository.WorkoutRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

/**
 * The Room-backed training week and its done-marks (WORK-01).
 *
 * **The plan is stored; due-ness and doneness are derived.** The week says which weekdays are
 * training days; whether TODAY is one is `week[today.dayOfWeek].isTraining`, computed on every
 * read. There is no "today's workout" row to create at midnight and nothing to go stale when
 * the app is opened after a fortnight away — the same discipline as the meal-plan grid coming
 * from the enum (PLAN-02) and the logical day being re-derived rather than rolled (MEAL-02).
 *
 * **The day in view is derived, never stored (MEAL-02).** The read streams re-derive it from
 * [time], so a screen left open across 04:00 rolls over on its own. A WRITE reads the clock
 * once instead: which day a tap belongs to is decided at the tap.
 *
 * The repository is the ONLY exception-translation point: every DAO call runs inside
 * suspendRunCatching, which maps infrastructure exceptions to typed DomainError values and
 * ALWAYS rethrows CancellationException. Seed data lives here in the data layer (ARCH-09).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutRepositoryImpl(
    private val dao: WorkoutDao,
    private val time: TimeSignal = RealTimeSignal(),
    private val zone: TimeZone = systemZone(),
    private val dayStartHour: Int = DEFAULT_DAY_START_HOUR,
) : WorkoutRepository {

    override suspend fun week(): AppResult<WorkoutWeek> = suspendRunCatching {
        ensureSeeded()
        dao.week().toWeek()
    }

    override fun observeWeek(): Flow<AppResult<WorkoutWeek>> =
        dao.weekStream()
            .map { it.toWeek() }
            .onStart { ensureSeeded() }
            .asAppResult()

    override suspend fun saveDay(day: DayOfWeek, plan: WorkoutDayPlan): AppResult<Unit> =
        suspendRunCatching {
            ensureSeeded()
            dao.upsertDay(plan.toEntity(day))
        }

    override fun observeToday(): Flow<AppResult<WorkoutDay>> =
        time.days(dayStartHour, zone)
            .flatMapLatest { day ->
                combine(
                    dao.weekStream(),
                    dao.doneBetweenStream(day.toString(), day.toString()),
                ) { week, done ->
                    WorkoutDay(
                        date = day,
                        plan = week.toWeek()[day.dayOfWeek],
                        done = done.isNotEmpty(),
                    )
                }
            }
            .onStart { ensureSeeded() }
            .asAppResult()

    override suspend fun setDone(done: Boolean): AppResult<Unit> = suspendRunCatching {
        val today = time.currentDay(dayStartHour, zone)
        if (done) dao.insertDone(workoutDoneEntity(today)) else dao.clearDone(today.toString())
    }

    override fun observeRange(from: LocalDate, to: LocalDate): Flow<AppResult<List<WorkoutDay>>> =
        combine(
            dao.weekStream(),
            dao.doneBetweenStream(from.toString(), to.toString()),
        ) { weekRows, doneRows ->
            val week = weekRows.toWeek()
            val done = doneRows.map { it.logicalDate }.toSet()
            // Every date in the window, whether or not it has a row — the strip is a GRID, and
            // a day missing from a collection is indistinguishable from a rest day.
            generateSequence(from) { previous ->
                previous.plus(1, DateTimeUnit.DAY).takeIf { it <= to }
            }.map { date ->
                WorkoutDay(
                    date = date,
                    plan = week[date.dayOfWeek],
                    done = date.toString() in done,
                )
            }.toList()
        }
            .onStart { ensureSeeded() }
            .asAppResult()

    override suspend fun doneBetween(from: LocalDate, to: LocalDate): AppResult<Set<LocalDate>> =
        suspendRunCatching {
            dao.doneBetween(from.toString(), to.toString())
                .mapNotNull { row -> parseIsoDateOrNull(row.logicalDate) }
                .toSet()
        }

    /**
     * Seed the classic split on first run so the app ships with a real week (WORK-08).
     *
     * Idempotent, and keyed on the table being EMPTY rather than on a flag: a user who deletes
     * every label has a week of seven rest days, which is seven rows, so this never overwrites
     * a deliberate choice with the default.
     */
    private suspend fun ensureSeeded() {
        if (dao.count() == 0) dao.upsertWeek(WorkoutWeek.DEFAULT.toEntities())
    }
}
