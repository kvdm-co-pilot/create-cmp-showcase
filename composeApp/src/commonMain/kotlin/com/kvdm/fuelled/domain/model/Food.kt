package com.kvdm.fuelled.domain.model

/**
 * A catalog entry with nutritional values per serving (see specs/intent.md — Glossary).
 * The canonical domain model for the Foods feature: pure Kotlin, no framework types, the
 * shape the presentation renders and the data layer maps its `FoodEntity` rows into.
 */
data class Food(
    val id: String,
    val name: String,
    val brand: String,
    val serving: String,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
)
