package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.History
import com.kvdm.fuelled.domain.model.PlanDay
import com.kvdm.fuelled.domain.model.TREND_DAYS
import com.kvdm.fuelled.domain.model.TodayModel
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
 * The look back, OBSERVED (HIST-01) — [TREND_DAYS] logical days ending with the current one,
 * each folded from the same [PlanDay] derivation the plan screen and Today read, with targets
 * from the same observed summary the ring reads.
 *
 * **One stream, two projections.** This replaced `GetWeekReviewUseCase` rather than sitting
 * beside it: the seven-day verdict is now `History.week` and the four-week trend is
 * `History.weeks`, both folds of this one list. A separate aggregate query for the trend
 * would have been faster and would have been a second source of truth for numbers the day
 * cards already state — and when two such sources disagree, nothing in the app can say which
 * one is lying (history decision D5, TODAY-13's no-second-path discipline).
 *
 * The outer `flatMapLatest` on the logical day means the window itself rolls at the day
 * boundary (RS-02): a Progress screen left open across 04:00 re-derives as the new window.
 *
 * Any failing source fails the whole history with its own error — silently missing days would
 * read as "you didn't log", which is worse than an honest error (the observed stream heals it
 * on the next emission, RS-01).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetHistoryUseCase(
    private val getPlanDay: GetPlanDayUseCase,
    private val getTodaySummary: GetTodaySummaryUseCase,
) {
    operator fun invoke(days: Int = TREND_DAYS): Flow<AppResult<History>> =
        getPlanDay.currentLogicalDay().flatMapLatest { today ->
            val dates = ((days - 1) downTo 0).map { today.minus(it, DateTimeUnit.DAY) }
            val dayFlows = dates.map { getPlanDay(it) }
            combine(dayFlows + listOf(getTodaySummary())) { results ->
                fold(dates, today, results)
            }
        }

    private fun fold(
        dates: List<LocalDate>,
        today: LocalDate,
        results: Array<*>,
    ): AppResult<History> {
        // Any source's failure is the history's failure — never a silently short window.
        results.filterIsInstance<AppResult.Failure>().firstOrNull()?.let { return it }

        @Suppress("UNCHECKED_CAST")
        val days = results.dropLast(1).map { (it as AppResult.Success<PlanDay>).value }
        val summary = (results.last() as AppResult.Success<TodayModel>).value

        return AppResult.Success(
            History(
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
