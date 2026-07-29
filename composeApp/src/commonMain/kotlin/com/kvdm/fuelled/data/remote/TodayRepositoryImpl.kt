package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR
import com.kvdm.fuelled.core.time.logicalDate
import com.kvdm.fuelled.data.local.LogEntryEntity
import com.kvdm.fuelled.data.local.TodayDao
import com.kvdm.fuelled.data.local.TodayGoalEntity
import com.kvdm.fuelled.data.local.logStatus
import com.kvdm.fuelled.data.local.mealSlot
import com.kvdm.fuelled.data.local.toDomain
import com.kvdm.fuelled.data.local.toEntity
import com.kvdm.fuelled.data.suspendRunCatching
import com.kvdm.fuelled.domain.model.LogStatus
import com.kvdm.fuelled.domain.model.MacroProgress
import com.kvdm.fuelled.domain.model.MealGroup
import com.kvdm.fuelled.domain.model.MealSlot
import com.kvdm.fuelled.domain.model.NewLogEntry
import com.kvdm.fuelled.domain.model.TodayModel
import com.kvdm.fuelled.domain.repository.TodayRepository
import com.kvdm.fuelled.domain.result.AppResult
import com.kvdm.fuelled.core.time.RealTimeSignal
import com.kvdm.fuelled.core.time.TimeSignal
import com.kvdm.fuelled.core.time.days
import com.kvdm.fuelled.data.asAppResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * The Room-backed Today dashboard and meal-log write path — the fully-wired data source.
 * Reads the flat [TodayDao] rows (one goal + many log entries) and AGGREGATES them into the
 * domain [TodayModel]: groups the day's entries by slot IN SLOT ORDER, totals each meal and
 * the day, and computes each macro's current against the goal's target. Seeds a realistic
 * sample day on first run so the app has content offline from install (idempotent).
 *
 * **The day in view is derived, never stored (MEAL-02/TODAY-01).** Every read computes
 * `logicalDate(clock.now(), dayStartHour, zone)` afresh, so the day rolls over by
 * re-derivation when the app returns to the foreground — there is no boundary row and no
 * scheduled job that rewrites entries. The clock, zone, and `dayStartHour` are constructor
 * parameters with production defaults so tests can drive the boundary.
 *
 * **Consumed is `LOGGED`-only (TODAY-03).** A `PLANNED` entry is still returned in its meal
 * group — it is scheduled and the tray wrote it — but it contributes to neither the calorie
 * ring nor any macro's current until it is marked logged (MEAL-07).
 *
 * The repository is the ONLY exception-translation point: every DAO call runs inside
 * suspendRunCatching (data/AppResultCatching.kt), which maps infrastructure exceptions to
 * typed DomainError values and ALWAYS rethrows CancellationException. Seed data lives here in
 * the data layer (ARCH-09) — it never reaches for the presentation layer's preview fixtures.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayRepositoryImpl(
    private val dao: TodayDao,
    private val time: TimeSignal = RealTimeSignal(),
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val dayStartHour: Int = DEFAULT_DAY_START_HOUR,
) : TodayRepository {

    /**
     * Two independent change sources, combined:
     *
     * - `time.days(...)` re-emits the moment the LOGICAL DAY changes (04:00, or a wake after
     *   the device slept). `flatMapLatest` tears down the previous day's queries and starts
     *   the new day's, so the dashboard follows the boundary instead of holding the day it
     *   launched in.
     * - the two Room streams re-emit on every write to their tables, which is what carries a
     *   meal added in the tray straight to this dashboard.
     *
     * `onStart { ensureSeeded() }` runs once per collection BEFORE the goal stream is read,
     * so the ring has a target on a first run; the seeded write then flows back through
     * `goalStream()` like any other. A missing goal is mapped to a typed failure rather than
     * thrown, because a stream that throws kills the collection for good — the screen would
     * need a relaunch, which is the staleness this whole change exists to remove.
     */
    override fun observeTodaySummary(): Flow<AppResult<TodayModel>> =
        time.days(dayStartHour, zone)
            .flatMapLatest { dayInView ->
                combine(dao.goalStream(), dao.entriesStream(dayInView.toString())) { goal, rows ->
                    goal?.let { aggregate(dayInView, it, rows) }
                }
            }
            .onStart { ensureSeeded() }
            .map { it ?: throw IllegalStateException("today goal row missing after seeding") }
            .asAppResult()

    /**
     * MEAL-05: one transaction, all-or-nothing. The rows are built first and handed to the
     * DAO's single atomic call — never inserted one by one from here, which would leave a
     * half-written meal behind on the failing item.
     */
    override suspend fun addEntries(
        entries: List<NewLogEntry>,
        date: LocalDate,
        slot: MealSlot,
        status: LogStatus,
    ): AppResult<Unit> = suspendRunCatching {
        // Append after whatever is already in this (day, slot) so the tray's items keep their
        // order behind the existing ones. Single-user local storage: no contending writer.
        val firstOrder = dao.maxEntryOrder(date.toString(), slot.name) + 1
        dao.insertEntriesAtomically(
            entries.mapIndexed { index, entry ->
                entry.toEntity(date, slot, status, firstOrder + index)
            },
        )
    }

    override suspend fun deleteEntry(id: String): AppResult<Unit> = suspendRunCatching {
        dao.deleteEntry(id)
    }

    override suspend fun markEntryLogged(id: String): AppResult<Unit> = suspendRunCatching {
        dao.setStatus(id, LogStatus.LOGGED.name)
    }

    /** The logical day right now — a one-shot for WRITES only; reads observe (MEAL-02). */
    private fun currentLogicalDay(): LocalDate = logicalDate(time.now(), dayStartHour, zone)

    /**
     * Fold the flat rows into the aggregate.
     *
     * Groups come out in `MealSlot` declaration order (MEAL-03/TODAY-03) by walking the enum
     * rather than the rows: the ordinal is the ONE source of slot order, so reordering the
     * enum reorders the screen and nothing has to be kept in sync. Entries inside a group keep
     * the DAO's `entryOrder`.
     *
     * The day's consumed total and every macro's current sum the `LOGGED` rows only — a
     * `PLANNED` entry appears in its group but is not eaten (TODAY-02/TODAY-03/MEAL-08).
     */
    private fun aggregate(date: LocalDate, goal: TodayGoalEntity, rows: List<LogEntryEntity>): TodayModel {
        val bySlot = rows.groupBy { it.mealSlot }
        val meals = MealSlot.entries.mapNotNull { slot ->
            bySlot[slot]?.let { slotRows -> MealGroup(slot = slot, entries = slotRows.map { it.toDomain() }) }
        }
        val consumed = rows.filter { it.logStatus == LogStatus.LOGGED }
        return TodayModel(
            date = date,
            consumedKcal = consumed.sumOf { it.kcal },
            targetKcal = goal.targetKcal,
            protein = MacroProgress("Protein", consumed.sumOf { it.proteinG }, goal.proteinTargetG, "g"),
            carbs = MacroProgress("Carbs", consumed.sumOf { it.carbsG }, goal.carbsTargetG, "g"),
            fat = MacroProgress("Fat", consumed.sumOf { it.fatG }, goal.fatTargetG, "g"),
            meals = meals,
        )
    }

    /**
     * Seed the day's GOAL on first run so the ring has a target offline (idempotent). No log
     * entries are seeded: every day starts empty and is planned by its owner (PLAN-03). The
     * goal is a setting, not a pretence of having eaten — which is why it stayed when the
     * starter entries went.
     */
    private suspend fun ensureSeeded() {
        if (dao.goalCount() == 0) {
            dao.upsertGoal(SEED_GOAL)
        }
    }

    private companion object {
        // The starter day, seeded once into Room. Lives in the data layer (the source owns its
        // seed data, ARCH-09); the presentation layer keeps its own preview fixture separately.
        val SEED_GOAL = TodayGoalEntity(
            id = "current",
            targetKcal = 2400,
            proteinTargetG = 180,
            carbsTargetG = 260,
            fatTargetG = 70,
        )

        // `seedEntries` lived here: a starter day of six logged foods, written on first run.
        // PLAN-03 removes it — every day now begins empty, with its six containers waiting to
        // be planned. The FOODS catalog seed stays (the tray needs foods to pick from); what
        // went is the pretence that the user had already eaten.
    }
}
