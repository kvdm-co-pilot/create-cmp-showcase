package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.data.local.ProfileDao
import com.kvdm.fuelled.data.local.ProfileEntity
import com.kvdm.fuelled.data.local.toDomain
import com.kvdm.fuelled.data.suspendRunCatching
import com.kvdm.fuelled.domain.model.Profile
import com.kvdm.fuelled.domain.repository.ProfileRepository
import com.kvdm.fuelled.domain.result.AppResult

/**
 * The Room-backed Profile source — the fully-wired data source. Reads the flat [ProfileDao] row
 * and maps it into the domain [Profile]. Seeds one realistic profile on first run so the app has
 * content offline from install (idempotent).
 *
 * The repository is the ONLY exception-translation point: every DAO call runs inside
 * suspendRunCatching (data/AppResultCatching.kt), which maps infrastructure exceptions to typed
 * DomainError values and ALWAYS rethrows CancellationException. Seed data lives here in the data
 * layer (ARCH-09) — it never reaches for the presentation layer's preview fixtures.
 */
class ProfileRepositoryImpl(
    private val dao: ProfileDao,
) : ProfileRepository {

    override suspend fun getProfile(): AppResult<Profile> = suspendRunCatching {
        ensureSeeded()
        (dao.get() ?: error("profile row missing after seeding")).toDomain()
    }

    /** Seed a realistic profile on first run so the screen ships with content offline (idempotent). */
    private suspend fun ensureSeeded() {
        if (dao.count() == 0) dao.upsert(SEED)
    }

    private companion object {
        // The starter profile, seeded once into Room. Lives in the data layer (the source owns its
        // seed data, ARCH-09); the presentation layer keeps its own preview fixture separately.
        val SEED = ProfileEntity(
            id = "current",
            name = "Karel",
            planLabel = "Cutting",
            calorieTarget = 2400,
            proteinGoalG = 180,
            activity = "Trains 5×/week",
            streakDays = 12,
            avgProteinG = 172,
            weightKg = 82.4,
        )
    }
}
