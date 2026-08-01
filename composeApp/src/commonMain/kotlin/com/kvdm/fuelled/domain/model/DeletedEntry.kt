package com.kvdm.fuelled.domain.model

import kotlinx.datetime.LocalDate

/**
 * A log entry as it was the moment before it was removed (ENTRY-02) — everything an undo
 * needs to put it back exactly where it stood: its day, its slot, its place in that slot,
 * and its serving multiple.
 *
 * Deliberately a domain type and not the Room row: undo is a behaviour of the ledger, and
 * the ViewModel that holds one of these between the delete and the undo must never be
 * holding a database entity (ARCH-02).
 */
data class DeletedEntry(
    val id: String,
    val foodId: String,
    val date: LocalDate,
    val slot: MealSlot,
    val status: LogStatus,
    val entryOrder: Int,
    val name: String,
    val serving: String,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val servings: Int,
    val veg: Boolean,
)
