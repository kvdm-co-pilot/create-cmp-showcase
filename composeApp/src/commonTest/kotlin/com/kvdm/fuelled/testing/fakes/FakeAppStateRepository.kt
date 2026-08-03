package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.domain.model.AppSettings
import com.kvdm.fuelled.domain.model.AppState
import com.kvdm.fuelled.domain.model.PREP_LEAD_RANGE
import com.kvdm.fuelled.domain.model.UnitSystem
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

    /** NOTIF-01: whether the permission dialog has ever been shown. */
    var notifPromptShown: Boolean = false
        set(value) { field = value; revision.value += 1 }

    var startedAt: Instant = Instant.parse("2026-07-22T12:45:00Z")
    var failure: DomainError? = null

    /** SET-02/SET-07: the settings on the same row. Mutating either re-emits, like the real one. */
    var settings: AppSettings = AppSettings()
        set(value) { field = value; revision.value += 1 }

    /** Every lead ATTEMPT, recorded before the range guard — so a refusal is observable too. */
    val prepLeadCalls: MutableList<Int> = mutableListOf()

    private val revision = MutableStateFlow(0)

    override fun observe(): Flow<AppResult<AppState>> = revision.map {
        failure?.let { f -> AppResult.Failure(f) }
            ?: AppResult.Success(AppState(onboarded, startedAt, settings, notifPromptShown))
    }

    override suspend fun current(): AppResult<AppState> {
        failure?.let { return AppResult.Failure(it) }
        return AppResult.Success(AppState(onboarded, startedAt, settings, notifPromptShown))
    }

    override suspend fun markOnboarded(): AppResult<Unit> {
        failure?.let { return AppResult.Failure(it) }
        onboarded = true
        return AppResult.Success(Unit)
    }

    override suspend fun setUnitSystem(system: UnitSystem): AppResult<Unit> {
        failure?.let { return AppResult.Failure(it) }
        settings = settings.copy(unitSystem = system)
        return AppResult.Success(Unit)
    }

    override suspend fun setPrepLeadMinutes(minutes: Int): AppResult<Unit> {
        prepLeadCalls += minutes
        failure?.let { return AppResult.Failure(it) }
        // SET-07: refused, not clamped — the real store's stance, so a test that asserts a
        // rejection is asserting the same behaviour the app ships.
        if (minutes !in PREP_LEAD_RANGE) return AppResult.Failure(DomainError.Unexpected())
        settings = settings.copy(prepLeadMinutes = minutes)
        return AppResult.Success(Unit)
    }

    override suspend fun markNotifPromptShown(): AppResult<Unit> {
        failure?.let { return AppResult.Failure(it) }
        notifPromptShown = true
        return AppResult.Success(Unit)
    }
}
