package com.kvdm.fuelled.testing.fakes

import com.kvdm.fuelled.data.local.FoodDao
import com.kvdm.fuelled.data.local.FoodEntity

/**
 * Hand-written in-memory [FoodDao] — lets [com.kvdm.fuelled.data.remote.FoodRepositoryImpl]
 * be tested through its DOMAIN contract without a real Room database. Mirrors the DAO's
 * observable behavior: `search` matches name OR brand case-insensitively (SQLite LIKE), and
 * results are name-ordered like the `@Query`s declare.
 */
class FakeFoodDao : FoodDao {

    private val rows = mutableListOf<FoodEntity>()

    override suspend fun getAll(): List<FoodEntity> = rows.sortedBy { it.name }

    override suspend fun search(query: String): List<FoodEntity> =
        rows.filter {
            it.name.contains(query, ignoreCase = true) || it.brand.contains(query, ignoreCase = true)
        }.sortedBy { it.name }

    override suspend fun getById(id: String): FoodEntity? = rows.firstOrNull { it.id == id }

    override suspend fun count(): Int = rows.size

    override suspend fun upsertAll(foods: List<FoodEntity>) {
        for (food in foods) {
            rows.removeAll { it.id == food.id }
            rows.add(food)
        }
    }

    override suspend fun clear() = rows.clear()
}
