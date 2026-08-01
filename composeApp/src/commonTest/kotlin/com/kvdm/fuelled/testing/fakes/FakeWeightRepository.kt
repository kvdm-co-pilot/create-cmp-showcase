package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.WeightEntry
import com.kvdm.fuelled.domain.repository.WeightRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * Hand-written fake for the weigh-in log (HIST-06..08).
 *
 * Observable like the real one, and — the part that matters — it upserts BY DATE, so a test
 * asserting "weighing twice in a morning corrects rather than appends" is asserting the
 * behaviour the primary key actually gives the app, not a convention this fake invented.
 */
class FakeWeightRepository : WeightRepository {

    var entries: List<WeightEntry> = emptyList()
        set(value) { field = value; revision.value += 1 }
    var failure: DomainError? = null
        set(value) { field = value; revision.value += 1 }

    private val revision = MutableStateFlow(0)

    /** Every record ATTEMPT, recorded before `failure` is applied. */
    val recorded: MutableList<WeightEntry> = mutableListOf()

    override fun observeBetween(from: LocalDate, to: LocalDate): Flow<AppResult<List<WeightEntry>>> =
        revision.map {
            failure?.let { f -> AppResult.Failure(f) }
                ?: AppResult.Success(entries.filter { it.date in from..to }.sortedBy { it.date })
        }

    override suspend fun record(entry: WeightEntry): AppResult<Unit> {
        recorded += entry
        failure?.let { return AppResult.Failure(it) }
        entries = entries.filterNot { it.date == entry.date } + entry
        return AppResult.Success(Unit)
    }
}
