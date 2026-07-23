package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.data.local.SupplementDao
import com.kvdm.fuelled.data.local.SupplementEntity
import com.kvdm.fuelled.data.local.toDomain
import com.kvdm.fuelled.data.suspendRunCatching
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.domain.repository.SupplementRepository
import com.kvdm.fuelled.domain.result.AppResult

/**
 * The Room-backed supplement stack — the fully-wired data source (mirrors FoodRepositoryImpl).
 * Reads/writes the on-device [SupplementDao] (Room), seeding a realistic stack on first run so
 * the app has content offline from install. `setTaken` PERSISTS the tap-to-take state to Room,
 * so it survives a reload of the screen — the whole point of the feature (SUPP-03).
 *
 * The repository is the ONLY exception-translation point: every DAO call runs inside
 * suspendRunCatching (data/AppResultCatching.kt), which maps infrastructure exceptions to typed
 * [DomainError] values and ALWAYS rethrows CancellationException. Seed data lives here in the
 * data layer (ARCH-09) — it never reaches for the presentation layer's preview fixtures.
 */
class SupplementRepositoryImpl(
    private val dao: SupplementDao,
) : SupplementRepository {

    override suspend fun getStack(): AppResult<List<Supplement>> = suspendRunCatching {
        ensureSeeded()
        dao.getAll().map { it.toDomain() }
    }

    override suspend fun setTaken(id: String, taken: Boolean): AppResult<Unit> = suspendRunCatching {
        dao.setTaken(id, taken)
    }

    /** Seed the stack on first run so the app ships with content offline (idempotent). */
    private suspend fun ensureSeeded() {
        if (dao.count() == 0) dao.upsertAll(SEED_STACK)
    }

    private companion object {
        // The starter stack, seeded once into Room. Lives in the data layer (the source owns
        // its seed data, ARCH-09); the presentation layer keeps its own preview fixture. Two
        // are pre-taken so the summary reads a realistic 2-of-6 on first open.
        val SEED_STACK = listOf(
            SupplementEntity("1", "Creatine", "5 g", "Morning", 0, taken = true),
            SupplementEntity("2", "Vitamin D3", "2000 IU", "Morning", 0, taken = true),
            SupplementEntity("3", "Omega-3", "1 g", "Morning", 0, taken = false),
            SupplementEntity("4", "Caffeine", "200 mg", "Pre-workout", 1, taken = false),
            SupplementEntity("5", "Beta-alanine", "3 g", "Pre-workout", 1, taken = false),
            SupplementEntity("6", "Magnesium", "400 mg", "Evening", 2, taken = false),
        )
    }
}
