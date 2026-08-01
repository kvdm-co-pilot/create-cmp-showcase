package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.data.asAppResult
import com.kvdm.fuelled.data.local.WeightDao
import com.kvdm.fuelled.data.local.toDomain
import com.kvdm.fuelled.data.local.toEntity
import com.kvdm.fuelled.data.suspendRunCatching
import com.kvdm.fuelled.domain.model.WeightEntry
import com.kvdm.fuelled.domain.repository.WeightRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * The Room-backed weigh-in log (HIST-06..08).
 *
 * Deliberately thin: the interesting decision — one row per logical day, so the second
 * weigh-in of a morning corrects the first — lives in the PRIMARY KEY, not in code here. A
 * uniqueness rule enforced in Kotlin is a rule two concurrent writes can step around; the same
 * rule as a primary key cannot be violated at all.
 *
 * The repository is the only exception-translation point (suspendRunCatching / asAppResult).
 */
class WeightRepositoryImpl(private val dao: WeightDao) : WeightRepository {

    override fun observeBetween(from: LocalDate, to: LocalDate): Flow<AppResult<List<WeightEntry>>> =
        dao.streamBetween(from.toString(), to.toString())
            .map { rows -> rows.map { it.toDomain() } }
            .asAppResult()

    override suspend fun record(entry: WeightEntry): AppResult<Unit> = suspendRunCatching {
        dao.upsert(entry.toEntity())
    }
}
