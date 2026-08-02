package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.model.DatedGoal
import com.kvdm.fuelled.domain.model.TREND_DAYS
import com.kvdm.fuelled.domain.model.WeightEntry
import com.kvdm.fuelled.domain.model.WeightLog
import com.kvdm.fuelled.domain.repository.TodayRepository
import com.kvdm.fuelled.domain.repository.WeightRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

/**
 * The weigh-in window, observed (HIST-07/HIST-08).
 *
 * The window is anchored on the CURRENT logical day and re-derived through
 * `getPlanDay.currentLogicalDay()`, exactly as the history window is — the two must span the
 * same days or the trend and the weight beside it would describe different fortnights.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveWeightLogUseCase(
    private val repository: WeightRepository,
    private val getPlanDay: GetPlanDayUseCase,
) {
    operator fun invoke(days: Int = TREND_DAYS): Flow<AppResult<WeightLog>> =
        getPlanDay.currentLogicalDay().flatMapLatest { today ->
            repository.observeBetween(from = today.minus(days - 1, DateTimeUnit.DAY), to = today)
                .map { result ->
                    when (result) {
                        is AppResult.Failure -> result
                        is AppResult.Success -> AppResult.Success(WeightLog(result.value))
                    }
                }
        }
}

/**
 * HIST-06: record today's weight.
 *
 * The DAY is derived here, not passed in — a weigh-in belongs to the logical day it was
 * entered on, decided at the moment of the write (MEAL-02's rule for every write in this app).
 * A non-positive weight is refused before the write: there is no reading it could be, and
 * storing it would poison the change calculation for the whole window.
 */
class RecordWeightUseCase(
    private val repository: WeightRepository,
    private val getPlanDay: GetPlanDayUseCase,
) {
    suspend operator fun invoke(kg: Double): AppResult<Unit> {
        if (kg <= 0.0 || kg > MAX_PLAUSIBLE_KG) return AppResult.Success(Unit)
        return repository.record(WeightEntry(date = getPlanDay.currentLogicalDayNow(), kg = kg))
    }

    private companion object {
        /** A guard against a slipped decimal point, not a judgement about bodies. */
        const val MAX_PLAUSIBLE_KG = 500.0
    }
}

/**
 * GOAL-01/GOAL-03: every goal ever set, observed.
 *
 * Its own use case rather than a repository call inlined into the history, because the
 * resolution rule — "the latest goal starting on or before this day" — is domain logic that
 * the trend, the week verdict and any later surface must all apply identically. One rule,
 * one place ([goalOn]).
 */
class ObserveGoalHistoryUseCase(private val repository: TodayRepository) {
    operator fun invoke(): Flow<AppResult<List<DatedGoal>>> = repository.observeGoalHistory()
}
