package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.core.time.RealTimeSignal
import com.kvdm.fuelled.core.time.TimeSignal
import com.kvdm.fuelled.data.asAppResult
import com.kvdm.fuelled.data.local.AppStateDao
import com.kvdm.fuelled.data.local.AppStateEntity
import com.kvdm.fuelled.data.suspendRunCatching
import com.kvdm.fuelled.domain.model.AppState
import com.kvdm.fuelled.domain.repository.AppStateRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * The Room-backed app state (START-01/START-02).
 *
 * Seeding is the interesting part: the row is written on the FIRST read, stamping
 * [AppStateEntity.startedAtEpochMs] with the current instant. That is the only moment this
 * install's start could ever be captured — there is no earlier hook — and it is idempotent,
 * so every later read returns the same instant rather than moving the boundary forward.
 *
 * The repository is the ONLY exception-translation point (suspendRunCatching / asAppResult).
 */
class AppStateRepositoryImpl(
    private val dao: AppStateDao,
    private val time: TimeSignal = RealTimeSignal(),
) : AppStateRepository {

    override fun observe(): Flow<AppResult<AppState>> =
        dao.stream()
            .onStart { ensureSeeded() }
            .map { row -> (row ?: ensureSeededRow()).toDomain() }
            .asAppResult()

    override suspend fun current(): AppResult<AppState> = suspendRunCatching {
        ensureSeededRow().toDomain()
    }

    override suspend fun markOnboarded(): AppResult<Unit> = suspendRunCatching {
        val row = ensureSeededRow()
        dao.upsert(row.copy(onboarded = true))
    }

    private suspend fun ensureSeeded() {
        if (dao.get() == null) {
            dao.upsert(AppStateEntity(id = ID, onboarded = false, startedAtEpochMs = time.now().toEpochMilliseconds()))
        }
    }

    private suspend fun ensureSeededRow(): AppStateEntity {
        ensureSeeded()
        return dao.get() ?: error("app state row missing after seeding")
    }

    private fun AppStateEntity.toDomain() = AppState(
        onboarded = onboarded,
        startedAt = Instant.fromEpochMilliseconds(startedAtEpochMs),
    )

    private companion object {
        const val ID = "current"
    }
}
