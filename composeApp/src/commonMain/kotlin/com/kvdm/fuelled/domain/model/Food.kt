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
    /**
     * Whether this catalog entry counts as a vegetable (PLAN-22). A property of the FOOD, not
     * of the log entry that references it — "is broccoli a vegetable" is a fact about broccoli.
     * Flagging it here makes the day's veg count derivable from what was actually eaten,
     * without anyone tagging a meal. Defaults false: most of a catalog is not veg, and the
     * flag is a claim to be made deliberately.
     */
    val veg: Boolean = false,
    /**
     * CAT-02: pinned by the user. A favourite is a statement about how OFTEN you eat this,
     * which is why it sorts the tray ahead of everything else — the fastest log is the one
     * that needs no search.
     */
    val favourite: Boolean = false,
    /**
     * CAT-01: created by the user, not seeded. Only a custom food is editable: the seeded
     * catalog's numbers are reference data, and silently editing "Chicken breast" under
     * everyone's past entries would rewrite what those days claimed (log rows snapshot their
     * own macros, so history is safe — but the catalog should still say what it means).
     */
    val custom: Boolean = false,
)
