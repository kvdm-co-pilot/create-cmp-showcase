package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.data.local.SupplementDao
import com.kvdm.fuelled.data.local.SupplementEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Hand-written in-memory [SupplementDao] — lets
 * [com.kvdm.fuelled.data.remote.SupplementRepositoryImpl] be tested through its DOMAIN contract
 * without a real Room database. Mirrors the DAO's observable behaviour: `getAll()` is stably
 * ordered by `timingOrder` (a stable sort preserves insertion order within a bucket), and
 * `setTaken` writes THROUGH to the stored row so a re-read returns the persisted state (SUPP-03).
 */
class FakeSupplementDao : SupplementDao {

    private val rows = mutableListOf<SupplementEntity>()


    /**
     * Room's invalidation tracker, faked honestly: every write bumps this, and every `*Stream`
     * re-queries off it. A fake that returned `flowOf(currentRows)` would emit once and go
     * quiet — which is exactly the bug under test, so it would let the regression back in.
     */
    private val version = MutableStateFlow(0)

    private fun bump() { version.value += 1 }

    override suspend fun getAll(): List<SupplementEntity> = rows.sortedBy { it.timingOrder }

    override fun getAllStream(): Flow<List<SupplementEntity>> = version.map { rows.sortedBy { r -> r.timingOrder } }

    // Bumped AFTER each mutation, never before — Room's invalidation tracker fires after the
    // transaction commits, and a bump-first fake under an immediate dispatcher lets a collector
    // re-query pre-write state with no emission ever carrying the write.
    override suspend fun setTaken(id: String, taken: Boolean) {
        val i = rows.indexOfFirst { it.id == id }
        if (i >= 0) rows[i] = rows[i].copy(taken = taken)
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

    override suspend fun clear() = rows.clear()
}
