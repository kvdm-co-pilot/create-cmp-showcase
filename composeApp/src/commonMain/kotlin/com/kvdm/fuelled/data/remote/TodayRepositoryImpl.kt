package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.data.local.LogEntryEntity
import com.kvdm.fuelled.data.local.TodayDao
import com.kvdm.fuelled.data.local.TodayGoalEntity
import com.kvdm.fuelled.data.local.toDomain
import com.kvdm.fuelled.data.suspendRunCatching
import com.kvdm.fuelled.domain.model.MacroProgress
import com.kvdm.fuelled.domain.model.MealGroup
import com.kvdm.fuelled.domain.model.TodayModel
import com.kvdm.fuelled.domain.repository.TodayRepository
import com.kvdm.fuelled.domain.result.AppResult

/**
 * The Room-backed Today dashboard — the fully-wired data source. Reads the flat [TodayDao]
 * rows (one goal + many log entries) and AGGREGATES them into the domain [TodayModel]:
 * groups entries by meal in order, totals each meal and the day, and computes each macro's
 * current against the goal's target. Seeds a realistic sample day on first run so the app
 * has content offline from install (idempotent).
 *
 * The repository is the ONLY exception-translation point: every DAO call runs inside
 * suspendRunCatching (data/AppResultCatching.kt), which maps infrastructure exceptions to
 * typed DomainError values and ALWAYS rethrows CancellationException. Seed data lives here in
 * the data layer (ARCH-09) — it never reaches for the presentation layer's preview fixtures.
 */
class TodayRepositoryImpl(
    private val dao: TodayDao,
) : TodayRepository {

    override suspend fun getTodaySummary(): AppResult<TodayModel> = suspendRunCatching {
        ensureSeeded()
        val goal = dao.goal() ?: error("today goal row missing after seeding")
        aggregate(goal, dao.entries())
    }

    /**
     * Fold the flat rows into the aggregate. Entries arrive meal-grouped and ordered from the
     * DAO, so `groupBy` preserves meal order (first-seen key order). The day's consumed total
     * is the sum of every entry's calories (TODAY-03); each macro's current is the sum of that
     * macro across every entry, against the goal's target (TODAY-02).
     */
    private fun aggregate(goal: TodayGoalEntity, rows: List<LogEntryEntity>): TodayModel {
        val meals = rows
            .groupBy { it.meal }
            .map { (meal, entries) -> MealGroup(name = meal, entries = entries.map { it.toDomain() }) }
        return TodayModel(
            dateLabel = goal.dateLabel,
            consumedKcal = rows.sumOf { it.kcal },
            targetKcal = goal.targetKcal,
            protein = MacroProgress("Protein", rows.sumOf { it.proteinG }, goal.proteinTargetG, "g"),
            carbs = MacroProgress("Carbs", rows.sumOf { it.carbsG }, goal.carbsTargetG, "g"),
            fat = MacroProgress("Fat", rows.sumOf { it.fatG }, goal.fatTargetG, "g"),
            meals = meals,
        )
    }

    /** Seed a realistic day on first run so the dashboard ships with content offline (idempotent). */
    private suspend fun ensureSeeded() {
        if (dao.goalCount() == 0) {
            dao.upsertGoal(SEED_GOAL)
            dao.upsertEntries(SEED_ENTRIES)
        }
    }

    private companion object {
        // The starter day, seeded once into Room. Lives in the data layer (the source owns its
        // seed data, ARCH-09); the presentation layer keeps its own preview fixture separately.
        val SEED_GOAL = TodayGoalEntity(
            id = "current",
            dateLabel = "Wednesday, Jul 23",
            targetKcal = 2400,
            proteinTargetG = 180,
            carbsTargetG = 260,
            fatTargetG = 70,
        )
        val SEED_ENTRIES = listOf(
            LogEntryEntity("b1", "Breakfast", 0, 0, "Rolled oats & whey", "80 g · 1 scoop", 430, 38, 52, 9),
            LogEntryEntity("b2", "Breakfast", 0, 1, "Banana", "1 medium", 105, 1, 27, 0),
            LogEntryEntity("l1", "Lunch", 1, 0, "Chicken breast & rice", "200 g · 150 g", 620, 58, 68, 8),
            LogEntryEntity("l2", "Lunch", 1, 1, "Mixed greens", "1 bowl", 90, 3, 11, 4),
            LogEntryEntity("s1", "Snack", 2, 0, "Greek yogurt 0%", "170 g", 100, 17, 6, 0),
            LogEntryEntity("s2", "Snack", 2, 1, "Almonds", "20 g", 116, 4, 4, 10),
        )
    }
}
