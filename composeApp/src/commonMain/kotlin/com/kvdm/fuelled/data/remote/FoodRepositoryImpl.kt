package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.data.local.FoodDao
import com.kvdm.fuelled.data.local.toDomain
import com.kvdm.fuelled.data.local.toEntity
import com.kvdm.fuelled.data.suspendRunCatching
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.repository.FoodRepository
import com.kvdm.fuelled.domain.result.AppResult

/**
 * The Room-backed Foods catalog — the fully-wired exemplar's data source. Unlike a
 * dependency-light in-memory stub, this reads and writes the on-device [FoodDao] (Room),
 * seeding the catalog on first run so the app has content offline from install.
 *
 * The repository is the ONLY exception-translation point: every DAO call runs inside
 * suspendRunCatching (data/AppResultCatching.kt), which maps infrastructure exceptions to
 * typed [DomainError] values and ALWAYS rethrows CancellationException. A real remote-backed
 * variant swaps the DAO for a Firestore/Ktor source behind this same interface — the Clean
 * Architecture seam is the [FoodRepository] interface in the domain layer, not this class.
 */
class FoodRepositoryImpl(
    private val dao: FoodDao,
) : FoodRepository {

    override suspend fun getFoods(): AppResult<List<Food>> = suspendRunCatching {
        ensureSeeded()
        dao.getAll().map { it.toDomain() }
    }

    override suspend fun searchFoods(query: String): AppResult<List<Food>> = suspendRunCatching {
        ensureSeeded()
        val trimmed = query.trim()
        val rows = if (trimmed.isEmpty()) dao.getAll() else dao.search(trimmed)
        rows.map { it.toDomain() }
    }

    override suspend fun getFood(id: String): AppResult<Food> = suspendRunCatching(
        // A missing catalog entry is a typed NotFound, not an Unexpected — the detail maps it
        // to its own copy. Everything else falls through to the default Unexpected classifier.
        mapError = { if (it is NoSuchEntryException) DomainError.NotFound else DomainError.Unexpected(it) },
    ) {
        ensureSeeded()
        dao.getById(id)?.toDomain() ?: throw NoSuchEntryException(id)
    }

    /** Seed the catalog on first run so the app ships with content offline (idempotent). */
    private suspend fun ensureSeeded() {
        if (dao.count() == 0) dao.upsertAll(SEED_CATALOG.map { it.toEntity() })
    }

    private class NoSuchEntryException(id: String) : NoSuchElementException("no food with id '$id'")

    private companion object {
        // The starter catalog, seeded once into Room. Lives in the data layer (the source
        // owns its seed data); the presentation layer keeps its own preview fixtures.
        val SEED_CATALOG = listOf(
            Food("1", "Chicken breast", "Raw · skinless", "100 g", 165, 31, 0, 4),
            Food("2", "Whey protein", "Gold Standard", "1 scoop · 30 g", 120, 24, 3, 2),
            Food("3", "Rolled oats", "Quaker", "80 g", 303, 11, 54, 6),
            Food("4", "Greek yogurt 0%", "Fage", "170 g", 100, 17, 6, 0),
            Food("5", "Banana", "Medium", "1 · 118 g", 105, 1, 27, 0),
            Food("6", "White rice", "Cooked", "150 g", 195, 4, 42, 0),
            Food("7", "Almonds", "Raw", "20 g", 116, 4, 4, 10),
            // Veg-flagged (PLAN-22). The method asks for vegetables with at least two meals,
            // and a catalog with nothing flagged would make that count permanently 0 of 2 —
            // a rule surfaced against food you cannot pick is worse than not surfacing it.
            // A banana is deliberately NOT flagged: this is the method's vegetable rule, and
            // counting fruit toward it would quietly make the target easier than it is.
            Food("8", "Broccoli", "Steamed", "100 g", 35, 2, 7, 0, veg = true),
            Food("9", "Mixed greens", "Salad bowl", "1 bowl · 85 g", 90, 3, 5, 6, veg = true),
            Food("10", "Green beans", "Steamed", "100 g", 31, 2, 7, 0, veg = true),
        )
    }
}
