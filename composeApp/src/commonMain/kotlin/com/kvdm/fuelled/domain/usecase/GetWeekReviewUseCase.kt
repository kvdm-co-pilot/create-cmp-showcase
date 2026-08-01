package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.PlanDay
import com.kvdm.fuelled.domain.model.TodayModel
import com.kvdm.fuelled.domain.model.WEEK_REVIEW_DAYS
import com.kvdm.fuelled.domain.model.WeekReview
import com.kvdm.fuelled.domain.model.weekDayOf
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * The week in review, OBSERVED (JRN-01): the last [WEEK_REVIEW_DAYS] logical days ending
 * with the current one, each folded from the same [PlanDay] derivation the plan screen and
 * Today read, with targets from the same observed summary the ring reads.
 *
 * Composed from the existing use cases rather than a new repository read — the week is a
 * projection over data other contracts already own, and a second read path is exactly what
 * TODAY-13 exists to forbid. The outer `flatMapLatest` on the logical day means the window
 * itself rolls at the day boundary (RS-02): a review left open across 04:00 re-derives as
 * the new week, not yesterday's.
 *
 * Any failing source fails the whole week with its own error — a review with silently
 * missing days would read as "you didn't log", which is worse than an honest error
 * (the observed stream heals it on the next emission, RS-01).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetWeekReviewUseCase(
    private val getPlanDay: GetPlanDayUseCase,
    private val getTodaySummary: GetTodaySummaryUseCase,
) {
    operator fun invoke(): Flow<AppResult<WeekReview>> =
        getPlanDay.currentLogicalDay().flatMapLatest { today ->
            val dates = ((WEEK_REVIEW_DAYS - 1) downTo 0).map { today.minus(it, DateTimeUnit.DAY) }
            val dayFlows = dates.map { getPlanDay(it) }
            combine(dayFlows + listOf(getTodaySummary())) { results ->
                fold(dates, today, results)
            }
        }

    private fun fold(
        dates: List<LocalDate>,
        today: LocalDate,
        results: Array<*>,
    ): AppResult<WeekReview> {
        // Any source's failure is the week's failure — never a silently short week.
        results.filterIsInstance<AppResult.Failure>().firstOrNull()?.let { return it }

        @Suppress("UNCHECKED_CAST")
        val days = results.dropLast(1).map { (it as AppResult.Success<PlanDay>).value }
        val summary = (results.last() as AppResult.Success<TodayModel>).value

        return AppResult.Success(
            WeekReview(
                days = days.mapIndexed { i, plan ->
                    weekDayOf(
                        plan = plan,
                        isToday = dates[i] == today,
                        targetKcal = summary.targetKcal,
                        proteinGoalG = summary.protein.target,
                    )
                },
            ),
        )
    }
}
