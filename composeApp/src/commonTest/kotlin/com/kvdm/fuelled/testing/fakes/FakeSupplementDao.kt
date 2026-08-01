package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.data.local.SupplementDao
import com.kvdm.fuelled.data.local.SupplementEntity
import com.kvdm.fuelled.data.local.SupplementTakenEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Hand-written in-memory [SupplementDao] — lets
 * [com.kvdm.fuelled.data.remote.SupplementRepositoryImpl] be tested through its DOMAIN contract
 * without a real Room database. Mirrors the DAO's observable behaviour: `getAll()` is stably
 * ordered by `timingOrder` (a stable sort preserves insertion order within a bucket), and doses
 * are keyed by logical day so a re-read returns the persisted state (SUPP-03/SUPP-07).
 */
class FakeSupplementDao : SupplementDao {

    private val rows = mutableListOf<SupplementEntity>()

    /** Doses, keyed exactly as the table is: (logicalDate, supplementId). */
    private val taken = mutableSetOf<Pair<String, String>>()


    /**
     * Room's invalidation tracker, faked honestly: every write bumps this, and every `*Stream`
     * re-queries off it. A fake that returned `flowOf(currentRows)` would emit once and go
     * quiet — which is exactly the bug under test, so it would let the regression back in.
     */
    private val version = MutableStateFlow(0)

    private fun bump() { version.value += 1 }

    override suspend fun getAll(): List<SupplementEntity> = rows.sortedBy { it.timingOrder }

    override fun getAllStream(): Flow<List<SupplementEntity>> = version.map { rows.sortedBy { r -> r.timingOrder } }

    override suspend fun takenOn(logicalDate: String): List<SupplementTakenEntity> =
        taken.filter { it.first == logicalDate }.map { SupplementTakenEntity(it.first, it.second) }

    override fun takenStream(logicalDate: String): Flow<List<SupplementTakenEntity>> =
        version.map { takenOn(logicalDate) }

    // Bumped AFTER each mutation, never before — Room's invalidation tracker fires after the
    // transaction commits, and a bump-first fake under an immediate dispatcher lets a collector
    // re-query pre-write state with no emission ever carrying the write.
    override suspend fun insertTaken(row: SupplementTakenEntity) {
        taken += row.logicalDate to row.supplementId
        bump()
    }

    override suspend fun clearTaken(logicalDate: String, id: String) {
        taken -= logicalDate to id
        bump()
    }

    override suspend fun count(): Int = rows.size

    override suspend fun upsertAll(supplements: List<SupplementEntity>) {
        for (supp in supplements) {
            rows.removeAll { it.id == supp.id }
            rows.add(supp)
        }
        bump()
    }

    /** SET-04: write-through by id, like the DAO's REPLACE — a re-save corrects, never twins. */
    override suspend fun upsert(supplement: SupplementEntity) {
        rows.removeAll { it.id == supplement.id }
        rows.add(supplement)
        bump()
    }

    /** SET-05: the catalog row goes; `taken` rows are deliberately left alone (history stands). */
    override suspend fun deleteById(id: String) {
        rows.removeAll { it.id == id }
        bump()
    }

    override suspend fun clear() = rows.clear()
}
