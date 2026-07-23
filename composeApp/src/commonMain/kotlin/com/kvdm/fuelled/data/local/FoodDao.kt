package com.kvdm.fuelled.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FoodDao {
    @Query("SELECT * FROM foods ORDER BY name")
    suspend fun getAll(): List<FoodEntity>

    // SQLite LIKE is case-insensitive for ASCII, so search matches regardless of case.
    @Query(
        "SELECT * FROM foods WHERE name LIKE '%' || :query || '%' " +
            "OR brand LIKE '%' || :query || '%' ORDER BY name",
    )
    suspend fun search(query: String): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FoodEntity?

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(foods: List<FoodEntity>)

    @Query("DELETE FROM foods")
    suspend fun clear()
}
