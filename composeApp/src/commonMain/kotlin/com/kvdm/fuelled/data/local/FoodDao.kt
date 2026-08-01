package com.kvdm.fuelled.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FoodDao {
    // CAT-02: favourites lead, then alphabetical. Sorting in SQL rather than in the caller
    // keeps every list in the app (catalog, tray) in one agreed order.
    @Query("SELECT * FROM foods ORDER BY favourite DESC, name")
    suspend fun getAll(): List<FoodEntity>

    // SQLite LIKE is case-insensitive for ASCII, so search matches regardless of case.
    @Query(
        "SELECT * FROM foods WHERE name LIKE '%' || :query || '%' " +
            "OR brand LIKE '%' || :query || '%' ORDER BY favourite DESC, name",
    )
    suspend fun search(query: String): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FoodEntity?

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(foods: List<FoodEntity>)

    /** CAT-01/CAT-02: create, edit, or re-flag ONE food. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(food: FoodEntity)

    /** CAT-01: remove a custom food. Past log rows keep their own snapshot and are untouched. */
    @Query("DELETE FROM foods WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM foods")
    suspend fun clear()
}
