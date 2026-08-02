package com.kvdm.fuelled.domain.model

import kotlinx.datetime.LocalDate

/**
 * A goal and the day it took effect (GOAL-01).
 *
 * Goals used to be one row that got overwritten, and `WeekDay`'s contract carried the caveat:
 * targets are the CURRENT goals. That was honest while history was seven days long. Four weeks
 * of trend (HIST-05) made it a defect — cut your target from 2600 to 2400 and the fortnight
 * you spent deliberately eating 2600, and hitting it, re-scored overnight as a fortnight you
 * overshot.
 */
data class DatedGoal(
    val effectiveFrom: LocalDate,
    val targetKcal: Int,
    val proteinGoalG: Int,
)

/**
 * GOAL-01: the goal in force on [date] — the latest one starting on or before it.
 *
 * The EARLIEST goal also answers for every day before itself: a day logged before anyone
 * opened the goal editor is judged against the seeded default rather than against nothing,
 * because "no applicable goal" renders as `0 / 0 kcal`, which is its own kind of lie
 * (decision D3). Null only when there are no goals at all — a state seeding rules out.
 */
fun List<DatedGoal>.goalOn(date: LocalDate): DatedGoal? =
    filter { it.effectiveFrom <= date }.maxByOrNull { it.effectiveFrom }
        ?: minByOrNull { it.effectiveFrom }
