package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.data.local.ProfileDao
import com.kvdm.fuelled.data.local.ProfileEntity

/**
 * Hand-written in-memory [ProfileDao] — lets [com.kvdm.fuelled.data.remote.ProfileRepositoryImpl]
 * be tested through its DOMAIN contract without a real Room database. Mirrors the DAO's observable
 * behaviour: a single row, replaced on upsert, read back by `get()`.
 */
class FakeProfileDao : ProfileDao {

    private var row: ProfileEntity? = null

    override suspend fun get(): ProfileEntity? = row

    override suspend fun count(): Int = if (row != null) 1 else 0

    override suspend fun upsert(profile: ProfileEntity) {
        row = profile
    }
}
