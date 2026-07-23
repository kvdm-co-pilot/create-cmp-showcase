package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.data.local.SupplementDao
import com.kvdm.fuelled.data.local.SupplementEntity

/**
 * Hand-written in-memory [SupplementDao] — lets
 * [com.kvdm.fuelled.data.remote.SupplementRepositoryImpl] be tested through its DOMAIN contract
 * without a real Room database. Mirrors the DAO's observable behaviour: `getAll()` is stably
 * ordered by `timingOrder` (a stable sort preserves insertion order within a bucket), and
 * `setTaken` writes THROUGH to the stored row so a re-read returns the persisted state (SUPP-03).
 */
class FakeSupplementDao : SupplementDao {

    private val rows = mutableListOf<SupplementEntity>()

    override suspend fun getAll(): List<SupplementEntity> = rows.sortedBy { it.timingOrder }

    override suspend fun setTaken(id: String, taken: Boolean) {
        val i = rows.indexOfFirst { it.id == id }
        if (i >= 0) rows[i] = rows[i].copy(taken = taken)
    }

    override suspend fun count(): Int = rows.size

    override suspend fun upsertAll(supplements: List<SupplementEntity>) {
        for (supp in supplements) {
            rows.removeAll { it.id == supp.id }
            rows.add(supp)
        }
    }

    override suspend fun clear() = rows.clear()
}
