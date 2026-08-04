package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.WorkoutDay
import com.kvdm.fuelled.domain.model.WorkoutDayPlan
import com.kvdm.fuelled.domain.model.WorkoutWeek
import com.kvdm.fuelled.domain.repository.WorkoutRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Hand-written fake — the template's testing convention (no mocking frameworks). Follows the
 * FakeSupplementRepository pattern: configurable behaviour (`week`, `done`, `failure`),
 * recorded interactions (`saves`, `lastSetDone`), implements the DOMAIN interface, and returns
 * typed [AppResult.Failure] — it never throws (repositories don't, per ARCH-06).
 *
 * [today] is injected rather than read from a clock: `setDone` has to record the fact against
 * SOME logical day, and a fake that read the real clock would make every test that asserts on
 * a date fail at 04:00 (MEAL-01's boundary).
 */
class FakeWorkoutRepository(
    var today: LocalDate = LocalDate(2026, 8, 4),
) : WorkoutRepository {

    var week: WorkoutWeek = WorkoutWeek.DEFAULT
        set(value) { field = value; revision.value += 1 }
    var done: Set<LocalDate> = emptySet()
        set(value) { field = value; revision.value += 1 }
    var failure: DomainError? = null
        set(value) { field = value; revision.value += 1 }

    /** Observable like the real one: any change re-emits, so a collector sees it. */
    private val revision = MutableStateFlow(0)

    val saves: MutableList<Pair<DayOfWeek, WorkoutDayPlan>> = mutableListOf()
    var lastSetDone: Boolean? = null
        private set

    override suspend fun week(): AppResult<WorkoutWeek> =
        failure?.let { AppResult.Failure(it) } ?: AppResult.Success(week)

    override fun observeWeek(): Flow<AppResult<WorkoutWeek>> =
        revision.map { failure?.let { AppResult.Failure(it) } ?: AppResult.Success(week) }

    override suspend fun saveDay(day: DayOfWeek, plan: WorkoutDayPlan): AppResult<Unit> {
        saves += day to plan
        failure?.let { return AppResult.Failure(it) }
        week = WorkoutWeek(week.days + (day to plan))
        return AppResult.Success(Unit)
    }

    override fun observeToday(): Flow<AppResult<WorkoutDay>> = revision.map {
        failure?.let { return@map AppResult.Failure(it) }
        AppResult.Success(WorkoutDay(today, week[today.dayOfWeek], today in done))
    }

    override suspend fun setDone(done: Boolean): AppResult<Unit> {
        lastSetDone = done
        failure?.let { return AppResult.Failure(it) }
        this.done = if (done) this.done + today else this.done - today
        return AppResult.Success(Unit)
    }

    override fun observeRange(from: LocalDate, to: LocalDate): Flow<AppResult<List<WorkoutDay>>> =
        revision.map {
            failure?.let { return@map AppResult.Failure(it) }
            AppResult.Success(
                generateSequence(from) { it.plus(1, DateTimeUnit.DAY).takeIf { d -> d <= to } }
                    .map { date -> WorkoutDay(date, week[date.dayOfWeek], date in done) }
                    .toList(),
            )
        }

    override suspend fun doneBetween(from: LocalDate, to: LocalDate): AppResult<Set<LocalDate>> =
        failure?.let { AppResult.Failure(it) }
            ?: AppResult.Success(done.filter { it in from..to }.toSet())
}
