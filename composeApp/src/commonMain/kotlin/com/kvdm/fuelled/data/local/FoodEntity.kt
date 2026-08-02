package com.kvdm.fuelled.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kvdm.fuelled.domain.model.BflCategory
import com.kvdm.fuelled.domain.model.Food
import com.kvdm.fuelled.domain.model.Macros100g

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
    /** CAT-02: pinned by the user — favourites lead the tray's list. */
    val favourite: Boolean = false,
    /** CAT-01: created by the user rather than seeded — only these are editable. */
    val custom: Boolean = false,
    // BFL-01: per-100 g truth, provenance, role and portion. The columns above hold this
    // food's DEFAULT PORTION, derived from these at seed time — kept because every existing
    // surface reads them, never entered independently.
    val kcalPer100g: Double = 0.0,
    val proteinPer100g: Double = 0.0,
    val carbsPer100g: Double = 0.0,
    val fatPer100g: Double = 0.0,
    val category: String = "CARB",
    val portionGrams: Int = 100,
    /** The USDA FoodData Central id these numbers came from; null for a user's own food. */
    val fdcId: Int? = null,
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
    favourite = favourite,
    custom = custom,
    per100g = Macros100g(kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g),
    // BFL-03: an unrecognised category reads as CARB rather than throwing — a row written by
    // an older build must not be able to crash the catalog it belongs to.
    category = BflCategory.entries.firstOrNull { it.name == category } ?: BflCategory.CARB,
    portionGrams = portionGrams,
    fdcId = fdcId,
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
    favourite = favourite,
    custom = custom,
    kcalPer100g = per100g.kcal,
    proteinPer100g = per100g.proteinG,
    carbsPer100g = per100g.carbsG,
    fatPer100g = per100g.fatG,
    category = category.name,
    portionGrams = portionGrams,
    fdcId = fdcId,
)
