package com.kvdm.fuelled.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kvdm.fuelled.domain.model.Food

// ── Room entity for the Foods catalog — the on-device SSOT the repository reads/writes ──
// The data layer's own row shape; it maps to/from the domain `Food` at the repository seam
// so domain never sees a Room type.
@Entity(tableName = "foods")
data class FoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String,
    val serving: String,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    /** PLAN-22: does this food count toward "veg with at least two meals"? */
    val veg: Boolean = false,
)

fun FoodEntity.toDomain(): Food = Food(
    id = id,
    name = name,
    brand = brand,
    serving = serving,
    kcal = kcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    veg = veg,
)

fun Food.toEntity(): FoodEntity = FoodEntity(
    id = id,
    name = name,
    brand = brand,
    serving = serving,
    kcal = kcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    veg = veg,
)
