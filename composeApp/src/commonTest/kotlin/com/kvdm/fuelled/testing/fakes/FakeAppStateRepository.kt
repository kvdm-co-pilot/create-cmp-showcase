package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.domain.model.AppState
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.repository.AppStateRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Hand-written fake for the app's own state (START-01/START-02) — observable like the real
 * one, so a test can assert the gate SWAPS when onboarding completes rather than merely
 * that a flag was written.
 */
class FakeAppStateRepository : AppStateRepository {

    var onboarded: Boolean = false
        set(value) { field = value; revision.value += 1 }

    var startedAt: Instant = Instant.parse("2026-07-22T12:45:00Z")
    var failure: DomainError? = null

    private val revision = MutableStateFlow(0)

    override fun observe(): Flow<AppResult<AppState>> = revision.map {
        failure?.let { f -> AppResult.Failure(f) } ?: AppResult.Success(AppState(onboarded, startedAt))
    }

    override suspend fun current(): AppResult<AppState> {
        failure?.let { return AppResult.Failure(it) }
        return AppResult.Success(AppState(onboarded, startedAt))
    }

    override suspend fun markOnboarded(): AppResult<Unit> {
        failure?.let { return AppResult.Failure(it) }
        onboarded = true
        return AppResult.Success(Unit)
    }
}
